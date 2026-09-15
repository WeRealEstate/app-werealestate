package com.werealestate.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** mapaX/mapaY van en null juntos para borrar el pin de un lote en el plano; ver
 * LoteService.actualizarPosicionMapa. */
public record ActualizarPosicionMapaRequest(
        @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal mapaX,
        @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal mapaY) {
}
