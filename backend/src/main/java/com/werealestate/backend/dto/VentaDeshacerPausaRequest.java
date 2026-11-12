package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deshace la pausa de mensualidades más reciente del plan vigente (ver PausaMensualidadService.deshacer). */
public record VentaDeshacerPausaRequest(
        @NotBlank String password,
        @NotBlank @Size(max = 500) String motivo) {
}
