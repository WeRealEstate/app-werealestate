package com.werealestate.backend.dto;

import com.werealestate.backend.model.Promocion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionDto(
        Long id,
        String nombre,
        String proyecto,
        BigDecimal mensualidadFija,
        String descripcion,
        boolean activa,
        LocalDateTime fechaFin,
        LocalDateTime fechaCreacion) {

    public static PromocionDto from(Promocion promocion) {
        return new PromocionDto(
                promocion.getId(),
                promocion.getNombre(),
                promocion.getProyecto(),
                promocion.getMensualidadFija(),
                promocion.getDescripcion(),
                promocion.isActiva(),
                promocion.getFechaFin(),
                promocion.getFechaCreacion());
    }
}
