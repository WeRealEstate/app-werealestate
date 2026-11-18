package com.werealestate.backend.dto;

import java.time.LocalDateTime;

/** Lo que el detalle de la venta necesita para el botón "Enviar correos de liquidación". Ver LiquidacionAvisoService. */
public record LiquidacionAvisoDto(
        // false si la venta no es solo de SAMAI Campestre (el botón ni se muestra).
        boolean disponible,
        boolean puedeEnviar,
        // Por qué no se puede enviar (null si se puede): aún hay saldo, etc.
        String motivoNoDisponible,
        // Correo del cliente titular (null si no tiene: entonces solo se avisa a finanzas).
        String correoCliente,
        // El último envío; null si nunca se ha enviado.
        Ultimo ultimo) {

    public record Ultimo(
            LocalDateTime enviadoEn, String usuario, String correoCliente, boolean clienteEnviado, boolean finanzasEnviado) {
    }
}
