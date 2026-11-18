package com.werealestate.backend.service;

/** Utilidades de texto compartidas por los servicios. */
final class Textos {

    private Textos() {
    }

    /** Texto recortado, o null si viene nulo o vacío. */
    static String opcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Comodín de LIKE en minúsculas ("%texto%") para buscar sin importar mayúsculas. */
    static String comodinMinusculas(String texto) {
        return "%" + texto.trim().toLowerCase() + "%";
    }

    /** Texto para comparar nombres: sin mayúsculas, acentos ni espacios de más ("  José  PÉREZ " = "jose perez"). */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String sinAcentos = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
