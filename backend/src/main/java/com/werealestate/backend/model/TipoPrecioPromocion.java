package com.werealestate.backend.model;

/**
 * A qué tarifa del proyecto aplica una {@link Promocion}: LOTE es el precio normal por m²;
 * HECTAREA es la tarifa de macrolote (solo existe en SAMAI, ver SUPERFICIE_MACROLOTE_M2 en el
 * cotizador). Nanuu nunca tiene tarifa de macrolote, así que ahí solo cabe LOTE.
 */
public enum TipoPrecioPromocion {
    LOTE,
    HECTAREA,
}
