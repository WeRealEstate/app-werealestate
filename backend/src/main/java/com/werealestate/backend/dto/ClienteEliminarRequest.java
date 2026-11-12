package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Pide eliminar un cliente (ver EliminacionClienteService); exige la contraseña de quien lo hace. */
public record ClienteEliminarRequest(@NotBlank String password) {
}
