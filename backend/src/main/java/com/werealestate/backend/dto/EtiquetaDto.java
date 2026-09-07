package com.werealestate.backend.dto;

import com.werealestate.backend.model.Etiqueta;
import com.werealestate.backend.model.EtiquetaColor;

public record EtiquetaDto(Long id, String nombre, EtiquetaColor color) {

    public static EtiquetaDto from(Etiqueta etiqueta) {
        return new EtiquetaDto(etiqueta.getId(), etiqueta.getNombre(), etiqueta.getColor());
    }
}
