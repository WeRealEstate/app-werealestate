package com.werealestate.backend.dto;

/** Conteo genérico agrupado por una etiqueta (estado, desarrollo, asesor, proyecto...), usado en
 * varias secciones del dashboard de desempeño. */
public record ReporteConteoDto(String etiqueta, long total) {
}
