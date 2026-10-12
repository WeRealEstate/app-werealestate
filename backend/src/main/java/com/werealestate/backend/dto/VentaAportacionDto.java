package com.werealestate.backend.dto;

import com.werealestate.backend.model.VentaAportacion;
import java.math.BigDecimal;

public record VentaAportacionDto(Long id, Integer anio, Integer mes, BigDecimal monto) {

    public static VentaAportacionDto from(VentaAportacion aportacion) {
        return new VentaAportacionDto(aportacion.getId(), aportacion.getAnio(), aportacion.getMes(), aportacion.getMonto());
    }
}
