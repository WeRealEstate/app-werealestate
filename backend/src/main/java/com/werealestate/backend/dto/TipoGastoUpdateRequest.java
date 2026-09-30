package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TipoGastoUpdateRequest(@NotBlank String nombre, boolean requiereTicket, boolean activo) {
}
