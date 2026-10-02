package com.werealestate.backend.dto;

/** firma identifica la ocurrencia concreta de la notificación (ver NotificacionService y
 * NotificacionLeida): al marcarla como leída, se guarda junto con tipo y el id de la entidad, y
 * mientras la firma no cambie (la condición no se "renueva" de verdad) no vuelve a aparecer.
 * movimientoId (avisos de lotes) es el id del MovimientoLote que originó el aviso. */
public record NotificacionDto(
        String tipo, String mensaje, Long leadId, Long tareaId, Long eventoId, Long movimientoId, String firma) {

    public static NotificacionDto seguimientoPendiente(String mensaje, Long leadId, String firma) {
        return new NotificacionDto("SEGUIMIENTO_PENDIENTE", mensaje, leadId, null, null, null, firma);
    }

    public static NotificacionDto tareaPendiente(String mensaje, Long tareaId, String firma) {
        return new NotificacionDto("TAREA_PENDIENTE", mensaje, null, tareaId, null, null, firma);
    }

    public static NotificacionDto eventoPendiente(String mensaje, Long eventoId, String firma) {
        return new NotificacionDto("EVENTO_PENDIENTE", mensaje, null, null, eventoId, null, firma);
    }

    public static NotificacionDto loteApartado(String mensaje, Long movimientoId, String firma) {
        return new NotificacionDto("LOTE_APARTADO", mensaje, null, null, null, movimientoId, firma);
    }

    public static NotificacionDto loteDesapartado(String mensaje, Long movimientoId, String firma) {
        return new NotificacionDto("LOTE_DESAPARTADO", mensaje, null, null, null, movimientoId, firma);
    }
}
