package com.werealestate.backend.dto;

import com.werealestate.backend.model.Comision;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComisionDto(
        Long id,
        Long leadId,
        String leadNombreCliente,
        UsuarioResumenDto asesor,
        BigDecimal monto,
        BigDecimal porcentajeAplicado,
        boolean pagada,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaPago) {

    public static ComisionDto from(Comision comision) {
        return new ComisionDto(
                comision.getId(),
                comision.getLead().getId(),
                comision.getLead().getNombreCliente(),
                UsuarioResumenDto.from(comision.getAsesor()),
                comision.getMonto(),
                comision.getPorcentajeAplicado(),
                comision.isPagada(),
                comision.getFechaCreacion(),
                comision.getFechaPago());
    }
}
