package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionUpdateRequest(
        @NotBlank String nombre,
        @NotNull @Positive BigDecimal mensualidadFija,
        String descripcion,
        LocalDateTime fechaFin) {
}
