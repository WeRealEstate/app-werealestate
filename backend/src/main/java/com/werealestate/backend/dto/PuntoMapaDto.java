package com.werealestate.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Un vértice del polígono que delimita un lote sobre el plano, en % del ancho/alto de la imagen. */
public record PuntoMapaDto(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal x,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal y) {
}
