package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Pide borrar la delimitación de todos los lotes de un desarrollo; exige la contraseña del admin
 * que lo pide como confirmación. Ver LoteService.limpiarMapa. */
public record LimpiarMapaRequest(@NotNull Long desarrolloId, @NotBlank String password) {
}
