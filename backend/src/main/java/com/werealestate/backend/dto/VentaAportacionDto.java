package com.werealestate.backend.dto;

import com.werealestate.backend.model.VentaAportacion;
import java.math.BigDecimal;

public record VentaAportacionDto(Integer anio, Integer mes, BigDecimal monto) {

    public static VentaAportacionDto from(VentaAportacion aportacion) {
        return new VentaAportacionDto(aportacion.getAnio(), aportacion.getMes(), aportacion.getMonto());
    }
}
