package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** fecha: null = hoy. conIva, conFactura y formaPago se eligen siempre (no tienen valor por defecto): solo
 * se marcan y salen en el ticket de pago, no cambian el monto. */
public record ComisionEntregaRequest(
        @NotNull @Positive BigDecimal monto,
        LocalDate fecha,
        @Size(max = 500) String notas,
        @NotNull(message = "Elige si la comisión va con o sin IVA") Boolean conIva,
        @NotNull(message = "Elige si la comisión va con o sin factura") Boolean conFactura,
        @NotNull(message = "Elige la forma de pago") ModalidadPago formaPago) {
}
