package com.werealestate.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.MovimientoLoteDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** "Reservado por We" solo lo ve admin y administración; para todos los demás (y sin sesión) es "Vendido". */
class VisibilidadLoteTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void entrarComo(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y", "ROLE_" + rol));
    }

    private static Lote loteReservado() {
        Desarrollo d = mock(Desarrollo.class);
        when(d.getId()).thenReturn(1L);
        when(d.getNombre()).thenReturn("SAMAI Campestre");
        when(d.getPrecioM2()).thenReturn(new BigDecimal("800"));
        Lote lote = new Lote(d, "1", "5", new BigDecimal("200"));
        lote.cambiarEstado(EstadoLote.RESERVADO_WE, null);
        return lote;
    }

    @Test
    void sinSesionSeVeVendido() {
        assertEquals(EstadoLote.VENDIDO, VisibilidadLote.visible(EstadoLote.RESERVADO_WE));
        assertEquals(EstadoLote.VENDIDO, LoteDto.from(loteReservado()).estado());
    }

    @Test
    void asesoresYEquipoInternoLoVenVendido() {
        for (String rol : new String[] {"ASESOR", "EQUIPO_INTERNO"}) {
            entrarComo(rol);
            assertFalse(VisibilidadLote.veReservadosWe(), rol);
            assertEquals(EstadoLote.VENDIDO, LoteDto.from(loteReservado()).estado(), rol);
        }
    }

    @Test
    void adminYAdministracionLoVenComoReservado() {
        for (String rol : new String[] {"ADMIN", "LIDER_AREA"}) {
            entrarComo(rol);
            assertTrue(VisibilidadLote.veReservadosWe(), rol);
            assertEquals(EstadoLote.RESERVADO_WE, LoteDto.from(loteReservado()).estado(), rol);
        }
    }

    @Test
    void losEndpointsPublicosSiempreVenVendidoAunConSesionDeAdmin() {
        for (String rol : new String[] {"ADMIN", "LIDER_AREA", "ASESOR"}) {
            entrarComo(rol);
            assertEquals(EstadoLote.VENDIDO, LoteDto.paraPublico(loteReservado()).estado(), rol);
        }
        SecurityContextHolder.clearContext();
        assertEquals(EstadoLote.VENDIDO, LoteDto.paraPublico(loteReservado()).estado());
    }

    @Test
    void paraPublicoNoCambiaLosOtrosEstados() {
        entrarComo("ADMIN");
        for (EstadoLote e : EstadoLote.values()) {
            if (e == EstadoLote.RESERVADO_WE) continue;
            assertEquals(e, VisibilidadLote.comoPublico(e));
        }
    }

    @Test
    void losOtrosEstadosNuncaCambian() {
        for (EstadoLote e : EstadoLote.values()) {
            if (e == EstadoLote.RESERVADO_WE) continue;
            assertEquals(e, VisibilidadLote.visible(e));
        }
    }

    @Test
    void elHistorialDeUnReservadoSeDisfrazaSinNotaNiCliente() {
        Usuario admin = mock(Usuario.class);
        MovimientoLote mov = new MovimientoLote(
                loteReservado(), EstadoLote.DISPONIBLE, EstadoLote.RESERVADO_WE, admin, "Asesor X", "Cliente Y",
                new BigDecimal("1000"), "Se lo guarda Francisco");

        entrarComo("ASESOR");
        MovimientoLoteDto visto = MovimientoLoteDto.from(mov);
        assertEquals(EstadoLote.DISPONIBLE, visto.estadoAnterior());
        assertEquals(EstadoLote.VENDIDO, visto.estadoNuevo());
        assertNull(visto.nota());
        assertNull(visto.nombreAsesor());
        assertNull(visto.nombreCliente());
        assertNull(visto.monto());
        assertNull(visto.usuario());

        entrarComo("LIDER_AREA");
        MovimientoLoteDto completo = MovimientoLoteDto.from(mov);
        assertEquals(EstadoLote.RESERVADO_WE, completo.estadoNuevo());
        assertEquals("Se lo guarda Francisco", completo.nota());
        assertEquals("Cliente Y", completo.nombreCliente());
    }

    @Test
    void unMovimientoNormalNoSeToca() {
        MovimientoLote mov = new MovimientoLote(
                loteReservado(), EstadoLote.DISPONIBLE, EstadoLote.APARTADO, null, "Asesor X", "Cliente Y", null, "ok");
        entrarComo("ASESOR");
        MovimientoLoteDto visto = MovimientoLoteDto.from(mov);
        assertEquals("ok", visto.nota());
        assertEquals("Cliente Y", visto.nombreCliente());
    }
}
