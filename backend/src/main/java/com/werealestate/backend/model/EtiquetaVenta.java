package com.werealestate.backend.model;

import com.werealestate.backend.service.ClienteNumeracionService;
import java.util.Optional;

/**
 * Cómo se muestra y se escribe el número de una venta: por desarrollo, con la letra del desarrollo ("S-12" en SAMAI,
 * "N-5" en Aldea Nanuu), igual que el número de cliente. El número global de siempre ({@link Venta#getNumeroGlobal()})
 * solo queda como respaldo interno.
 */
public final class EtiquetaVenta {

    private EtiquetaVenta() {
    }

    /** "S-12"; si el desarrollo no numera (o aún no hay número) cae al número global, sin prefijo. */
    public static String de(Desarrollo desarrollo, Long numeroDesarrollo, Long numeroGlobal) {
        String prefijo = desarrollo == null ? null : ClienteNumeracionService.prefijo(desarrollo.getNombre());
        if (prefijo == null || numeroDesarrollo == null) return String.valueOf(numeroGlobal);
        return prefijo + "-" + numeroDesarrollo;
    }

    /** El desarrollo de cada letra (el inverso de {@link ClienteNumeracionService#prefijo}). */
    public static String nombreDe(String prefijo) {
        return "S".equals(prefijo) ? "SAMAI Campestre" : "Aldea Nanuu";
    }

    /** Lo escrito en una dirección o en un buscador ("S-12", "s12", "n-5") como letra y número; vacío si no lo es. */
    public static Optional<Escrita> leer(String texto) {
        if (texto == null) return Optional.empty();
        String limpio = texto.trim().toUpperCase().replace(" ", "");
        if (limpio.length() < 2 || (limpio.charAt(0) != 'S' && limpio.charAt(0) != 'N')) return Optional.empty();
        String resto = limpio.substring(1);
        if (resto.startsWith("-")) resto = resto.substring(1);
        if (resto.isEmpty() || resto.length() > 8 || !resto.chars().allMatch(Character::isDigit)) return Optional.empty();
        return Optional.of(new Escrita(limpio.substring(0, 1), Long.parseLong(resto)));
    }

    public record Escrita(String prefijo, long numero) {
    }
}
