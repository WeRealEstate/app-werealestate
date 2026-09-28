package com.werealestate.backend.model;

/** Clasificación de un asesor externo dentro de la estructura de equipos (Teams): INDEPENDIENTE
 * trabaja solo (default, como todo asesor externo antes de que existiera esto); LIDER encabeza un
 * equipo; LINEA reporta a alguien (ver AsesorExterno.liderDirecto/nivelLinea) — tope de 3 niveles,
 * Líder → Línea 1 → Línea 2. */
public enum TipoAsesorExterno {
    INDEPENDIENTE,
    LIDER,
    LINEA
}
