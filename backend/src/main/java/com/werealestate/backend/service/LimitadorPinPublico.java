package com.werealestate.backend.service;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Revisa el PIN de asesor en los endpoints públicos de We Forms y limita los intentos fallidos por IP
 * (mismos topes que el botón "Asesor" del plano: 5 fallos en 10 minutos), para que no se pueda adivinar
 * un PIN probando en masa. */
@Component
public class LimitadorPinPublico {

    private static final int MAX_FALLOS = 5;
    private static final long VENTANA_MS = 10 * 60 * 1000L;

    private final AsesorPublicoService asesorPublicoService;
    private final Map<String, Deque<Long>> fallosPorIp = new ConcurrentHashMap<>();

    public LimitadorPinPublico(AsesorPublicoService asesorPublicoService) {
        this.asesorPublicoService = asesorPublicoService;
    }

    /** Devuelve al asesor del PIN o lanza el error que corresponda (429 con demasiados fallos). */
    public AsesorPublicoService.Identificado exigir(HttpServletRequest http, String pin, String proyecto) {
        String ip = IpCliente.de(http);
        if (fallosRecientes(ip) >= MAX_FALLOS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.");
        }
        AsesorPublicoService.Identificado id = asesorPublicoService.identificar(pin, proyecto);
        if (id.motivo() == AsesorPublicoService.Motivo.OK) {
            fallosPorIp.remove(ip);
            return id;
        }
        registrarFallo(ip);
        switch (id.motivo()) {
            case CONTRATO_NO_VIGENTE ->
                throw new ForbiddenOperationException("Tu contrato no está vigente. Comunícate con administración.");
            case SIN_ACCESO -> throw new ForbiddenOperationException("No tienes acceso a este desarrollo.");
            default -> throw new ResourceNotFoundException("PIN incorrecto");
        }
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
