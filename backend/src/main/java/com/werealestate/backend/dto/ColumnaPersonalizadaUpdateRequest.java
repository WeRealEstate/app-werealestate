package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ColumnaPersonalizadaUpdateRequest(@NotBlank @Size(max = 60) String nombre) {
}
