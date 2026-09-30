package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TipoGastoCreateRequest(@NotBlank String nombre, boolean requiereTicket) {
}
