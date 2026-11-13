package com.werealestate.backend.service;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El estado de los lotes que manda su venta: al registrarla, al firmar el contrato, al eliminarla o al alinear un lote
 * que quedó desfasado. Todo cambio pasa por el historial del lote. */
@Service
@Transactional
public class LoteVentaService {

    /** Nota que queda en el historial del lote al pasar a "En proceso de firma" desde We Forms. */
    public static final String NOTA_REGISTRO_VENTA = "Registro de venta We Forms";
    /** Texto que tenía esa nota antes de cambiar el nombre: en el historial ya guardado sigue apareciendo así. */
    private static final Set<String> NOTAS_REGISTRO_VENTA = Set.of(NOTA_REGISTRO_VENTA, "Precontrato We Forms");

    /** Si la nota de un movimiento es la de un registro de venta de We Forms (la actual o la anterior). Acepta null:
     * la mayoría de los movimientos no llevan nota, y Set.of(...).contains(null) lanzaría NullPointerException. */
    public static boolean esNotaDeRegistroDeVenta(String nota) {
        return nota != null && NOTAS_REGISTRO_VENTA.contains(nota);
    }

    private final VentaLoteRepository ventaLoteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LoteConsultaService consulta;
    private final HistorialLoteService historial;
    private final AutorizacionLotes autorizacion;

    public LoteVentaService(
            VentaLoteRepository ventaLoteRepository,
            CurrentUserProvider currentUserProvider,
            LoteConsultaService consulta,
            HistorialLoteService historial,
            AutorizacionLotes autorizacion) {
        this.ventaLoteRepository = ventaLoteRepository;
        this.currentUserProvider = currentUserProvider;
        this.consulta = consulta;
        this.historial = historial;
        this.autorizacion = autorizacion;
    }

    /** Devuelve a DISPONIBLE, con su historial, un lote que se quedó sin venta porque se eliminó la suya (VentaService.
     * eliminar ya revisó que ninguna otra lo tenga): tanto si estaba Vendido como En proceso de firma, porque sin venta
     * no puede seguir así. Se limpian el vencimiento y el dinero apartado, que ya no aplican. Si estaba en otro estado
     * (datos de antes de que lo mandara la venta) se deja como está. */
    public void liberarPorVentaEliminada(Long id) {
        Lote lote = consulta.obtenerEntidad(id);
        if (!EstadosDeLote.DE_VENTA.contains(lote.getEstado())) return;
        Usuario actual = currentUserProvider.getUsuarioActual();
        historial.cambiarEstadoConHistorial(lote, EstadoLote.DISPONIBLE, actual, null, null, null, "Venta eliminada");
        lote.setFechaExpiraApartado(null);
        lote.setMontoApartado(null);
    }

    /** El lote pasa a EN_PROCESO_DE_FIRMA a raíz de un registro de venta de We Forms (ver VentaService.
     * crearDesdeRegistroDeVenta), sin sesión: el cambio queda en el historial a nombre del usuario de sistema.
     * A diferencia de marcarPorVenta, el dinero con el que estuviera apartado NO se borra: queda pendiente
     * de aplicar en la venta (ver Venta.depositoPendiente) y lo limpia {@link #limpiarMontoApartado}. */
    public void marcarEnProcesoDeFirma(Long id, String cliente, String asesor, Usuario actor) {
        Lote lote = consulta.obtenerEntidad(id);
        historial.cambiarEstadoConHistorial(
                lote, EstadoLote.EN_PROCESO_DE_FIRMA, actor, asesor, cliente, null, NOTA_REGISTRO_VENTA);
        lote.setFechaExpiraApartado(null);
    }

    /** El dinero apartado del lote ya se aplicó a su venta: deja de aparecer como depósito pendiente. */
    public void limpiarMontoApartado(Long id) {
        consulta.obtenerEntidad(id).setMontoApartado(null);
    }

    /** El lote entra a una venta registrada desde el panel (ver VentaService): Vendido si el contrato ya está firmado,
     * En proceso de firma si no. Mismo historial que cualquier otro cambio de estado; no repite el chequeo de rol de
     * {@link #cambiarEstado}: VentaService ya exige admin o administración para registrar una venta. */
    public void marcarPorVenta(Long id, boolean contratoFirmado, String cliente, String asesor) {
        Lote lote = consulta.obtenerEntidad(id);
        Usuario actual = currentUserProvider.getUsuarioActual();
        EstadoLote estado = contratoFirmado ? EstadoLote.VENDIDO : EstadoLote.EN_PROCESO_DE_FIRMA;
        historial.cambiarEstadoConHistorial(lote, estado, actual, asesor, cliente, null,
                contratoFirmado ? "Venta registrada" : "Venta registrada, contrato por firmar");
        lote.setFechaExpiraApartado(null);
        // El formulario de Ventas ya leyó montoApartado (vía LoteDto) antes de llegar aquí, para
        // decidir si ese dinero baja la mensualidad o el saldo; una vez en la venta, ya no representa
        // un depósito pendiente de aplicar.
        lote.setMontoApartado(null);
    }

    /** Pone el lote en el estado que le manda su venta cuando cambia la firma del contrato (ver VentaService.
     * registrarContrato): Vendido al firmarse, En proceso de firma si se le quita la firma. Si ya está así no deja
     * movimiento. No toca el dinero apartado: en una venta que vino de We Forms puede seguir pendiente de aplicar
     * (Venta.depositoPendiente). */
    public void ponerEstadoDeVenta(Long id, EstadoLote estado, String cliente, String nota) {
        if (!EstadosDeLote.DE_VENTA.contains(estado)) {
            throw new IllegalArgumentException("Un lote con venta solo puede estar Vendido o En proceso de firma");
        }
        Lote lote = consulta.obtenerEntidad(id);
        if (lote.getEstado() == estado) return;
        historial.cambiarEstadoConHistorial(lote, estado, currentUserProvider.getUsuarioActual(), null, cliente, null, nota);
        lote.setFechaExpiraApartado(null);
    }

    /**
     * Atajo para un lote que está en una venta pero quedó en otro estado (datos de antes de que la venta mandara el
     * estado del lote; ver el aviso LOTE_VENTA_DESALINEADA de NotificacionService): lo pone en el estado que le toca
     * por su venta —Vendido si alguna de sus ventas tiene el contrato firmado, En proceso de firma si no— con el
     * cliente y el asesor de esa venta y la nota "Alineado con la venta #N", sin pedir más datos. Solo admin y
     * Administración. Igual que un cambio a mano a esos estados, deja de contar el dinero apartado del lote.
     */
    public LoteDto alinearConVenta(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (!AutorizacionLotes.esAdminOLider(actual)) {
            throw new ForbiddenOperationException(
                    "Solo un administrador o personal de administración puede alinear un lote con su venta");
        }
        Lote lote = consulta.obtenerEntidad(id);
        List<VentaLote> ventasDelLote = ventaLoteRepository.findByLoteId(id);
        if (ventasDelLote.isEmpty()) throw new ConflictException("Este lote no está en ninguna venta");
        if (EstadosDeLote.DE_VENTA.contains(lote.getEstado())) {
            throw new ConflictException("Este lote ya está como su venta lo indica");
        }
        // Manda una venta con el contrato firmado, si la hay; si no, la más reciente.
        Venta venta = ventasDelLote.stream()
                .map(VentaLote::getVenta)
                .filter(Venta::isContratoFirmado)
                .findFirst()
                .orElseGet(() -> ventasDelLote.stream()
                        .map(VentaLote::getVenta)
                        .max(Comparator.comparing(Venta::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElseThrow());
        EstadoLote estado = venta.isContratoFirmado() ? EstadoLote.VENDIDO : EstadoLote.EN_PROCESO_DE_FIRMA;
        historial.cambiarEstadoConHistorial(lote, estado, actual, venta.getAsesorNombre(), venta.getCliente(), null,
                "Alineado con la venta " + venta.getEtiqueta());
        lote.setFechaExpiraApartado(null);
        lote.setMontoApartado(null);
        return consulta.conVentas(lote);
    }
}
