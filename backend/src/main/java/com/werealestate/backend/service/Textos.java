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
}
