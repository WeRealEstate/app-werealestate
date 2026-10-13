package com.werealestate.backend.model;

/** A qué campo del cliente o de la venta alimenta una pregunta predefinida. Estas preguntas no se
 * pueden borrar; algunas son siempre obligatorias y otras (las del final de cada bloque) el admin
 * puede ocultarlas o volverlas opcionales. */
public enum DestinoPregunta {
    // Cliente
    NOMBRE(true, false),
    APELLIDO_PATERNO(true, false),
    APELLIDO_MATERNO(false, true),
    FECHA_NACIMIENTO(true, false),
    CURP(true, false),
    TELEFONO(true, false),
    CORREO(false, true),
    RFC(false, true),
    CALLE(false, true),
    COLONIA(false, true),
    MUNICIPIO(false, true),
    ESTADO(false, true),
    CODIGO_POSTAL(false, true),
    // Venta
    LOTES(true, false),
    TIPO_PAGO(true, false),
    ENGANCHE(false, false),
    PLAZO(false, false),
    DIA_PAGO(false, false),
    APORTACIONES(false, false),
    // Quien captura (el asesor que entró con su PIN)
    ASESOR(false, false);

    private final boolean siempreObligatoria;
    private final boolean opcionalEditable;

    DestinoPregunta(boolean siempreObligatoria, boolean opcionalEditable) {
        this.siempreObligatoria = siempreObligatoria;
        this.opcionalEditable = opcionalEditable;
    }

    /** Debe estar siempre visible y obligatoria. */
    public boolean siempreObligatoria() {
        return siempreObligatoria;
    }

    /** El admin puede ocultarla o hacerla opcional/obligatoria a su gusto. */
    public boolean opcionalEditable() {
        return opcionalEditable;
    }
}
