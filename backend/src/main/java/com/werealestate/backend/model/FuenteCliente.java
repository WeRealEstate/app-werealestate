package com.werealestate.backend.model;

/** Cómo conoció el cliente a la empresa. */
public enum FuenteCliente {
    REDES_SOCIALES,
    RECOMENDACION,
    EVENTO,
    ESPECTACULAR,
    OTRO,
    /** Cliente creado desde un registro de venta de We Forms. */
    PRECONTRATO
}
