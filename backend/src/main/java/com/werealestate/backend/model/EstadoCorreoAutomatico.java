package com.werealestate.backend.model;

/** PENDIENTE espera su hora; ENVIADO salió; FALLIDO no se pudo tras varios intentos; NO_ENVIADO no salió por algo que
 * conviene ver (apagado, sin correo); OMITIDO no aplicaba (sin plantilla o ya se había mandado a mano) y no se muestra. */
public enum EstadoCorreoAutomatico {
    PENDIENTE, ENVIADO, FALLIDO, NO_ENVIADO, OMITIDO
}
