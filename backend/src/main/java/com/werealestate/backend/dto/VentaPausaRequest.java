package com.werealestate.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Pausa de mensualidades (ver PausaMensualidadService.pausar): la mensualidad {@code numero} del plan vigente pasa a
 * {@code fecha} (posterior a la que tenía) y las siguientes se recorren. Exige la contraseña y un motivo.
 */
public record VentaPausaRequest(
        @NotBlank String password,
        @NotBlank @Size(max = 500) String motivo,
        @NotNull @Min(1) Integer numero,
        @NotNull LocalDate fecha) {
}
