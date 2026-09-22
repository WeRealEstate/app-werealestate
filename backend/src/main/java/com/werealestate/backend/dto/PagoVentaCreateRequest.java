package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoVentaCreateRequest(
        @NotNull LocalDate fecha, @NotNull @Positive BigDecimal monto, @Size(max = 500) String notas) {
}
