package com.werealestate.backend.service;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Usuario;

/** La clave con la que se identifica a un asesor al guardar quién apartó un lote ("U-12" interno, "E-5" externo). Es el
 * mismo esquema del Score y las comisiones; un asesor externo que es la ficha de un usuario interno cuenta como ese
 * usuario. A diferencia de ScoreService.claveDe no filtra por rol: aquí solo hay que reconocer a quien apartó. */
public final class ClaveAsesor {

    private ClaveAsesor() {
    }

    /** null si no hay asesor. */
    public static String de(Usuario usuario, AsesorExterno externo) {
        if (usuario != null) return "U-" + usuario.getId();
        if (externo == null) return null;
        return externo.getUsuarioId() != null ? "U-" + externo.getUsuarioId() : "E-" + externo.getId();
    }
}
