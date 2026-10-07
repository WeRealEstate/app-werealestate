package com.werealestate.backend.dto;

import com.werealestate.backend.model.ExperienciaAsesor;
import com.werealestate.backend.model.UbicacionDocumento;
import jakarta.validation.constraints.Size;

/** Todo opcional (null = sin definir). traidoPor*: a lo más uno (usuario, asesor externo u otro con
 * texto). Los links de Drive deben ser http(s). */
public record AsesorFichaRequest(
        ExperienciaAsesor experiencia,
        UbicacionDocumento contratoCopia,
        @Size(max = 500) String contratoDriveUrl,
        Boolean expedienteAplica,
        UbicacionDocumento expedienteUbicacion,
        @Size(max = 500) String expedienteDriveUrl,
        Long traidoPorUsuarioId,
        Long traidoPorAsesorId,
        @Size(max = 200) String traidoPorOtro,
        @Size(max = 1000) String notas) {
}
