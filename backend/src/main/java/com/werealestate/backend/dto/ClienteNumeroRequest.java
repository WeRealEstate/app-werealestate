package com.werealestate.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cambia el número de un cliente en un desarrollo; si otro ya lo tiene, se recorren +1 él y los consecutivos (ver
 * ClienteNumeracionService.cambiarNumero). */
public record ClienteNumeroRequest(@NotNull Long desarrolloId, @NotNull @Min(1) Long numero) {
}
