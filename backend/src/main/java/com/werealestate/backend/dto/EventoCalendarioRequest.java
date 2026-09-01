package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EventoCalendarioRequest(@NotBlank String titulo, String descripcion, @NotNull LocalDate fecha) {
}
