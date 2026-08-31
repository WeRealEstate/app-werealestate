package com.werealestate.backend.dto;

import com.werealestate.backend.model.Desarrollo;
import java.math.BigDecimal;

public record DesarrolloDto(Long id, String nombre, String ubicacion, BigDecimal precioM2, BigDecimal areaMinima) {

    public static DesarrolloDto from(Desarrollo desarrollo) {
        return new DesarrolloDto(
                desarrollo.getId(),
                desarrollo.getNombre(),
                desarrollo.getUbicacion(),
                desarrollo.getPrecioM2(),
                desarrollo.getAreaMinima());
    }
}
