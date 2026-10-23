package com.werealestate.backend.controller;

import com.werealestate.backend.service.DriveService;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Conexión con Google Drive para los archivos de Registrar venta (solo admin; ver DriveService). */
@RestController
@RequestMapping("/api/drive")
public class DriveController {

    private final DriveService driveService;

    public DriveController(DriveService driveService) {
        this.driveService = driveService;
    }

    @GetMapping("/estado")
    public DriveService.Estado estado() {
        return driveService.estado();
    }

    /** Devuelve la dirección de Google; el panel manda ahí al navegador del admin. */
    @PostMapping("/conectar")
    public Map<String, String> conectar() {
        return Map.of("url", driveService.urlParaConectar());
    }

    /** A donde regresa Google después de elegir la cuenta (público: es una redirección del navegador, sin el token
     * de la app; lo protege el "state" de un solo uso que emitió /conectar). */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String destino = driveService.completarConexion(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).build();
    }

    /** Token, clave y App ID para abrir el selector de Google en el navegador del admin. */
    @GetMapping("/selector")
    public DriveService.DatosSelector selector() {
        return driveService.datosSelector();
    }

    public record CarpetaRequest(String id) {}

    /** La carpeta que el admin eligió con el selector: ahí se crean las carpetas de los clientes. */
    @PutMapping("/carpeta")
    public DriveService.Estado elegirCarpeta(@RequestBody CarpetaRequest request) {
        return driveService.elegirCarpeta(request.id());
    }

    @PostMapping("/desconectar")
    public void desconectar() {
        driveService.desconectar();
    }

    /** Corre una vuelta de subida en segundo plano y responde de inmediato. */
    @PostMapping("/sincronizar")
    public DriveService.Estado sincronizar() {
        return driveService.sincronizarAhora();
    }

    @PostMapping("/reintentar")
    public DriveService.Estado reintentar() {
        return driveService.reintentarFallidos();
    }
}
