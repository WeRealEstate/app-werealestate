package com.werealestate.backend.dto;

import com.werealestate.backend.model.AsesorExterno;

public record AsesorExternoDto(Long id, String nombre, String celular, String correo, boolean activo) {

    public static AsesorExternoDto from(AsesorExterno asesor) {
        return new AsesorExternoDto(
                asesor.getId(), asesor.getNombre(), asesor.getCelular(), asesor.getCorreo(), asesor.isActivo());
    }
}
