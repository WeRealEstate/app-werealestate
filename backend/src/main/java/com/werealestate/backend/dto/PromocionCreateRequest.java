package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionCreateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @NotBlank(message = "El proyecto es obligatorio")
        @Pattern(regexp = "samai|nanuu", message = "El proyecto debe ser 'samai' o 'nanuu'")
        String proyecto,

        TipoPrecioPromocion tipoPrecio,

        ModalidadPromocion modalidad,

        BigDecimal mensualidadFija,

        /** Opcional: vacío o 0 = la promoción no aplica a 1 lote. */
        BigDecimal bonoUnLote,

        BigDecimal bonoDosLotes,

        BigDecimal bonoTresLotes,

        Integer plazoFijo,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion,

        Boolean activa,

        LocalDateTime fechaFin,

        // null = empieza al crearla.
        LocalDateTime fechaInicio,

        // 6 dígitos escritos por el admin; vacío o null = uno al azar.
        String codigo
) {
    /** Sin código (al azar). */
    public PromocionCreateRequest(String nombre, String proyecto, TipoPrecioPromocion tipoPrecio, ModalidadPromocion modalidad,
            BigDecimal mensualidadFija, BigDecimal bonoUnLote, BigDecimal bonoDosLotes, BigDecimal bonoTresLotes,
            Integer plazoFijo, String descripcion, Boolean activa, LocalDateTime fechaFin, LocalDateTime fechaInicio) {
        this(nombre, proyecto, tipoPrecio, modalidad, mensualidadFija, bonoUnLote, bonoDosLotes, bonoTresLotes, plazoFijo,
                descripcion, activa, fechaFin, fechaInicio, null);
    }
}
