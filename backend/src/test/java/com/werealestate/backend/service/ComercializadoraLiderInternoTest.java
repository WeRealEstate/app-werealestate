package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAsesorExterno;
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

/** Un asesor interno (la ficha de comunidad de un usuario con rol ASESOR) puede ser la líder de una comercializadora. */
class ComercializadoraLiderInternoTest {

    private ComercializadoraRepository repo;
    private AsesorExternoRepository asesores;
    private ComercializadoraService service;
    private AsesorExterno interno;

    @BeforeEach
    void preparar() {
        repo = mock(ComercializadoraRepository.class);
        asesores = mock(AsesorExternoRepository.class);
        CurrentUserProvider actual = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(actual.getUsuarioActual()).thenReturn(admin);
        service = new ComercializadoraService(
                repo, asesores, actual, new PinAsesor(asesores, mock(UsuarioRepository.class)),
                mock(VentaRepository.class), mock(VentaComisionRepository.class), mock(ClienteRepository.class),
                mock(WeFormRespuestaRepository.class));

        interno = new AsesorExterno("Iván Interno", null, "ivan@weinversiones.com");
        ReflectionTestUtils.setField(interno, "id", 7L);
        interno.setUsuarioId(70L);
        when(asesores.findById(7L)).thenReturn(Optional.of(interno));
        when(repo.findAllByOrderByNombreAsc()).thenReturn(List.of());
        when(repo.save(org.mockito.ArgumentMatchers.any(Comercializadora.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void unAsesorInternoPuedeSerLiderYPasaAEncabezarUnEquipo() {
        var r = service.crear(new ComercializadoraDtos.CrearRequest("Sol", 7L, null));
        assertEquals("Iván Interno", r.liderNombre());
        assertEquals(TipoAsesorExterno.LIDER, interno.getTipo());
    }

    @Test
    void unInternoQueYaReportaAOtraPersonaNoPuedeSerLider() {
        interno.setTipo(TipoAsesorExterno.LINEA);
        assertThrows(ConflictException.class, () -> service.crear(new ComercializadoraDtos.CrearRequest("Sol", 7L, null)));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(Comercializadora.class));
    }

    @Test
    void noPuedeLiderarDosComercializadoras() {
        Comercializadora otra = new Comercializadora("Luna", interno, null, "tok");
        ReflectionTestUtils.setField(otra, "id", 11L);
        when(repo.findAllByOrderByNombreAsc()).thenReturn(List.of(otra));
        assertThrows(ConflictException.class, () -> service.crear(new ComercializadoraDtos.CrearRequest("Sol", 7L, null)));
    }

    @Test
    void noSeQuitaDeComunidadesWeAUnInternoQueLideraUnaComercializadora() {
        AsesorExternoRepository asesoresComunidad = mock(AsesorExternoRepository.class);
        ComercializadoraRepository comercializadoras = mock(ComercializadoraRepository.class);
        when(asesoresComunidad.findByUsuarioId(70L)).thenReturn(Optional.of(interno));
        when(comercializadoras.existsByLiderId(7L)).thenReturn(true);
        ComunidadInternosService comunidad = new ComunidadInternosService(asesoresComunidad, comercializadoras);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(70L);
        assertThrows(ConflictException.class, () -> comunidad.eliminarDe(usuario));
        verify(asesoresComunidad, never()).delete(interno);
    }
}
