package com.werealestate.backend.service;

import com.werealestate.backend.model.EstadoLote;
import java.util.Set;

/** Grupos de estados de lote que usan varios servicios. */
final class EstadosDeLote {

    private EstadosDeLote() {
    }

    static final Set<EstadoLote> APARTADO =
            Set.of(EstadoLote.APARTADO, EstadoLote.APARTADO_A_PLAZO, EstadoLote.APARTADO_CON_DINERO);

    /** Los únicos estados de un lote que está en una venta, y a los que solo se llega registrándola: En proceso de
     * firma mientras el contrato no esté firmado y Vendido ya firmado (ver VentaService). */
    static final Set<EstadoLote> DE_VENTA = Set.of(EstadoLote.EN_PROCESO_DE_FIRMA, EstadoLote.VENDIDO);
}
