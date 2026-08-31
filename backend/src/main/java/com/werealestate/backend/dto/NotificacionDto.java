package com.werealestate.backend.dto;

public record NotificacionDto(String tipo, String mensaje, Long leadId, Long tareaId) {

    public static NotificacionDto leadFrio(String mensaje, Long leadId) {
        return new NotificacionDto("LEAD_FRIO", mensaje, leadId, null);
    }

    public static NotificacionDto seguimientoPendiente(String mensaje, Long leadId) {
        return new NotificacionDto("SEGUIMIENTO_PENDIENTE", mensaje, leadId, null);
    }

    public static NotificacionDto tareaPendiente(String mensaje, Long tareaId) {
        return new NotificacionDto("TAREA_PENDIENTE", mensaje, null, tareaId);
    }
}
