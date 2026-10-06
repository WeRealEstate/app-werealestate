package com.werealestate.backend.dto;

import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** modulos = los que realmente puede usar hoy (ya acotados por su rol); un admin siempre tiene todos.
 * nominaSemanal/nominaDesde solo van en las respuestas de administración de usuarios (ver
 * {@link #conNomina}); nunca en el login ni en el perfil de cada quien. */
public record UsuarioDto(
        Long id,
        String nombre,
        String email,
        Role rol,
        Long areaId,
        boolean activo,
        List<Modulo> modulos,
        BigDecimal nominaSemanal,
        LocalDate nominaDesde) {

    public static UsuarioDto from(Usuario usuario) {
        return construir(usuario, null, null);
    }

    public static UsuarioDto conNomina(Usuario usuario) {
        return construir(usuario, usuario.getNominaSemanal(), usuario.getNominaDesde());
    }

    private static UsuarioDto construir(Usuario usuario, BigDecimal nomina, LocalDate desde) {
        return new UsuarioDto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getAreaId(),
                usuario.isActivo(),
                ModulosAcceso.ordenados(ModulosAcceso.efectivos(usuario)),
                nomina,
                desde);
    }
}
