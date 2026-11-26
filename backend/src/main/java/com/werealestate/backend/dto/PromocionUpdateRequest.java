package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PromocionUpdateRequest(
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @Pattern(regexp = "samai|nanuu", message = "El proyecto debe ser 'samai' o 'nanuu'")
        String proyecto,

        TipoPrecioPromocion tipoPrecio,

        ModalidadPromocion modalidad,

        BigDecimal mensualidadFija,

        /** Niveles del bono por lotes (null = los mismos). */
        List<NivelBono> bonos,

        Integer plazoFijo,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion,

        Boolean activa,

        LocalDateTime fechaFin,

        Boolean limpiarFechaFin,

        // null = sin cambios.
        LocalDateTime fechaInicio,

        // 6 letras o números nuevos; null o vacío = el mismo (para uno al azar está POST /api/promociones/{id}/codigo).
        String codigo
) {
}
