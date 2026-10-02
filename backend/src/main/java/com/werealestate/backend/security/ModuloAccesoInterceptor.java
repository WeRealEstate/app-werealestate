package com.werealestate.backend.security;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica en el servidor los módulos que un admin le dio (o quitó) a cada usuario: aunque alguien
 * abra a mano la URL de una API de un módulo que no tiene, responde 403. Es un único punto central
 * (prefijo de la API → módulos que lo habilitan) en vez de repetir el chequeo en cada servicio.
 *
 * <p>Solo se listan las APIs que pertenecen claramente a un módulo; las compartidas (desarrollos,
 * usuarios, notificaciones, tareas, reportes) siguen protegidas solo por rol. Cuando una API sirve a
 * varios módulos (leads lo usan Leads y Pipeline; lotes lo usan Lotes, Plano, Ventas y Cotizador) basta
 * con tener cualquiera de ellos. Un admin no pasa por aquí: siempre tiene todo. */
@Component
public class ModuloAccesoInterceptor implements HandlerInterceptor {

    private static final Map<String, Set<Modulo>> MODULOS_POR_PREFIJO = new LinkedHashMap<>();

    static {
        MODULOS_POR_PREFIJO.put("/api/leads", EnumSet.of(Modulo.LEADS, Modulo.PIPELINE));
        MODULOS_POR_PREFIJO.put("/api/seguimientos", EnumSet.of(Modulo.LEADS, Modulo.PIPELINE));
        MODULOS_POR_PREFIJO.put("/api/etiquetas", EnumSet.of(Modulo.LEADS, Modulo.PIPELINE));
        MODULOS_POR_PREFIJO.put("/api/columnas", EnumSet.of(Modulo.PIPELINE));
        MODULOS_POR_PREFIJO.put("/api/cotizaciones", EnumSet.of(Modulo.COTIZADOR));
        MODULOS_POR_PREFIJO.put("/api/lotes", EnumSet.of(Modulo.LOTES, Modulo.PLANO, Modulo.VENTAS, Modulo.COTIZADOR));
        MODULOS_POR_PREFIJO.put("/api/ventas", EnumSet.of(Modulo.VENTAS));
        MODULOS_POR_PREFIJO.put("/api/gastos", EnumSet.of(Modulo.GASTOS));
        MODULOS_POR_PREFIJO.put("/api/tipos-gasto", EnumSet.of(Modulo.GASTOS));
        MODULOS_POR_PREFIJO.put("/api/eventos-calendario", EnumSet.of(Modulo.CALENDARIO));
    }

    private final CurrentUserProvider currentUserProvider;

    public ModuloAccesoInterceptor(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        Set<Modulo> requeridos = modulosQueHabilitan(request.getRequestURI());
        if (requeridos.isEmpty()) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true; // los endpoints públicos (cotizador/plano sin sesión) no tienen usuario
        }

        Usuario usuario = currentUserProvider.getUsuarioActual();
        Set<Modulo> disponibles = ModulosAcceso.efectivos(usuario);
        if (Collections.disjoint(disponibles, requeridos)) {
            throw new ForbiddenOperationException("No tienes acceso a este módulo");
        }
        return true;
    }

    private static Set<Modulo> modulosQueHabilitan(String uri) {
        for (Map.Entry<String, Set<Modulo>> e : MODULOS_POR_PREFIJO.entrySet()) {
            String prefijo = e.getKey();
            if (uri.equals(prefijo) || uri.startsWith(prefijo + "/")) return e.getValue();
        }
        return Set.of();
    }
}
