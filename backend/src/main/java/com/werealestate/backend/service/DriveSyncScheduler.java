package com.werealestate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto sube a Google Drive los archivos de registros de venta que estén pendientes (ver DriveService). */
@Component
public class DriveSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DriveSyncScheduler.class);

    private final DriveService driveService;

    public DriveSyncScheduler(DriveService driveService) {
        this.driveService = driveService;
    }

    @Scheduled(initialDelay = 45_000, fixedDelay = 60_000)
    public void subirPendientes() {
        try {
            driveService.sincronizar();
        } catch (Exception e) {
            log.warn("Falló la subida a Google Drive: {}", e.toString());
        }
    }
}
