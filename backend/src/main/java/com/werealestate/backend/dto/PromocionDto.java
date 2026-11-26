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
        BigDecimal bonoUnLote,
        BigDecimal bonoDosLotes,
        BigDecimal bonoTresLotes,
        Integer plazoFijo,
        String descripcion,
        boolean activa,
        LocalDateTime fechaFin,
        LocalDateTime fechaCreacion,
        // null en las de antes de la fecha de inicio (valen desde siempre).
        LocalDateTime fechaInicio,
        // true = espera su fecha de inicio para activarse sola.
        boolean programada,
        // Código de 6 dígitos para el registro de venta: solo va en lo que ve el admin (ver conCodigo); null en todo lo demás.
        String codigo
) {
    /** Sin el código: es lo que reciben el cotizador (también el público), Nueva venta y cualquiera que no sea admin. */
    public static PromocionDto fromEntity(Promocion p) {
        return armar(p, null);
    }

    /** Con el código: solo para el admin (pantalla Promociones). */
    public static PromocionDto conCodigo(Promocion p) {
        return armar(p, p.getCodigo());
    }

    private static PromocionDto armar(Promocion p, String codigo) {
        return new PromocionDto(
                p.getId(),
                p.getNombre(),
                p.getProyecto(),
                p.getTipoPrecio() != null ? p.getTipoPrecio() : TipoPrecioPromocion.LOTE,
                p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA,
                p.getMensualidadFija(),
                p.getBonoUnLote(),
                p.getBonoDosLotes(),
                p.getBonoTresLotes(),
                p.getPlazoFijo(),
                p.getDescripcion(),
                p.isActiva(),
                p.getFechaFin(),
                p.getFechaCreacion(),
                p.getFechaInicio(),
                !p.isInicioAplicado(),
                codigo
        );
    }
}
