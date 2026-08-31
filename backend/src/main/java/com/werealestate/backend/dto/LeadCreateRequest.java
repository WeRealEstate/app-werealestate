package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LeadCreateRequest(
        @NotBlank String nombreCliente,
        @NotBlank String telefono,
        String email,
        String origen,
        @NotNull Long desarrolloId,
        BigDecimal valorEstimado) {
}
