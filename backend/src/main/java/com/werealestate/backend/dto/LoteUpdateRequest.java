package com.werealestate.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LoteUpdateRequest(
        @NotBlank String manzana,
        @NotBlank String numeroLote,
        @NotNull @DecimalMin(value = "1") BigDecimal superficie) {
}
