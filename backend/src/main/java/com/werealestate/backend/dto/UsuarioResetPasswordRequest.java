package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioResetPasswordRequest(
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password) {
}
