package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un vencimiento sin pagar de un gasto recurrente. estado: PENDIENTE (aún no vence) o VENCIDO. */
public record GastoRecurrentePagoDto(
        Long id,
        Long recurrenteId,
        String nombre,
        LocalDate fechaVencimiento,
        BigDecimal montoEstimado,
        String estado) {
}
