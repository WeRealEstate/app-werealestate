package com.werealestate.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** El historial de lotes debe distinguir un apartado hecho desde una página pública (el nombre del asesor es
 * quien lo hizo) de uno hecho en el panel (el nombre del asesor es para quién se apartó). */
class MovimientoLoteDtoTest {

    private static final Usuario SISTEMA_PUBLICO =
            new Usuario("Cotizador público (sin sesión)", "cotizador-publico@weinversiones.com", "x", Role.ASESOR, null);
    private static final Usuario ADMIN = new Usuario("Administrador We", "admin@weinversiones.com", "x", Role.ADMIN, null);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static Lote lote() {
        Desarrollo d = mock(Desarrollo.class);
        when(d.getNombre()).thenReturn("SAMAI Campestre");
        return new Lote(d, "1", "5", new BigDecimal("200"));
    }

    private static MovimientoLote apartado(Usuario usuario, String nombreAsesor) {
        return new MovimientoLote(
                lote(), EstadoLote.DISPONIBLE, EstadoLote.APARTADO, usuario, nombreAsesor, "Cliente Y", null, "ok");
    }

    @Test
    void elApartadoDesdeLaPaginaPublicaVieneMarcado() {
        MovimientoLoteDto dto = MovimientoLoteDto.from(apartado(SISTEMA_PUBLICO, "Sofía Ramírez"));
        assertTrue(dto.viaPublico());
        assertEquals("Sofía Ramírez", dto.nombreAsesor());
    }

    @Test
    void elApartadoHechoEnElPanelNoEsPublicoAunqueLleveAsesor() {
        MovimientoLoteDto dto = MovimientoLoteDto.from(apartado(ADMIN, "Sofía Ramírez"));
        assertFalse(dto.viaPublico());
        assertEquals("Administrador We", dto.usuario().nombre());
        assertEquals("Sofía Ramírez", dto.nombreAsesor());
    }

    @Test
    void laReversionAutomaticaNoTieneUsuarioNiEsPublica() {
        assertFalse(MovimientoLoteDto.from(apartado(null, null)).viaPublico());
    }

    @Test
    void unMovimientoDisfrazadoNoDelataQueFuePublico() {
        MovimientoLote mov = new MovimientoLote(
                lote(), EstadoLote.RESERVADO_WE, EstadoLote.APARTADO, SISTEMA_PUBLICO, "Asesor X", null, null, null);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y", "ROLE_ASESOR"));
        assertFalse(MovimientoLoteDto.from(mov).viaPublico());
    }
}
