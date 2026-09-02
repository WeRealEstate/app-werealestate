package com.werealestate.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ComisionConfigRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentaje) {
}
