package com.werealestate.backend.service;

import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** PINs de asesor (4 letras o números, únicos entre asesores externos y usuarios): los genera al azar y valida los
 * que se escriben a mano. Lo usan el alta por el enlace de una comercializadora y la edición de sus integrantes. */
@Component
public class PinAsesor {

    /** Sin 0/O ni 1/I, que se confunden al dictarlos o anotarlos. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final AsesorExternoRepository asesorRepository;
    private final UsuarioRepository usuarioRepository;

    public PinAsesor(AsesorExternoRepository asesorRepository, UsuarioRepository usuarioRepository) {
        this.asesorRepository = asesorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Un PIN aleatorio que nadie tiene todavía. */
    public String generarLibre() {
        for (int intento = 0; intento < 500; intento++) {
            StringBuilder sb = new StringBuilder(4);
            for (int i = 0; i < 4; i++) sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            String pin = sb.toString();
            if (libre(pin, null)) return pin;
        }
        throw new IllegalStateException("No se pudo generar un PIN libre");
    }

    /** Normaliza (mayúsculas) y comprueba que sean 4 letras o números que no tenga ya otra persona. */
    public String validarEscrito(String pin, Long asesorId) {
        String limpio = pin == null ? "" : pin.trim().toUpperCase(Locale.ROOT);
        if (!limpio.matches("[A-Z0-9]{4}")) {
            throw new ValidationException("El PIN debe tener exactamente 4 letras o números");
        }
        asesorRepository.findByPin(limpio).filter(o -> !o.getId().equals(asesorId)).ifPresent(o -> {
            throw new ConflictException("Ese PIN ya lo tiene " + o.getNombre());
        });
        usuarioRepository.findByPin(limpio).ifPresent(o -> {
            throw new ConflictException("Ese PIN ya lo tiene " + o.getNombre());
        });
        return limpio;
    }

    /** Para un PIN que escribe alguien sin sesión (el formulario público): mismo formato, pero si ya está en uso no se
     * dice de quién es, solo que hay que elegir otro. */
    public String validarElegido(String pin) {
        String limpio = pin == null ? "" : pin.trim().toUpperCase(Locale.ROOT);
        if (!limpio.matches("[A-Z0-9]{4}")) {
            throw new ValidationException("El PIN debe tener exactamente 4 letras o números");
        }
        if (!libre(limpio, null)) throw new ConflictException("Ese PIN ya está en uso. Elige otro.");
        return limpio;
    }

    private boolean libre(String pin, Long asesorId) {
        return asesorRepository.findByPin(pin).filter(o -> !o.getId().equals(asesorId)).isEmpty()
                && usuarioRepository.findByPin(pin).isEmpty();
    }
}
