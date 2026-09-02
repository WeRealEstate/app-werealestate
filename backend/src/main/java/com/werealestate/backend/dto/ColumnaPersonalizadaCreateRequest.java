package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code asesorId} es opcional: solo un admin puede usarlo para crear la columna en el tablero de otro asesor. */
public record ColumnaPersonalizadaCreateRequest(@NotBlank @Size(max = 60) String nombre, Long asesorId) {
}
