package com.werealestate.backend.service;

import com.werealestate.backend.exception.ValidationException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Limita los códigos de promoción equivocados en el registro de venta (decisión del usuario: 10 por hora), para que no se
 * pueda adivinar uno probando. La clave es la IP en el enlace público y el usuario en el panel del asesor. Un código que
 * sí sirve no borra los fallos: si no, con un código conocido se podría seguir probando de 9 en 9.
 */
@Component
public class LimitadorCodigoPromocion {

    static final int MAX_FALLOS = 10;
    private static final long VENTANA_MS = 60 * 60 * 1000L;

    private final Map<String, Deque<Long>> fallos = new ConcurrentHashMap<>();

    /** Corre la búsqueda del código; si lo rechaza (ValidationException) cuenta un fallo. Al tope responde 429 sin buscar. */
    public <T> T revisar(String clave, Supplier<T> buscar) {
        if (fallosRecientes(clave) >= MAX_FALLOS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados códigos de promoción equivocados. Espera un rato e inténtalo de nuevo.");
        }
        try {
            return buscar.get();
        } catch (ValidationException e) {
            registrarFallo(clave);
            throw e;
        }
    }

    private int fallosRecientes(String clave) {
        Deque<Long> lista = fallos.get(clave);
        if (lista == null) return 0;
        synchronized (lista) {
            purgar(lista);
            return lista.size();
        }
    }

    private void registrarFallo(String clave) {
        Deque<Long> lista = fallos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (lista) {
            purgar(lista);
            lista.addLast(System.currentTimeMillis());
        }
    }

    private static void purgar(Deque<Long> lista) {
        long limite = System.currentTimeMillis() - VENTANA_MS;
        while (!lista.isEmpty() && lista.peekFirst() < limite) lista.removeFirst();
    }
}
