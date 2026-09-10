package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CotizacionCreateRequest(
        @NotBlank String proyecto,
        @NotBlank String nombreCliente,
        String manzana,
        String lote,
        @NotNull @Positive BigDecimal superficie,
        @NotNull @Positive BigDecimal precioM2,
        @NotNull @Positive BigDecimal precioTotal,
        @NotBlank String formaPago,
        @NotBlank String engancheLabel,
        @NotNull BigDecimal enganche,
        @NotNull BigDecimal montoFinanciado,
        @NotNull Integer meses,
        @NotNull BigDecimal mensualidad,
        @NotNull BigDecimal interesPorcentaje,
        @NotNull BigDecimal interesMonto,
        @NotNull BigDecimal totalInversion) {
}
