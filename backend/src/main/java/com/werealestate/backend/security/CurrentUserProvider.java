package com.werealestate.backend.security;

import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resuelve el Usuario autenticado a partir del email guardado como principal por JwtAuthFilter. */
@Component
public class CurrentUserProvider {

    private final UsuarioRepository usuarioRepository;

    public CurrentUserProvider(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario getUsuarioActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no existe: " + email));
    }
}
