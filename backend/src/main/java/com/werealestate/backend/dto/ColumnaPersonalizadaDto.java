package com.werealestate.backend.dto;

import com.werealestate.backend.model.ColumnaPersonalizada;

public record ColumnaPersonalizadaDto(Long id, String nombre, int orden) {

    public static ColumnaPersonalizadaDto from(ColumnaPersonalizada columna) {
        return new ColumnaPersonalizadaDto(columna.getId(), columna.getNombre(), columna.getOrden());
    }
}
