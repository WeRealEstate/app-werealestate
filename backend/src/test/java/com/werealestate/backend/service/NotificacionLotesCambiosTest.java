package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.NotificacionLeidaRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Los avisos de cambios de lote (apartado, liberado, otro estado) llevan loteId para abrir Lotes filtrado,
 * pero su identidad (lo leído y la clave del push) sigue siendo el movimiento. */
class NotificacionLotesCambiosTest {

    private final MovimientoLoteRepository movimientos = mock(MovimientoLoteRepository.class);

    private MovimientoLote movimiento(Long id, Long loteId, EstadoLote antes, EstadoLote despues, LocalDateTime fecha) {
        Desarrollo desarrollo = mock(Desarrollo.class);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");
        Lote lote = mock(Lote.class);
        when(lote.getId()).thenReturn(loteId);
        when(lote.getManzana()).thenReturn("7");
        when(lote.getNumeroLote()).thenReturn("193");
        when(lote.getDesarrollo()).thenReturn(desarrollo);
        MovimientoLote m = mock(MovimientoLote.class);
        when(m.getId()).thenReturn(id);
        when(m.getLote()).thenReturn(lote);
        when(m.getEstadoAnterior()).thenReturn(antes);
        when(m.getEstadoNuevo()).thenReturn(despues);
        when(m.getFecha()).thenReturn(fecha);
        return m;
    }

    private List<NotificacionDto> avisos() {
        LocalDateTime ahora = LocalDateTime.now().minusHours(1);
        List<MovimientoLote> lista = List.of(
                movimiento(11L, 193L, EstadoLote.DISPONIBLE, EstadoLote.APARTADO, ahora),
                movimiento(12L, 194L, EstadoLote.APARTADO, EstadoLote.DISPONIBLE, ahora),
                movimiento(13L, 195L, EstadoLote.VENDIDO, EstadoLote.DISPONIBLE, ahora));
        when(movimientos.recientesDeOtros(any(), anyLong())).thenReturn(lista);
        NotificacionService service = new NotificacionService(
                mock(SeguimientoRepository.class),
                mock(TareaRepository.class),
                mock(EventoCalendarioRepository.class),
                mock(NotificacionLeidaRepository.class),
                mock(CurrentUserProvider.class),
                movimientos,
                mock(VentaRepository.class),
                mock(VentaLoteRepository.class),
                mock(WeFormRespuestaRepository.class),
                mock(LoteRepository.class));
        Usuario admin = mock(Usuario.class);
        when(admin.getId()).thenReturn(99L);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        return service.listarPara(admin).stream().filter(n -> n.tipo().startsWith("LOTE_")).toList();
    }

    @Test
    void llevanElLoteYConservanElMovimiento() {
        List<NotificacionDto> avisos = avisos();
        assertThat(avisos).extracting(NotificacionDto::tipo)
                .containsExactly("LOTE_APARTADO", "LOTE_DESAPARTADO", "LOTE_ESTADO");
        assertThat(avisos).extracting(NotificacionDto::loteId).containsExactly(193L, 194L, 195L);
        assertThat(avisos).extracting(NotificacionDto::movimientoId).containsExactly(11L, 12L, 13L);
        assertThat(avisos).allMatch(n -> !n.fija());
    }

    @Test
    void laClaveDelPushSigueSiendoLaDelMovimiento() {
        for (NotificacionDto n : avisos()) {
            assertThat(PushService.claveDe(n))
                    .isEqualTo(NotificacionService.clave(n.tipo(), n.movimientoId(), n.firma()));
        }
        // Los desincronizados (sin movimiento) siguen usando el lote.
        NotificacionDto sinVenta = NotificacionDto.loteSinVenta("x", 2L, "f");
        assertThat(PushService.claveDe(sinVenta)).isEqualTo(NotificacionService.clave("LOTE_SIN_VENTA", 2L, "f"));
        NotificacionDto desalineado = NotificacionDto.loteVentaDesalineada("x", 4L, 20L, "f");
        assertThat(PushService.claveDe(desalineado)).isEqualTo(NotificacionService.clave("LOTE_VENTA_DESALINEADA", 4L, "f"));
    }
}
