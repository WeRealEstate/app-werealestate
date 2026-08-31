package com.werealestate.backend.dto;

import com.werealestate.backend.model.Usuario;

public record UsuarioResumenDto(Long id, String nombre) {

    public static UsuarioResumenDto from(Usuario usuario) {
        return new UsuarioResumenDto(usuario.getId(), usuario.getNombre());
    }
}
