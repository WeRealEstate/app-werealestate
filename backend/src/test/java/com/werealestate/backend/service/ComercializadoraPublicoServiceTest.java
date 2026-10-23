package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import com.werealestate.backend.model.TipoAsesorExterno;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ComercializadoraRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ComercializadoraPublicoServiceTest {

    private ComercializadoraRepository repo;
    private AsesorExternoRepository asesores;
    private UsuarioRepository usuarios;
    private ComercializadoraPublicoService service;
    private AsesorExterno lider;
    private Comercializadora comercializadora;

    @BeforeEach
    void preparar() {
        repo = mock(ComercializadoraRepository.class);
        asesores = mock(AsesorExternoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        service = new ComercializadoraPublicoService(repo, asesores, usuarios);
        lider = new AsesorExterno("Laura Líder", "9511111111", null);
        lider.setTipo(TipoAsesorExterno.LIDER);
        comercializadora = new Comercializadora("Inmobiliaria Sol", lider, null, "tok-abc");
        when(repo.findByToken("tok-abc")).thenReturn(Optional.of(comercializadora));
        when(asesores.findAll()).thenReturn(List.of(lider));
        when(asesores.findByPin(any())).thenReturn(Optional.empty());
        when(usuarios.findByPin(any())).thenReturn(Optional.empty());
        when(asesores.save(any(AsesorExterno.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ComercializadoraDtos.UnirseRequest datos(String nombre, String celular, String correo) {
        return new ComercializadoraDtos.UnirseRequest(nombre, celular, correo);
    }

    @Test
    void elIntegranteQuedaEnLineaUnoDeLaLiderConPin() {
        var r = service.unirse("tok-abc", datos("  Ana   Pérez ", "(951) 222-3344", " ana@correo.com "), "1.1.1.1");
        assertEquals("Ana Pérez", r.nombre());
        assertEquals("Laura Líder", r.lider());
        assertEquals("Inmobiliaria Sol", r.comercializadora());
        assertTrue(r.pin().matches("[A-HJ-NP-Z2-9]{4}"), r.pin());
    }

    @Test
    void guardaLaFichaConLaLiderComoQuienLoTrajo() {
        org.mockito.ArgumentCaptor<AsesorExterno> captor = org.mockito.ArgumentCaptor.forClass(AsesorExterno.class);
        service.unirse("tok-abc", datos("Ana Pérez", "9512223344", null), "1.1.1.1");
        org.mockito.Mockito.verify(asesores).save(captor.capture());
        AsesorExterno a = captor.getValue();
        assertEquals(TipoAsesorExterno.LINEA, a.getTipo());
        assertEquals(lider, a.getLiderDirecto());
        assertEquals(1, a.getNivelLinea());
        assertEquals("9512223344", a.getCelular());
        assertEquals(comercializadora, a.getComercializadora());
        assertEquals(lider, a.getTraidoPorAsesor());
        assertTrue(a.isAccesoSamai() && a.isAccesoNanuu());
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThrows(ValidationException.class, () -> service.unirse("tok-abc", datos("Al", "9512223344", null), "9.9.9.1"));
        assertThrows(ValidationException.class, () -> service.unirse("tok-abc", datos("Ana Pérez", "12345", null), "9.9.9.1"));
        assertThrows(ValidationException.class, () -> service.unirse("tok-abc", datos("Ana Pérez", "9512223344", "no-es-correo"), "9.9.9.1"));
    }

    @Test
    void noDejaRegistrarDosVecesElMismoCelular() {
        assertThrows(ConflictException.class, () -> service.unirse("tok-abc", datos("Otra Persona", "951-111-1111", null), "9.9.9.2"));
    }

    @Test
    void unEnlaceInvalidoOInactivoSeVeIgualDeInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> service.info("no-existe"));
        comercializadora.actualizar("Inmobiliaria Sol", lider, null, false);
        assertThrows(ResourceNotFoundException.class, () -> service.info("tok-abc"));
        assertThrows(ResourceNotFoundException.class, () -> service.unirse("tok-abc", datos("Ana Pérez", "9512223344", null), "9.9.9.3"));
    }

    @Test
    void siLaLiderYaNoEncabezaUnEquipoNoSeAceptanAltas() {
        lider.setTipo(TipoAsesorExterno.INDEPENDIENTE);
        assertThrows(ConflictException.class, () -> service.unirse("tok-abc", datos("Ana Pérez", "9512223344", null), "9.9.9.4"));
    }

    @Test
    void limitaLasAltasPorIp() {
        for (int i = 0; i < ComercializadoraPublicoService.MAX_ALTAS_POR_IP; i++) {
            service.unirse("tok-abc", datos("Persona " + i + " Prueba", "95120000" + String.format("%02d", i), null), "7.7.7.7");
        }
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.unirse("tok-abc", datos("Una Más Prueba", "9519999999", null), "7.7.7.7"));
        assertEquals(429, e.getStatusCode().value());
        // otra IP sí puede
        service.unirse("tok-abc", datos("Otra Ip Prueba", "9518888888", null), "8.8.8.8");
    }

    @Test
    void elTokenEsLargoYDistintoCadaVez() {
        String a = ComercializadoraService.nuevoToken();
        String b = ComercializadoraService.nuevoToken();
        assertNotEquals(a, b);
        assertTrue(a.length() >= 32 && a.matches("[A-Za-z0-9_-]+"), a);
    }
}
