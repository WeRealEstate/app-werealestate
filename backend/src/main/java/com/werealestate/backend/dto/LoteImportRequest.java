package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LoteImportRequest(
        @NotNull Long desarrolloId,
        @NotBlank String manzana,
        @NotBlank String numeroLote,
        @NotNull BigDecimal superficie) {
}
