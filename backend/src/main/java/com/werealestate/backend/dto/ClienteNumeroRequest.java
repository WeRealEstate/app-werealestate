package com.werealestate.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cambia el número de un cliente; si otro ya lo tiene, se recorren +1 él y los consecutivos (ver ClienteService.cambiarNumero). */
public record ClienteNumeroRequest(@NotNull @Min(1) Long numero) {
}
