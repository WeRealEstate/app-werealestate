package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cómo aplicar el depósito pendiente de una venta que nació de un registro de venta (ver VentaService.aplicarDeposito).
 * folio: obligatorio con SALDO (el abono que se registra). */
public record AplicarDepositoRequest(@NotNull AplicarDepositoA aplicarA, @Size(max = 50) String folio) {

    public enum AplicarDepositoA {
        SALDO,
        MENSUALIDAD
    }
}
