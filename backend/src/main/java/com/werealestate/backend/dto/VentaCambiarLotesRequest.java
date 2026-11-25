package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Cambiar los lotes de una venta ya registrada: la lista completa como debe quedar (cada precio ya con el bono de la
 * promoción descontado, igual que al registrar), la mensualidad nueva (null = se queda la actual), la promoción (null =
 * sin promoción) y la contraseña de quien lo hace. */
public record VentaCambiarLotesRequest(
        @NotEmpty @Valid List<VentaLoteItemRequest> lotes,
        @Positive BigDecimal mensualidad,
        Long promocionId,
        @NotBlank String password) {
}
