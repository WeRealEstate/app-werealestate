package com.werealestate.backend.model;

/** Se calcula, no se guarda: CANCELADA (un admin la canceló), PAGADA (ya se entregó todo),
 * PARCIAL (se entregó una parte), ACUMULANDO (ya generó algo, nada entregado) o PENDIENTE (todavía
 * no genera nada). */
public enum EstadoComision {
    PENDIENTE,
    ACUMULANDO,
    PARCIAL,
    PAGADA,
    CANCELADA
}
