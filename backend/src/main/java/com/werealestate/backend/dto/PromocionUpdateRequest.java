package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromocionUpdateRequest(
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @Pattern(regexp = "samai|nanuu", message = "El proyecto debe ser 'samai' o 'nanuu'")
        String proyecto,

        TipoPrecioPromocion tipoPrecio,

        ModalidadPromocion modalidad,

        BigDecimal mensualidadFija,

        BigDecimal bonoDosLotes,

        BigDecimal bonoTresLotes,

        Integer plazoFijo,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion,

        Boolean activa,

        LocalDateTime fechaFin,

        Boolean limpiarFechaFin
) {}
