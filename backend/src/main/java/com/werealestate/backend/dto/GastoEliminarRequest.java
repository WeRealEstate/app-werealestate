package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Pide eliminar un egreso; exige la contraseña de quien lo pide como confirmación. Ver GastoService.eliminar. */
public record GastoEliminarRequest(@NotBlank String password) {
}
