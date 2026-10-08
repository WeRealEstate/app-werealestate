package com.werealestate.backend.service;

import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada par de minutos revisa qué avisos nuevos tiene cada usuario con el push activado y se los manda. */
@Component
public class PushScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushScheduler.class);

    private final PushService pushService;
    private final UsuarioRepository usuarioRepository;

    public PushScheduler(PushService pushService, UsuarioRepository usuarioRepository) {
        this.pushService = pushService;
        this.usuarioRepository = usuarioRepository;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 2 * 60_000)
    public void enviarPendientes() {
        for (Long id : pushService.usuariosConSuscripcion()) {
            try {
                Usuario usuario = usuarioRepository.findById(id).orElse(null);
                if (usuario != null && usuario.isActivo()) pushService.enviarPendientes(usuario);
            } catch (Exception e) {
                log.warn("Falló el envío push del usuario {}: {}", id, e.toString());
            }
        }
    }
}
