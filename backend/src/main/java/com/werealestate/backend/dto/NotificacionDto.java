package com.werealestate.backend.dto;

/** firma identifica la ocurrencia concreta de la notificación (ver NotificacionService y
 * NotificacionLeida): al marcarla como leída, se guarda junto con tipo y el id de la entidad, y
 * mientras la firma no cambie (la condición no se "renueva" de verdad) no vuelve a aparecer. */
public record NotificacionDto(String tipo, String mensaje, Long leadId, Long tareaId, Long eventoId, String firma) {

    public static NotificacionDto leadFrio(String mensaje, Long leadId, String firma) {
        return new NotificacionDto("LEAD_FRIO", mensaje, leadId, null, null, firma);
    }

    public static NotificacionDto leadSinContactar(String mensaje, Long leadId, String firma) {
        return new NotificacionDto("LEAD_SIN_CONTACTAR", mensaje, leadId, null, null, firma);
    }

    public static NotificacionDto seguimientoPendiente(String mensaje, Long leadId, String firma) {
        return new NotificacionDto("SEGUIMIENTO_PENDIENTE", mensaje, leadId, null, null, firma);
    }

    public static NotificacionDto tareaPendiente(String mensaje, Long tareaId, String firma) {
        return new NotificacionDto("TAREA_PENDIENTE", mensaje, null, tareaId, null, firma);
    }

    public static NotificacionDto eventoPendiente(String mensaje, Long eventoId, String firma) {
        return new NotificacionDto("EVENTO_PENDIENTE", mensaje, null, null, eventoId, firma);
    }
}
