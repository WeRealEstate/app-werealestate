package com.werealestate.backend.model;

public enum Role {
    ASESOR,
    LIDER_AREA,
    EQUIPO_INTERNO,
    ADMIN;

    /** Jerarquía para asignar tareas (ver TareaService/UsuarioService.asignables): un asesor y
     * alguien de equipo interno están al mismo nivel, luego líder de área, luego admin. Cada quien
     * puede asignar tareas a su propio nivel o a cualquiera por debajo. */
    public int rango() {
        return switch (this) {
            case ASESOR, EQUIPO_INTERNO -> 1;
            case LIDER_AREA -> 2;
            case ADMIN -> 3;
        };
    }
}
