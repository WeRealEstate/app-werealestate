package com.werealestate.backend.dto;

import com.werealestate.backend.model.FrecuenciaGasto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** dia: día del mes (1-31) en MENSUAL, BIMESTRAL, ANUAL y QUINCENAL (el primero); dia2: el segundo
 * día del mes, solo en QUINCENAL; en SEMANAL no se usan (cada 7 días desde primerVencimiento).
 * activo: null = sin cambios al editar (activo al crear). */
public record GastoRecurrenteRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotNull @PositiveOrZero BigDecimal montoEstimado,
        @NotNull FrecuenciaGasto frecuencia,
        @Min(1) @Max(31) Integer dia,
        @Min(1) @Max(31) Integer dia2,
        @NotNull LocalDate primerVencimiento,
        Boolean activo) {
}
