package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** El asesor devuelve (total o parcialmente) una comisión ya entregada. fecha: null = hoy. */
public record ComisionDevolucionRequest(
        @NotNull @Positive BigDecimal monto, LocalDate fecha, @Size(max = 500) String notas) {
}
