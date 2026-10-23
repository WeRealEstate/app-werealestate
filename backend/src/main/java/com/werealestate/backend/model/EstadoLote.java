package com.werealestate.backend.model;

public enum EstadoLote {
    DISPONIBLE,
    APARTADO,
    APARTADO_A_PLAZO,
    APARTADO_CON_DINERO,
    EN_PROCESO_DE_FIRMA,
    /** Lote que la empresa se guarda: solo admin y administración lo ven así; para todos los demás (y en todo lo
     * público) se muestra como VENDIDO. Ver {@link VisibilidadLote}. */
    RESERVADO_WE,
    VENDIDO
}
