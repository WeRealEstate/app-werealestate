package com.werealestate.backend.dto;

import com.werealestate.backend.model.AsesorExterno;

public record AsesorExternoDto(Long id, String nombre, boolean activo) {

    public static AsesorExternoDto from(AsesorExterno asesor) {
        return new AsesorExternoDto(asesor.getId(), asesor.getNombre(), asesor.isActivo());
    }
}
