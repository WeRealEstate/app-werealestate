package com.werealestate.backend.model;

/** Cómo paga el cliente una venta: en efectivo o por transferencia (una sola; null = sin definir). No es la
 * "forma de pago" de la venta, que describe el esquema (p. ej. "Sin enganche · 60 MSI"). */
public enum ModalidadPago {
    EFECTIVO,
    TRANSFERENCIA
}
