package com.werealestate.backend.dto;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.ExperienciaAsesor;
import com.werealestate.backend.model.UbicacionDocumento;
import java.time.LocalDateTime;

/** Datos secundarios de un asesor externo (ver AsesorExterno). traidoPorTipo: USUARIO, ASESOR u
 * OTRO; null si no se ha definido. */
public record AsesorFichaDto(
        Long id,
        String nombre,
        LocalDateTime fechaCreacion,
        ExperienciaAsesor experiencia,
        UbicacionDocumento contratoCopia,
        String contratoDriveUrl,
        Boolean expedienteAplica,
        UbicacionDocumento expedienteUbicacion,
        String expedienteDriveUrl,
        String traidoPorTipo,
        Long traidoPorId,
        String traidoPorNombre,
        String notas) {

    public static AsesorFichaDto from(AsesorExterno a) {
        String tipo = null;
        Long id = null;
        String nombre = null;
        if (a.getTraidoPorUsuario() != null) {
            tipo = "USUARIO";
            id = a.getTraidoPorUsuario().getId();
            nombre = a.getTraidoPorUsuario().getNombre();
        } else if (a.getTraidoPorAsesor() != null) {
            tipo = "ASESOR";
            id = a.getTraidoPorAsesor().getId();
            nombre = a.getTraidoPorAsesor().getNombre();
        } else if (a.getTraidoPorOtro() != null) {
            tipo = "OTRO";
            nombre = a.getTraidoPorOtro();
        }
        return new AsesorFichaDto(
                a.getId(),
                a.getNombre(),
                a.getFechaCreacion(),
                a.getExperiencia(),
                a.getContratoCopia(),
                a.getContratoDriveUrl(),
                a.getExpedienteAplica(),
                a.getExpedienteUbicacion(),
                a.getExpedienteDriveUrl(),
                tipo,
                id,
                nombre,
                a.getNotas());
    }
}
