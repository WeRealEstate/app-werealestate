package com.werealestate.backend.model;

import java.util.List;

/** Los 32 estados de México, leídos de resources/estados-mx.txt. Solo valen cuando el país de residencia es
 * México (ver Paises.MEXICO); en otro país el estado es texto libre. La misma lista vive en el frontend
 * (core/data/estados-mx.ts); si se edita aquí hay que editarla allá. */
public final class EstadosMexico {

    private static final Catalogo CATALOGO = new Catalogo("/estados-mx.txt");

    private EstadosMexico() {}

    /** El nombre de la lista que corresponde al texto (sin importar mayúsculas ni acentos), o null. */
    public static String canonico(String texto) {
        return CATALOGO.canonico(texto);
    }

    public static List<String> todos() {
        return CATALOGO.todos();
    }
}
