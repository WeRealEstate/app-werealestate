package com.werealestate.backend.dto;

import com.werealestate.backend.model.EtiquetaColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code asesorId} es opcional: solo un admin puede usarlo para crear la etiqueta en el catálogo de otro asesor. */
public record EtiquetaCreateRequest(
        @NotBlank @Size(max = 40) String nombre, @NotNull EtiquetaColor color, Long asesorId) {
}
