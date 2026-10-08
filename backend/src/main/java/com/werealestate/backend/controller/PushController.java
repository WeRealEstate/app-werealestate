package com.werealestate.backend.controller;

import com.werealestate.backend.security.CurrentUserProvider;
import com.werealestate.backend.service.PushService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Suscripción de este dispositivo a las notificaciones push (cualquier usuario con sesión). */
@RestController
@RequestMapping("/api/push")
public class PushController {

    public record SuscripcionRequest(@NotBlank String endpoint, @NotBlank String p256dh, @NotBlank String auth) {
    }

    public record EndpointRequest(@NotBlank String endpoint) {
    }

    private final PushService pushService;
    private final CurrentUserProvider currentUserProvider;

    public PushController(PushService pushService, CurrentUserProvider currentUserProvider) {
        this.pushService = pushService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/clave-publica")
    public Map<String, String> clavePublica() {
        return Map.of("clave", pushService.clavePublica());
    }

    /** ¿Este dispositivo (su endpoint) ya está suscrito para el usuario actual? */
    @GetMapping("/suscrito")
    public Map<String, Boolean> suscrito(@RequestParam String endpoint) {
        return Map.of("suscrito", pushService.tieneSuscripcion(currentUserProvider.getUsuarioActual().getId(), endpoint));
    }

    @PostMapping("/suscribir")
    public ResponseEntity<Void> suscribir(@Valid @RequestBody SuscripcionRequest request, HttpServletRequest http) {
        pushService.suscribir(
                currentUserProvider.getUsuarioActual(), request.endpoint(), request.p256dh(), request.auth(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/desuscribir")
    public ResponseEntity<Void> desuscribir(@Valid @RequestBody EndpointRequest request) {
        pushService.desuscribir(currentUserProvider.getUsuarioActual(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
