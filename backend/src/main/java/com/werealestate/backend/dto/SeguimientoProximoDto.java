package com.werealestate.backend.dto;

import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.TipoSeguimiento;
import java.time.LocalDateTime;

public record SeguimientoProximoDto(
        Long id,
        Long leadId,
        String leadNombreCliente,
        UsuarioResumenDto asesor,
        TipoSeguimiento tipo,
        LocalDateTime proximoSeguimiento) {

    public static SeguimientoProximoDto from(Seguimiento seguimiento) {
        return new SeguimientoProximoDto(
                seguimiento.getId(),
                seguimiento.getLead().getId(),
                seguimiento.getLead().getNombreCliente(),
                UsuarioResumenDto.from(seguimiento.getAsesor()),
                seguimiento.getTipo(),
                seguimiento.getProximoSeguimiento());
    }
}
