package com.werealestate.backend.model;

/** Estado del contrato de un asesor externo. Solo con VIGENTE puede entrar a cotizar/apartar en los
 * planos públicos (ver AsesorPublicoService). */
public enum EstadoContratoAsesor {
    VIGENTE,
    PENDIENTE_DE_FIRMAR,
    VENCIDO,
    CANCELADO
}
