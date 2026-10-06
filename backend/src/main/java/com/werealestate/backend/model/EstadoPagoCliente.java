package com.werealestate.backend.model;

/** Cómo va el cliente con lo que le toca pagar este mes: YA_ABONO (cubrió el mes), ABONO_PARCIAL
 * (abonó pero menos de lo esperado), POR_VENCER (aún no abona y su día de pago no ha llegado) o
 * SIN_ABONAR (su día de pago ya pasó y no ha abonado). Se calcula, no se guarda. */
public enum EstadoPagoCliente {
    YA_ABONO,
    ABONO_PARCIAL,
    POR_VENCER,
    SIN_ABONAR
}
