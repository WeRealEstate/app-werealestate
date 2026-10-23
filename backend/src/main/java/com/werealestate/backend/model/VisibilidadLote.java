package com.werealestate.backend.model;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Quién puede saber que un lote está {@link EstadoLote#RESERVADO_WE}: solo admin y administración (líder de área).
 * Para cualquier otro rol, y para quien no ha iniciado sesión (planos públicos, cotizador público), ese estado se
 * muestra como VENDIDO. Se decide aquí, del lado del servidor y por el rol de la petición en curso, para que ningún
 * DTO de lotes lo deje salir por descuido (ver LoteDto, MovimientoLoteDto y LoteService).
 */
public final class VisibilidadLote {

    private VisibilidadLote() {}

    /** true si la petición en curso viene de un ADMIN o un LIDER_AREA con sesión. */
    public static boolean veReservadosWe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_LIDER_AREA".equals(a.getAuthority()));
    }

    /** El estado tal como lo puede ver quien hace la petición: RESERVADO_WE se disfraza de VENDIDO. */
    public static EstadoLote visible(EstadoLote estado) {
        return estado == EstadoLote.RESERVADO_WE && !veReservadosWe() ? EstadoLote.VENDIDO : estado;
    }

    /** true si ese estado, para quien pide, es en realidad un RESERVADO_WE disfrazado (no debe verse su detalle). */
    public static boolean estaDisfrazado(EstadoLote real) {
        return real == EstadoLote.RESERVADO_WE && !veReservadosWe();
    }
}
