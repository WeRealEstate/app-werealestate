package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionCreateRequest(
        @NotBlank String nombre,
        @NotBlank @Pattern(regexp = "samai|nanuu", message = "Debe ser 'samai' o 'nanuu'") String proyecto,
        @NotNull @Positive BigDecimal mensualidadFija,
        String descripcion,
        /** Nulo = la promoción no vence automáticamente. */
        LocalDateTime fechaFin) {
}
