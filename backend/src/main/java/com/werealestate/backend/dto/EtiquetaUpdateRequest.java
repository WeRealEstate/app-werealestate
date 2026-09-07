package com.werealestate.backend.dto;

import com.werealestate.backend.model.EtiquetaColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EtiquetaUpdateRequest(@NotBlank @Size(max = 40) String nombre, @NotNull EtiquetaColor color) {
}
