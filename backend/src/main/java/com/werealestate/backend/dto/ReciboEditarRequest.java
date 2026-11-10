package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoAbono;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Edita un recibo: folio, nota, fecha y, por línea, tipo, meses y aportación. El monto no se cambia: si está mal,
 * se anula el recibo y se registra uno nuevo. fecha: null = no se toca. */
public record ReciboEditarRequest(
        @NotBlank @Size(max = 50) String folio,
        java.time.LocalDate fecha,
        @Size(max = 500) String notas,
        @NotEmpty @Valid List<Linea> lineas) {

    public record Linea(
            @NotNull Long pagoId,
            @NotNull TipoAbono tipo,
            @Valid List<PagoVentaCreateRequest.Mes> meses,
            Long aportacionId,
            Boolean extemporaneo) {
    }
}
