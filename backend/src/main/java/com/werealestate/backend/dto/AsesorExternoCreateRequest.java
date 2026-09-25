package com.werealestate.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AsesorExternoCreateRequest(@NotBlank String nombre, @NotBlank String celular, @NotBlank @Email String correo) {
}
