package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.ComercializadoraRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ComercializadoraServiceIntegrantesTest {

    private ComercializadoraRepository repo;
    private AsesorExternoRepository asesores;
    private UsuarioRepository usuarios;
    private VentaRepository ventas;
    private VentaComisionRepository comisiones;
    private ClienteRepository clientes;
    private WeFormRespuestaRepository respuestas;
    private ComercializadoraService service;
    private Comercializadora com;
    private AsesorExterno lider;
    private AsesorExterno ana;

    @BeforeEach
    void preparar() {
        repo = mock(ComercializadoraRepository.class);
        asesores = mock(AsesorExternoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        ventas = mock(VentaRepository.class);
        comisiones = mock(VentaComisionRepository.class);
        clientes = mock(ClienteRepository.class);
        respuestas = mock(WeFormRespuestaRepository.class);
        CurrentUserProvider actual = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(actual.getUsuarioActual()).thenReturn(admin);
        service = new ComercializadoraService(
                repo, asesores, actual, new PinAsesor(asesores, usuarios), ventas, comisiones, clientes, respuestas);

        lider = new AsesorExterno("Laura Líder", "9511111111", null);
        ReflectionTestUtils.setField(lider, "id", 1L);
        com = new Comercializadora("Sol", lider, null, "tok");
        ReflectionTestUtils.setField(com, "id", 10L);
        ana = new AsesorExterno("Ana Pérez", "9512222222", null);
        ReflectionTestUtils.setField(ana, "id", 2L);
        ana.setComercializadora(com);
        ana.setPin("ABCD");
        when(repo.findById(10L)).thenReturn(Optional.of(com));
        when(asesores.findById(2L)).thenReturn(Optional.of(ana));
        when(asesores.findAll()).thenReturn(List.of(lider, ana));
        when(asesores.findByPin(any())).thenReturn(Optional.empty());
        when(usuarios.findByPin(any())).thenReturn(Optional.empty());
    }

    private static ComercializadoraDtos.ActualizarIntegranteRequest datos(String n, String c, String correo, String pin) {
        return new ComercializadoraDtos.ActualizarIntegranteRequest(n, c, correo, pin);
    }

    @Test
    void corrigeNombreCelularYCorreoSinTocarElPin() {
        var r = service.actualizarIntegrante(10L, 2L, datos("  Ana   María Pérez ", "(951) 333-4444", "ana@x.com", null));
        assertEquals("Ana María Pérez", r.nombre());
        assertEquals("9513334444", r.celular());
        assertEquals("ana@x.com", r.correo());
        assertEquals("ABCD", r.pin());
    }

    @Test
    void elPinVacioGeneraUnoNuevoYElEscritoSeNormaliza() {
        String nuevo = service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "9512222222", null, "")).pin();
        assertTrue(nuevo.matches("[A-HJ-NP-Z2-9]{4}"), nuevo);
        assertEquals("Z9Q2", service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "9512222222", null, "z9q2")).pin());
        assertThrows(ValidationException.class, () -> service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "9512222222", null, "AB")));
    }

    @Test
    void noDejaUnPinOUnCelularDeOtraPersona() {
        when(asesores.findByPin("LIDR")).thenReturn(Optional.of(lider));
        assertThrows(ConflictException.class, () -> service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "9512222222", null, "LIDR")));
        assertThrows(ConflictException.class, () -> service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "951-111-1111", null, null)));
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThrows(ValidationException.class, () -> service.actualizarIntegrante(10L, 2L, datos("An", "9512222222", null, null)));
        assertThrows(ValidationException.class, () -> service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "123", null, null)));
        assertThrows(ValidationException.class, () -> service.actualizarIntegrante(10L, 2L, datos("Ana Pérez", "9512222222", "malo", null)));
    }

    @Test
    void unAsesorDeOtraComercializadoraNoSePuedeTocar() {
        AsesorExterno ajeno = new AsesorExterno("Beto Ruiz", "9515555555", null);
        ReflectionTestUtils.setField(ajeno, "id", 3L);
        when(asesores.findById(3L)).thenReturn(Optional.of(ajeno));
        assertThrows(ResourceNotFoundException.class, () -> service.actualizarIntegrante(10L, 3L, datos("Beto Ruiz", "9515555555", null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.quitarIntegrante(10L, 3L));
    }

    @Test
    void sinHistorialSeEliminaDelSistema() {
        var r = service.quitarIntegrante(10L, 2L);
        assertEquals("ELIMINADO", r.resultado());
        verify(asesores).delete(ana);
        assertTrue(ana.isActivo());
    }

    @Test
    void conHistorialSoloSeDesactiva() {
        when(ventas.existsByAsesorExternoId(2L)).thenReturn(true);
        var r = service.quitarIntegrante(10L, 2L);
        assertEquals("DESACTIVADO", r.resultado());
        verify(asesores, never()).delete(any(AsesorExterno.class));
        assertFalse(ana.isActivo());
    }

    @Test
    void cualquierOtroHistorialTambienLoDesactiva() {
        when(comisiones.existsByAsesorExternoId(2L)).thenReturn(true);
        assertEquals("DESACTIVADO", service.quitarIntegrante(10L, 2L).resultado());
    }

    @Test
    void conGenteReportandoleHayQueReasignarlaAntes() {
        when(asesores.existsByLiderDirectoId(2L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.quitarIntegrante(10L, 2L));
        verify(asesores, never()).delete(any(AsesorExterno.class));
    }
}
