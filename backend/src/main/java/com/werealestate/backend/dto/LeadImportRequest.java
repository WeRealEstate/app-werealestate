package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeadImportRequest(
        @NotBlank String nombreCliente,
        @NotBlank String telefono,
        String email,
        @NotNull Long desarrolloId,
        String origen,
        String notas) {
}
