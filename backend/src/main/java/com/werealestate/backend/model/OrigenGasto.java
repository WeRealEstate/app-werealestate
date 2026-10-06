package com.werealestate.backend.model;

/** De dónde viene un gasto: UNICO (compra de una sola vez), RECURRENTE (pago de un gasto con
 * vencimiento como renta o luz) o COMISION (entrega de una comisión de venta). */
public enum OrigenGasto {
    UNICO,
    RECURRENTE,
    COMISION
}
