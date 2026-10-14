package com.werealestate.backend.model;

import java.util.List;

/** Catálogo de países (nombres en español) de donde se elige el país de residencia, leído de
 * resources/paises.txt (uno por línea). La misma lista vive en el frontend (core/data/paises.ts); si se
 * edita aquí hay que editarla allá. Se compara sin importar mayúsculas ni acentos y siempre se guarda el
 * nombre tal como está en la lista. */
public final class Paises {

    public static final String MEXICO = "México";

    private static final Catalogo CATALOGO = new Catalogo("/paises.txt");

    private Paises() {}

    /** El nombre oficial de la lista que corresponde al texto, o null si no es un país de la lista. */
    public static String canonico(String texto) {
        return CATALOGO.canonico(texto);
    }

    public static List<String> todos() {
        return CATALOGO.todos();
    }
}
