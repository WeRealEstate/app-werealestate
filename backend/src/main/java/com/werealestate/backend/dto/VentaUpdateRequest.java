package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Igual que VentaCreateRequest pero sin lotes ni marcarLoteVendido: no toca qué lotes incluye la
 * venta ni sus precios, solo los datos capturados (cliente, fechas, términos de financiamiento).
 * Temporal: el botón que usa esto en el frontend se va a quitar más adelante. */
public record VentaUpdateRequest(
        @NotBlank String cliente,
        @NotBlank String asesor,
        @NotBlank String formaPago,
        @NotNull LocalDate fechaVenta,
        @Positive BigDecimal mensualidad,
        @Positive Integer plazoMeses,
        String engancheLabel,
        @Positive BigDecimal enganche,
        @Size(max = 1000) String notas) {
}
