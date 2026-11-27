package com.werealestate.backend.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Dato opcional del cliente al que se puede vincular una pregunta propia de We Forms (12.19.0, decisión del usuario):
 * al registrar la venta, la respuesta llena ese dato del cliente (en un cliente que ya existía, solo si lo tenía vacío).
 * Cada dato se vincula a lo más a una pregunta del formulario y solo con los tipos de pregunta que le sirven.
 */
public enum CampoCliente {
    NACIONALIDAD("Nacionalidad", 80,
            EnumSet.of(TipoPreguntaForm.CORTA, TipoPreguntaForm.DESPLEGABLE, TipoPreguntaForm.OPCION_MULTIPLE, TipoPreguntaForm.PAIS)),
    LUGAR_NACIMIENTO("Originario de", 120,
            EnumSet.of(TipoPreguntaForm.CORTA, TipoPreguntaForm.PARRAFO, TipoPreguntaForm.DESPLEGABLE,
                    TipoPreguntaForm.OPCION_MULTIPLE, TipoPreguntaForm.PAIS)),
    ESTADO_CIVIL("Estado civil", 0, EnumSet.of(TipoPreguntaForm.DESPLEGABLE, TipoPreguntaForm.OPCION_MULTIPLE)),
    OCUPACION("Ocupación", 120,
            EnumSet.of(TipoPreguntaForm.CORTA, TipoPreguntaForm.PARRAFO, TipoPreguntaForm.DESPLEGABLE, TipoPreguntaForm.OPCION_MULTIPLE)),
    BENEFICIARIO_NOMBRE("Nombre del beneficiario", 200, EnumSet.of(TipoPreguntaForm.CORTA)),
    BENEFICIARIO_PARENTESCO("Parentesco del beneficiario", 80,
            EnumSet.of(TipoPreguntaForm.CORTA, TipoPreguntaForm.DESPLEGABLE, TipoPreguntaForm.OPCION_MULTIPLE));

    /** Las opciones de una pregunta vinculada a Estado civil (fijas, en el orden de {@link EstadoCivil}). */
    public static final List<String> OPCIONES_ESTADO_CIVIL =
            List.of("Soltero(a)", "Casado(a)", "Unión libre", "Divorciado(a)", "Viudo(a)");

    private final String etiqueta;
    private final int maximo;
    private final Set<TipoPreguntaForm> tipos;

    CampoCliente(String etiqueta, int maximo, Set<TipoPreguntaForm> tipos) {
        this.etiqueta = etiqueta;
        this.maximo = maximo;
        this.tipos = tipos;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Largo máximo del dato en la ficha del cliente (0 = no es texto libre). */
    public int maximo() {
        return maximo;
    }

    public boolean aceptaTipo(TipoPreguntaForm tipo) {
        return tipos.contains(tipo);
    }

    /** La opción elegida de Estado civil como valor de {@link EstadoCivil}; null si no es una de la lista. */
    public static EstadoCivil estadoCivilDe(String opcion) {
        int i = OPCIONES_ESTADO_CIVIL.indexOf(opcion == null ? "" : opcion.trim());
        return i < 0 ? null : EstadoCivil.values()[i];
    }
}
