package com.werealestate.backend.security;

import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resuelve el Usuario autenticado reusando la instancia ya cargada por JwtAuthFilter en la misma petición
 * (o consultando por email como respaldo en tests que armen el SecurityContext manualmente). */
@Component
public class CurrentUserProvider {

    private final UsuarioRepository usuarioRepository;

    public CurrentUserProvider(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario getUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No hay usuario autenticado");
        }
        if (auth.getDetails() instanceof Usuario u && u.getEmail().equals(auth.getName())) {
            return u;
        }
        String email = auth.getName();
        return usuarioRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no existe: " + email));
    }
}
