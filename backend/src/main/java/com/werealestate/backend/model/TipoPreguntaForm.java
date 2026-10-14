package com.werealestate.backend.model;

/** Tipos de pregunta de We Forms. PAIS es una lista de países (ver Paises) que el admin puede agregar a mano;
 * la primera de cada formulario llena el país de residencia del cliente. Los últimos cuatro son selectores del sistema que solo traen las
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
    PAIS,
    ASESOR,
    LOTES,
    TIPO_PAGO,
    APORTACIONES
}
