package com.werealestate.backend.dto;

import java.math.BigDecimal;

/** Totales de todas las comisiones no canceladas. */
public record ComisionResumenDto(
        long cantidad,
        BigDecimal total,
        BigDecimal devengado,
        BigDecimal entregado,
        BigDecimal porEntregar,
        BigDecimal pendienteDeDevengar) {
}
