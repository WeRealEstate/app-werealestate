package com.werealestate.backend.model;

/** Cómo paga el cliente una venta: efectivo, transferencia o depósito (una sola; null = sin definir). No es la
 * "forma de pago" de la venta, que describe el esquema (p. ej. "Sin enganche · 60 MSI"). Las entregas de
 * comisión también guardan su forma con este enum, pero el panel solo ofrece efectivo y transferencia ahí. */
public enum ModalidadPago {
    EFECTIVO,
    TRANSFERENCIA,
    DEPOSITO
}
