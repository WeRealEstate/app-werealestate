package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lo que el detalle de la venta necesita para el botón "Enviar recordatorio de pago": si se puede mandar (y si no, por
 * qué), a qué correo, qué pago se recordaría y cuándo fue el último recordatorio enviado. Ver RecordatorioPagoService.
 */
public record RecordatorioPagoDto(
        boolean puedeEnviar,
        // Por qué no se puede enviar (null si se puede): "El cliente no tiene correo registrado" o "No hay pagos pendientes".
        String motivoNoDisponible,
        // Correo del cliente titular (null si no tiene).
        String correoDestino,
        // El siguiente pago sin cubrir que aún no vence; null si no hay (p. ej. solo hay atraso).
        ProximoPago proximoPago,
        BigDecimal atrasoMonto,
        int pagosAtrasados,
        int mensualidadesAtrasadas,
        // El último recordatorio que salió bien; null si nunca se ha enviado.
        Ultimo ultimo) {

    // arrastre: parte del monto que viene de pagos anteriores sin cubrir (0 si el monto es solo el del mes).
    public record ProximoPago(LocalDate fecha, BigDecimal monto, String concepto, BigDecimal arrastre) {
    }

    public record Ultimo(LocalDateTime enviadoEn, String usuario, String correo) {
    }
}
