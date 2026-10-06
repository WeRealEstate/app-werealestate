package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ComisionDetalleDto(ComisionDto comision, List<Devengo> devengos, List<Entrega> entregas) {

    public record Devengo(LocalDate fechaOrigen, LocalDate fechaEntrega, BigDecimal monto) {
    }

    public record Entrega(
            Long id,
            LocalDate fecha,
            BigDecimal monto,
            String notas,
            Long gastoId,
            String registradaPor,
            LocalDateTime fechaCreacion) {
    }
}
