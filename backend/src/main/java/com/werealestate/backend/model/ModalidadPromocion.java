package com.werealestate.backend.model;

/**
 * Modalidad de una promoción del Cotizador:
 * - {@link #MENSUALIDAD_FIJA}: define una mensualidad fija para un lote estándar de 200 m²
 *   y el resto del precio se cubre con aportaciones anuales.
 * - {@link #BONO_LOTES}: descuenta un bono fijo del precio total al comprar 2 lotes
 *   ({@code bonoDosLotes}) o 3 o más lotes ({@code bonoTresLotes}), a un plazo fijo sin enganche.
 */
public enum ModalidadPromocion {
    MENSUALIDAD_FIJA,
    BONO_LOTES
}
