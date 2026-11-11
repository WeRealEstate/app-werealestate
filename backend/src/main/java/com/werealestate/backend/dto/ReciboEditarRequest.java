package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoAbono;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Edita un recibo: folio, nota, fecha y, por línea, tipo, meses, aportación y monto. fecha y monto: null = no se
 * toca. Cambiar algún monto exige la contraseña de quien lo hace y un motivo (queda en la bitácora del recibo). */
public record ReciboEditarRequest(
        @NotBlank @Size(max = 50) String folio,
        java.time.LocalDate fecha,
        @Size(max = 500) String notas,
        @NotEmpty @Valid List<Linea> lineas,
        String password,
        @Size(max = 500) String motivo) {

    public record Linea(
            @NotNull Long pagoId,
            @NotNull TipoAbono tipo,
            @Valid List<PagoVentaCreateRequest.Mes> meses,
            Long aportacionId,
            Boolean extemporaneo,
            @jakarta.validation.constraints.DecimalMin(value = "0.01") java.math.BigDecimal monto) {
    }
}
