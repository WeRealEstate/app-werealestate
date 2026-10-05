package com.werealestate.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Una aportación de una venta nueva: mes (1-12) y año, con su monto. Ver VentaService.validarAportaciones. */
public record VentaAportacionItemRequest(
        @NotNull @Min(2000) @Max(2200) Integer anio,
        @NotNull @Min(1) @Max(12) Integer mes,
        @NotNull @Positive BigDecimal monto) {
}
