package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionDto(
        Long id,
        String nombre,
        String proyecto,
        TipoPrecioPromocion tipoPrecio,
        ModalidadPromocion modalidad,
        BigDecimal mensualidadFija,
        BigDecimal bonoDosLotes,
        BigDecimal bonoTresLotes,
        Integer plazoFijo,
        String descripcion,
        boolean activa,
        LocalDateTime fechaFin,
        LocalDateTime fechaCreacion
) {
    public static PromocionDto fromEntity(Promocion p) {
        return new PromocionDto(
                p.getId(),
                p.getNombre(),
                p.getProyecto(),
                p.getTipoPrecio() != null ? p.getTipoPrecio() : TipoPrecioPromocion.LOTE,
                p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA,
                p.getMensualidadFija(),
                p.getBonoDosLotes(),
                p.getBonoTresLotes(),
                p.getPlazoFijo(),
                p.getDescripcion(),
                p.isActiva(),
                p.getFechaFin(),
                p.getFechaCreacion()
        );
    }
}
