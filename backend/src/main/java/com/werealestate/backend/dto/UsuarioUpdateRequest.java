package com.werealestate.backend.dto;

import com.werealestate.backend.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioUpdateRequest(@NotBlank String nombre, @NotNull Role rol, boolean activo) {
}
