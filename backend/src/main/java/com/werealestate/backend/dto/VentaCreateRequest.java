package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaCreateRequest(
        @NotNull Long loteId,
        @NotBlank String cliente,
        @NotBlank String asesor,
        @NotNull @Positive BigDecimal precioVenta,
        @NotBlank String formaPago,
        @NotNull LocalDate fechaVenta,
        // Términos de financiamiento; ambos opcionales (una venta de contado no los necesita).
        @Positive BigDecimal mensualidad,
        @Positive Integer plazoMeses,
        @Size(max = 1000) String notas,
        // Si es true, además de registrar la venta se marca el lote como VENDIDO (con su historial
        // normal, ver LoteService.marcarVendido). Ver VentaService.
        boolean marcarLoteVendido) {
}
