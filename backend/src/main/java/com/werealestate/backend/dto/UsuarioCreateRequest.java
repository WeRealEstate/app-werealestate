package com.werealestate.backend.dto;

import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** modulos null = los que trae su rol por defecto. */
public record UsuarioCreateRequest(
        @NotBlank String nombre,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
        @NotNull Role rol,
        List<Modulo> modulos) {
}
