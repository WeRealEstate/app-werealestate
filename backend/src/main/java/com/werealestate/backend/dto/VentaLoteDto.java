package com.werealestate.backend.dto;

import com.werealestate.backend.model.VentaLote;
import java.math.BigDecimal;

public record VentaLoteDto(Long id, LoteDto lote, BigDecimal precio) {

    public static VentaLoteDto from(VentaLote ventaLote) {
        return new VentaLoteDto(ventaLote.getId(), LoteDto.from(ventaLote.getLote()), ventaLote.getPrecio());
    }
}
