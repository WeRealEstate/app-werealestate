package com.werealestate.backend.dto;

import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.TipoSeguimiento;
import java.time.LocalDateTime;

public record SeguimientoDto(
        Long id,
        Long leadId,
        UsuarioResumenDto asesor,
        LocalDateTime fecha,
        TipoSeguimiento tipo,
        String nota,
        String resultado,
        LocalDateTime proximoSeguimiento) {

    public static SeguimientoDto from(Seguimiento seguimiento) {
        return new SeguimientoDto(
                seguimiento.getId(),
                seguimiento.getLead().getId(),
                UsuarioResumenDto.from(seguimiento.getAsesor()),
                seguimiento.getFecha(),
                seguimiento.getTipo(),
                seguimiento.getNota(),
                seguimiento.getResultado(),
                seguimiento.getProximoSeguimiento());
    }
}
