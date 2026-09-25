package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AsesorExternoUpdateRequest(@NotBlank String nombre, boolean activo) {
}
