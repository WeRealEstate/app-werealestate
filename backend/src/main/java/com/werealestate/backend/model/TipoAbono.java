package com.werealestate.backend.model;

/** Para qué es una línea de un recibo. Los abonos anteriores a que existieran los tipos no lo tienen
 * (null): se completan a mano. */
public enum TipoAbono {
    MENSUALIDAD,
    ENGANCHE,
    APARTADO,
    APORTACION
}
