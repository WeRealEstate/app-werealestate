package com.werealestate.backend.dto;

/** firma identifica la ocurrencia concreta de la notificación (ver NotificacionService y
 * NotificacionLeida): al marcarla como leída, se guarda junto con tipo y el id de la entidad, y
 * mientras la firma no cambie (la condición no se "renueva" de verdad) no vuelve a aparecer.
 * movimientoId (avisos de lotes) es el id del MovimientoLote que originó el aviso; ventaId, el de la venta; loteId, el
 * del lote en los avisos de lotes desincronizados con Ventas. fija = no se puede descartar: desaparece sola cuando se
 * corrige lo que la provoca. */
public record NotificacionDto(
        String tipo,
        String mensaje,
        Long leadId,
        Long tareaId,
        Long eventoId,
        Long movimientoId,
        Long ventaId,
        String firma,
        Long loteId,
        boolean fija) {

    public NotificacionDto(
            String tipo, String mensaje, Long leadId, Long tareaId, Long eventoId, Long movimientoId, Long ventaId, String firma) {
        this(tipo, mensaje, leadId, tareaId, eventoId, movimientoId, ventaId, firma, null, false);
    }

    public static NotificacionDto seguimientoPendiente(String mensaje, Long leadId, String firma) {
        return new NotificacionDto("SEGUIMIENTO_PENDIENTE", mensaje, leadId, null, null, null, null, firma);
    }

    public static NotificacionDto tareaPendiente(String mensaje, Long tareaId, String firma) {
        return new NotificacionDto("TAREA_PENDIENTE", mensaje, null, tareaId, null, null, null, firma);
    }

    public static NotificacionDto eventoPendiente(String mensaje, Long eventoId, String firma) {
        return new NotificacionDto("EVENTO_PENDIENTE", mensaje, null, null, eventoId, null, null, firma);
    }

    public static NotificacionDto loteApartado(String mensaje, Long movimientoId, String firma) {
        return new NotificacionDto("LOTE_APARTADO", mensaje, null, null, null, movimientoId, null, firma);
    }

    public static NotificacionDto loteDesapartado(String mensaje, Long movimientoId, String firma) {
        return new NotificacionDto("LOTE_DESAPARTADO", mensaje, null, null, null, movimientoId, null, firma);
    }

    /** Cualquier otro cambio de estado de un lote (firma, vendido, entre apartados…). */
    public static NotificacionDto loteEstado(String mensaje, Long movimientoId, String firma) {
        return new NotificacionDto("LOTE_ESTADO", mensaje, null, null, null, movimientoId, null, firma);
    }

    /** Se registró una venta (solo admin y Administración). */
    public static NotificacionDto ventaNueva(String mensaje, Long ventaId, String firma) {
        return new NotificacionDto("VENTA_NUEVA", mensaje, null, null, null, null, ventaId, firma);
    }

    /** Lote Vendido sin ninguna venta registrada (aviso fijo para admin y Administración). */
    public static NotificacionDto loteSinVenta(String mensaje, Long loteId, String firma) {
        return new NotificacionDto("LOTE_SIN_VENTA", mensaje, null, null, null, null, null, firma, loteId, true);
    }

    /** Lote que está en una venta pero no está Vendido ni En proceso de firma (aviso fijo para admin y Administración). */
    public static NotificacionDto loteVentaDesalineada(String mensaje, Long loteId, Long ventaId, String firma) {
        return new NotificacionDto("LOTE_VENTA_DESALINEADA", mensaje, null, null, null, null, ventaId, firma, loteId, true);
    }

    /** Un asesor envió un registro de venta de We Forms (admin y personal con el módulo We Forms). ventaId = la venta que se creó. */
    public static NotificacionDto registroVentaNuevo(String mensaje, Long respuestaId, Long ventaId, String firma) {
        return new NotificacionDto("REGISTRO_VENTA_NUEVO", mensaje, null, null, null, respuestaId, ventaId, firma);
    }
}
