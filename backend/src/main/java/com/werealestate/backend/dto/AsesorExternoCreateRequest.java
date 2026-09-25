package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AsesorExternoCreateRequest(@NotBlank String nombre) {
}
