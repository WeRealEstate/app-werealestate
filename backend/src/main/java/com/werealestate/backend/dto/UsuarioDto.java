package com.werealestate.backend.dto;

import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;

public record UsuarioDto(Long id, String nombre, String email, Role rol, Long areaId) {

    public static UsuarioDto from(Usuario usuario) {
        return new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getAreaId());
    }
}
