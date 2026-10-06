package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificarAsesorPublicoRequest(@NotBlank @Size(max = 200) String nombre) {
}
