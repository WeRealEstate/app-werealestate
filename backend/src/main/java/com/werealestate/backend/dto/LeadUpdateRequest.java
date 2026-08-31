package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLead;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LeadUpdateRequest(
        @NotBlank String nombreCliente,
        @NotBlank String telefono,
        String email,
        String origen,
        @NotNull EstadoLead estado,
        BigDecimal valorEstimado) {
}
