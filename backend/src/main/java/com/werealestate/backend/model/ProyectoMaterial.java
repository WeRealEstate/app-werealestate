package com.werealestate.backend.model;

/** Para qué plano público es un material: SAMAI, Aldea Nanuu o ambos. */
public enum ProyectoMaterial {
    SAMAI,
    NANUU,
    AMBOS;

    /** {@code proyecto} es el de la URL del plano ("samai" o "nanuu"). */
    public boolean aplicaA(String proyecto) {
        return this == AMBOS || name().equalsIgnoreCase(proyecto);
    }
}
