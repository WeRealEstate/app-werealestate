package com.werealestate.backend.dto;

import java.math.BigDecimal;

/** Exactamente uno de los dos: el porcentaje (el monto se calcula sobre el valor de la venta) o el
 * monto en pesos (el porcentaje se calcula a partir de él). Ver ComisionService.editar. */
public record ComisionUpdateRequest(BigDecimal porcentaje, BigDecimal monto) {
}
