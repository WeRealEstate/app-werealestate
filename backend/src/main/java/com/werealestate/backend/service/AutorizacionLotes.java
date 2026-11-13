package com.werealestate.backend.service;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.security.CurrentUserProvider;
import org.springframework.stereotype.Component;

/** Qué rol puede hacer qué con los lotes; lo comparten los servicios de lotes. */
@Component
public class AutorizacionLotes {

    private final CurrentUserProvider currentUserProvider;

    public AutorizacionLotes(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public static boolean esAdminOLider(Usuario usuario) {
        return usuario.getRol() == Role.ADMIN || usuario.getRol() == Role.LIDER_AREA;
    }

    /** Quién puede saber que un lote "vendido" en realidad lo reservó la empresa: admin y administración. */
    public boolean puedeVerReservadosWe() {
        return esAdminOLider(currentUserProvider.getUsuarioActual());
    }

    public Usuario exigirAdmin(String accion) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede " + accion);
        }
        return actual;
    }
}
