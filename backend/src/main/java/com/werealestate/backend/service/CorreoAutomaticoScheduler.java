package com.werealestate.backend.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto atiende los correos automáticos (la hora es la del centro de México: ver BackendApplication). */
@Component
public class CorreoAutomaticoScheduler {

    private static final Logger log = LoggerFactory.getLogger(CorreoAutomaticoScheduler.class);

    private final CorreoAutomaticoService servicio;

    public CorreoAutomaticoScheduler(CorreoAutomaticoService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 60_000)
    public void revisar() {
        try {
            servicio.ciclo(LocalDateTime.now());
        } catch (Exception e) {
            log.error("No se pudieron revisar los correos automáticos", e);
        }
    }
}
