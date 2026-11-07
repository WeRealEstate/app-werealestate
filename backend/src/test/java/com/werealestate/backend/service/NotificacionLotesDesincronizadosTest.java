package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Avisos fijos a Administración de lotes que no cuadran con Ventas: Vendido sin venta y en una venta pero en otro estado. */
class NotificacionLotesDesincronizadosTest {

    private static final LocalDateTime CAMBIO = LocalDateTime.of(2026, 10, 1, 9, 30);

    private final LoteRepository lotes = mock(LoteRepository.class);
    private final VentaLoteRepository ventaLotes = mock(VentaLoteRepository.class);
    private final NotificacionLeidaRepository leidas = mock(NotificacionLeidaRepository.class);
    private NotificacionService service;

    @BeforeEach
    void preparar() {
        service = new NotificacionService(
                mock(SeguimientoRepository.class),
                mock(TareaRepository.class),
                mock(EventoCalendarioRepository.class),
                leidas,
                mock(CurrentUserProvider.class),
                mock(MovimientoLoteRepository.class),
                mock(VentaRepository.class),
                ventaLotes,
                mock(WeFormRespuestaRepository.class),
                lotes);
        Lote vendido = lote(2L, "1", "2", EstadoLote.VENDIDO);
        Lote disponible = lote(4L, "1", "4", EstadoLote.DISPONIBLE);
        Venta venta = mock(Venta.class);
        when(venta.getId()).thenReturn(20L);
        when(venta.getNumero()).thenReturn(2L);
        VentaLote vl = mock(VentaLote.class);
        when(vl.getLote()).thenReturn(disponible);
        when(vl.getVenta()).thenReturn(venta);
        when(lotes.findVendidosSinVenta()).thenReturn(List.of(vendido));
        when(ventaLotes.findConLoteFueraDeEstadoDeVenta()).thenReturn(List.of(vl));
    }

    private static Lote lote(Long id, String manzana, String numero, EstadoLote estado) {
        Desarrollo desarrollo = mock(Desarrollo.class);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");
        Lote lote = mock(Lote.class);
        when(lote.getId()).thenReturn(id);
        when(lote.getManzana()).thenReturn(manzana);
        when(lote.getNumeroLote()).thenReturn(numero);
        when(lote.getEstado()).thenReturn(estado);
        when(lote.getDesarrollo()).thenReturn(desarrollo);
        when(lote.getFechaCambioEstado()).thenReturn(CAMBIO);
        return lote;
    }

    private static Usuario usuario(Role rol) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(99L);
        when(u.getRol()).thenReturn(rol);
        return u;
    }

    private List<NotificacionDto> avisosDeLotes(Role rol) {
        return service.listarPara(usuario(rol)).stream()
                .filter(n -> n.tipo().equals("LOTE_SIN_VENTA") || n.tipo().equals("LOTE_VENTA_DESALINEADA"))
                .toList();
    }

    @Test
    void administracionRecibeUnAvisoFijoPorLote() {
        for (Role rol : List.of(Role.ADMIN, Role.LIDER_AREA)) {
            List<NotificacionDto> avisos = avisosDeLotes(rol);
            assertThat(avisos).hasSize(2);

            NotificacionDto sinVenta = avisos.get(0);
            assertThat(sinVenta.tipo()).isEqualTo("LOTE_SIN_VENTA");
            assertThat(sinVenta.mensaje()).isEqualTo("Mz 1 / Lote 2 de SAMAI Campestre está marcado como Vendido sin una "
                    + "venta registrada. Registra su venta o, si la empresa se lo guarda, cámbialo a Reservado por We.");
            assertThat(sinVenta.loteId()).isEqualTo(2L);
            assertThat(sinVenta.fija()).isTrue();

            NotificacionDto desalineado = avisos.get(1);
            assertThat(desalineado.tipo()).isEqualTo("LOTE_VENTA_DESALINEADA");
            assertThat(desalineado.mensaje()).isEqualTo("Mz 1 / Lote 4 de SAMAI Campestre está en la venta #2 pero aparece "
                    + "como Disponible. Debe estar Vendido (contrato firmado) o En proceso de firma.");
            assertThat(desalineado.loteId()).isEqualTo(4L);
            assertThat(desalineado.ventaId()).isEqualTo(20L);
            assertThat(desalineado.fija()).isTrue();
        }
    }

    @Test
    void unAsesorNoLosRecibe() {
        assertThat(avisosDeLotes(Role.ASESOR)).isEmpty();
        assertThat(avisosDeLotes(Role.EQUIPO_INTERNO)).isEmpty();
    }

    @Test
    void marcarlosComoLeidosNoLosQuita() {
        NotificacionLeida leida = mock(NotificacionLeida.class);
        when(leida.getTipo()).thenReturn("LOTE_SIN_VENTA");
        when(leida.getEntidadId()).thenReturn(2L);
        when(leida.getFirma()).thenReturn(CAMBIO.toString());
        when(leidas.findByUsuarioId(anyLong())).thenReturn(List.of(leida));

        assertThat(avisosDeLotes(Role.ADMIN)).extracting(NotificacionDto::tipo).contains("LOTE_SIN_VENTA");
    }
}
