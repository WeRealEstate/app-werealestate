package com.werealestate.backend.dto;

import java.time.LocalDateTime;

/** Lo que el detalle de la venta necesita para el botón de avisar a finanzas la liquidación. Ver LiquidacionAvisoService. */
public record LiquidacionAvisoDto(
        boolean puedeEnviar,
        // Por qué no se puede enviar (null si se puede): aún hay saldo.
        String motivoNoDisponible,
        // El último envío; null si nunca se ha enviado.
        Ultimo ultimo) {

    public record Ultimo(LocalDateTime enviadoEn, String usuario) {
    }
}
