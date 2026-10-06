package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Cuánto dinero hay vendido: suma del precio de todos los lotes vendidos, por desarrollo y por
 * lote, junto con lo ya cobrado (abonos) y lo que falta por cobrar. */
public record FinanzasValorDto(
        BigDecimal total,
        long lotes,
        BigDecimal cobrado,
        BigDecimal saldoPendiente,
        List<PorDesarrollo> porDesarrollo,
        List<Lote> detalle) {

    public record PorDesarrollo(Long desarrolloId, String desarrollo, long lotes, BigDecimal valor) {
    }

    public record Lote(
            String desarrollo,
            String manzana,
            String numeroLote,
            Long ventaId,
            Long ventaNumero,
            String cliente,
            BigDecimal precio) {
    }
}
