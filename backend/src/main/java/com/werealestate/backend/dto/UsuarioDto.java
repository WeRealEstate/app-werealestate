package com.werealestate.backend.dto;

import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import java.util.List;

/** modulos = los que realmente puede usar hoy (ya acotados por su rol); un admin siempre tiene todos. */
public record UsuarioDto(Long id, String nombre, String email, Role rol, Long areaId, boolean activo, List<Modulo> modulos) {

    public static UsuarioDto from(Usuario usuario) {
        return new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getAreaId(),
                usuario.isActivo(),
                ModulosAcceso.ordenados(ModulosAcceso.efectivos(usuario)));
    }
}
