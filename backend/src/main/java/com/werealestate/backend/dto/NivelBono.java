package com.werealestate.backend.dto;

import java.math.BigDecimal;

/** Un nivel del bono de una promoción: con exactamente {@code lotes} lotes la venta recibe {@code bono} (12.20.0). */
public record NivelBono(Integer lotes, BigDecimal bono) {

    /** Los niveles de una promoción, de menos a más lotes. */
    public static java.util.List<NivelBono> de(com.werealestate.backend.model.Promocion p) {
        return p.getBonos().entrySet().stream().map(e -> new NivelBono(e.getKey(), e.getValue())).toList();
    }
}
