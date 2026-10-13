package com.werealestate.backend.model;

/** Tipos de pregunta de We Forms. Los últimos cuatro son selectores del sistema que solo traen las
 * preguntas predefinidas (ver DestinoPregunta): el asesor verificado, los lotes disponibles del
 * desarrollo, el tipo de pago y las aportaciones de la venta. */
public enum TipoPreguntaForm {
    CORTA,
    PARRAFO,
    OPCION_MULTIPLE,
    CASILLAS,
    DESPLEGABLE,
    FECHA,
    NUMERO,
    TELEFONO,
    CORREO,
    ARCHIVO,
    TITULO,
    ASESOR,
    LOTES,
    TIPO_PAGO,
    APORTACIONES
}
