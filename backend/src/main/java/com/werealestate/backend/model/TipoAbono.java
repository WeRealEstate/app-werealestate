package com.werealestate.backend.model;

/** Para qué es una línea de un recibo. Los abonos anteriores a que existieran los tipos no lo tienen
 * (null): se completan a mano. */
public enum TipoAbono {
    MENSUALIDAD,
    ENGANCHE,
    APARTADO,
    APORTACION,
    /** Un abono que se cubre con (a cuenta de) la comisión del asesor. Se comporta como una mensualidad (meses,
     * extemporáneo, comisión del 50 %) y solo se distingue en la tabla de amortización con una etiqueta. */
    A_CUENTA_COMISION;

    /** Los tipos que cubren meses del plan: MENSUALIDAD y A_CUENTA_COMISION. */
    public boolean esMensual() {
        return this == MENSUALIDAD || this == A_CUENTA_COMISION;
    }
}
