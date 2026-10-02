package com.werealestate.backend.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Qué módulos puede usar cada usuario:
 * <ul>
 *   <li>{@link #maximos(Role)}: hasta dónde llega un rol (lo que ya podía ver antes de que existieran
 *       los permisos por módulo). Un admin nunca puede darle a un usuario algo fuera de esto.
 *   <li>{@link #porDefecto(Role)}: con qué módulos arranca un usuario nuevo (o cuando cambia de rol).
 *       Administración arranca sin Leads; el admin se los puede activar a quien los necesite.
 *   <li>{@link #efectivos(Usuario)}: lo que de verdad puede usar ahora = lo guardado en el usuario
 *       (o el default del rol si nunca se tocó) acotado por el máximo del rol. Admin: todo.
 * </ul> */
public final class ModulosAcceso {

    private ModulosAcceso() {}

    public static Set<Modulo> maximos(Role rol) {
        return switch (rol) {
            case ADMIN -> EnumSet.allOf(Modulo.class);
            case ASESOR -> EnumSet.of(
                    Modulo.LEADS, Modulo.PIPELINE, Modulo.COTIZADOR, Modulo.LOTES, Modulo.PLANO, Modulo.CALENDARIO);
            case LIDER_AREA -> EnumSet.of(
                    Modulo.LEADS, Modulo.LOTES, Modulo.PLANO, Modulo.VENTAS, Modulo.GASTOS, Modulo.CALENDARIO);
            case EQUIPO_INTERNO -> EnumSet.of(Modulo.CALENDARIO);
        };
    }

    public static Set<Modulo> porDefecto(Role rol) {
        Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
        modulos.addAll(maximos(rol));
        if (rol == Role.LIDER_AREA) {
            modulos.remove(Modulo.LEADS);
            modulos.remove(Modulo.PLANO);
        }
        return modulos;
    }

    public static Set<Modulo> efectivos(Usuario usuario) {
        if (usuario.getRol() == Role.ADMIN) {
            return EnumSet.allOf(Modulo.class);
        }
        Set<Modulo> guardados = parsear(usuario.getModulos());
        if (guardados == null) {
            return porDefecto(usuario.getRol());
        }
        Set<Modulo> resultado = EnumSet.noneOf(Modulo.class);
        resultado.addAll(guardados);
        resultado.retainAll(maximos(usuario.getRol()));
        return resultado;
    }

    /** null = "nunca se personalizó" (usa el default del rol); vacío = sin ningún módulo. */
    public static Set<Modulo> parsear(String csv) {
        if (csv == null) return null;
        Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
        for (String parte : csv.split(",")) {
            if (parte.isBlank()) continue;
            try {
                modulos.add(Modulo.valueOf(parte.trim()));
            } catch (IllegalArgumentException ignorado) {
                // Un módulo que ya no existe se descarta en vez de romper el login.
            }
        }
        return modulos;
    }

    public static String serializar(Set<Modulo> modulos) {
        return modulos.stream().map(Modulo::name).sorted().collect(Collectors.joining(","));
    }

    public static List<Modulo> ordenados(Set<Modulo> modulos) {
        return modulos.stream().sorted().toList();
    }

    public static List<Modulo> todos() {
        return Collections.unmodifiableList(Arrays.asList(Modulo.values()));
    }
}
