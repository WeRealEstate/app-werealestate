package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificacionMarcarLeidaRequest(@NotBlank String tipo, @NotNull Long entidadId, @NotBlank String firma) {
}
