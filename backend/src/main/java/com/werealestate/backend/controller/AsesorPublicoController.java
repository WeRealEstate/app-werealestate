package com.werealestate.backend.controller;

import com.werealestate.backend.dto.VerificarAsesorPublicoRequest;
import com.werealestate.backend.service.AsesorPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público (sin sesión, ver SecurityConfig) del botón "Asesor" de /samai y
 * /aldea-nanuu. Limita los intentos fallidos por IP para que no se pueda probar nombres en masa. */
@RestController
@RequestMapping("/api/lotes/publico/verificar-asesor")
public class AsesorPublicoController {

    private static final int MAX_FALLOS = 5;
    private static final long VENTANA_MS = 10 * 60 * 1000L;

    private final AsesorPublicoService service;
    private final Map<String, Deque<Long>> fallosPorIp = new ConcurrentHashMap<>();

    public AsesorPublicoController(AsesorPublicoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> verificar(
            @Valid @RequestBody VerificarAsesorPublicoRequest request, HttpServletRequest http) {
        String ip = ipDe(http);
        if (fallosRecientes(ip) >= MAX_FALLOS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."));
        }
        AsesorPublicoService.Resultado resultado = service.verificar(request.nombre(), request.proyecto());
        if (resultado.motivo() == AsesorPublicoService.Motivo.OK) {
            fallosPorIp.remove(ip);
            return ResponseEntity.ok(Map.of("nombre", resultado.nombre()));
        }
        registrarFallo(ip);
        return switch (resultado.motivo()) {
            case CONTRATO_NO_VIGENTE -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Tu contrato no está vigente. Comunícate con administración."));
            case SIN_ACCESO -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "No tienes acceso a este desarrollo."));
            default -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No encontramos un asesor con ese nombre"));
        };
    }

    private static String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return http.getRemoteAddr();
    }

    private int fallosRecientes(String ip) {
        Deque<Long> fallos = fallosPorIp.get(ip);
        if (fallos == null) return 0;
        synchronized (fallos) {
            purgar(fallos);
            return fallos.size();
        }
    }

    private void registrarFallo(String ip) {
        Deque<Long> fallos = fallosPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (fallos) {
            purgar(fallos);
            fallos.addLast(System.currentTimeMillis());
        }
    }

    private static void purgar(Deque<Long> fallos) {
        long limite = System.currentTimeMillis() - VENTANA_MS;
        while (!fallos.isEmpty() && fallos.peekFirst() < limite) fallos.removeFirst();
    }
}
