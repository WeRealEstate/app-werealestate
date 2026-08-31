package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TareaCreateRequest(
        @NotBlank String titulo, String descripcion, @NotNull Long asignadoAId, LocalDate fechaLimite) {
}
