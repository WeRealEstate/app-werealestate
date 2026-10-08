package com.werealestate.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** precioM2 null = quitar el precio propio de los lotes de la manzana (vuelven al del desarrollo). */
public record PrecioManzanaRequest(
        @NotNull Long desarrolloId, @NotBlank String manzana, @DecimalMin(value = "0.01") BigDecimal precioM2) {
}
