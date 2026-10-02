package com.werealestate.backend.dto;

import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** modulos null = restablecer a los que trae su rol por defecto (lo que se quiere al cambiarle el
 * rol); para conservar los actuales hay que mandarlos explícitos. */
public record UsuarioUpdateRequest(@NotBlank String nombre, @NotNull Role rol, boolean activo, List<Modulo> modulos) {
}
