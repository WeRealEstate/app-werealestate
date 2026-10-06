package com.werealestate.backend.model;

/** Cómo se paga la comisión: UNA_EXHIBICION cuando el enganche/pago inicial de la venta alcanza a
 * cubrirla (se entrega completa); PARCIALIDADES cuando no, y entonces la mitad de cada abono del
 * cliente va para la comisión hasta completarla. */
public enum ModalidadComision {
    UNA_EXHIBICION,
    PARCIALIDADES
}
