package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoAbono;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Un recibo: folio, fecha, nota y una o más líneas (un concepto cada una). */
public record PagoVentaCreateRequest(
        @NotNull LocalDate fecha,
        @NotBlank @Size(max = 50) String folio,
        @Size(max = 500) String notas,
        @NotEmpty @Valid List<Linea> lineas) {

    public record Linea(
            @NotNull TipoAbono tipo,
            @NotNull @Positive BigDecimal monto,
            // Solo MENSUALIDAD: al menos un mes.
            @Valid List<Mes> meses,
            // Solo APORTACION: la aportación programada de la venta que paga.
            Long aportacionId) {
    }

    public record Mes(@NotNull @Min(2000) @Max(2200) Integer anio, @NotNull @Min(1) @Max(12) Integer mes) {
    }
}
