package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La promoción de un código escrito en el registro de venta: lo que el formulario necesita para llenar y bloquear los
 * campos. Sin el código. precioEstandar = precio del lote de 200 m² al que está calibrada la mensualidad fija (con ella
 * la mensualidad se escala por precio de la venta ÷ precioEstandar, igual que en el servidor).
 */
public record PromocionCodigoDto(
        Long id,
        String nombre,
        String descripcion,
        ModalidadPromocion modalidad,
        TipoPrecioPromocion tipoPrecio,
        // Bono por número exacto de lotes, de menos a más.
        java.util.List<NivelBono> bonos,
        Integer plazoFijo,
        BigDecimal mensualidadFija,
        BigDecimal precioEstandar,
        LocalDateTime fechaFin) {

    public static PromocionCodigoDto de(Promocion p, BigDecimal precioEstandar) {
        return new PromocionCodigoDto(p.getId(), p.getNombre(), p.getDescripcion(), p.getModalidad(), p.getTipoPrecio(),
                NivelBono.de(p), p.getPlazoFijo(), p.getMensualidadFija(),
                precioEstandar, p.getFechaFin());
    }
}
