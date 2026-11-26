package com.werealestate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto activa las promociones programadas cuya fecha de inicio llegó y apaga las vencidas. */
@Component
public class PromocionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromocionScheduler.class);

    private final PromocionService servicio;

    public PromocionScheduler(PromocionService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void revisar() {
        try {
            servicio.actualizarVigencias();
        } catch (Exception e) {
            log.error("No se pudieron actualizar las vigencias de las promociones", e);
        }
    }
}
