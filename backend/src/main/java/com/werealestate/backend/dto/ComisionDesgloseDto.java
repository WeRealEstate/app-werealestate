package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Desglose de las comisiones no canceladas (Finanzas → Comisiones), para que las cifras cuadren: cómo se reparte el
 * total, y lo mismo por asesor y por desarrollo. sabado = el próximo sábado de entrega (hoy, si es sábado). */
public record ComisionDesgloseDto(
        LocalDate sabado,
        Cifras total,
        // Cuántas comisiones tienen algo ganado detenido porque el contrato de su venta no está firmado.
        long comisionesDetenidas,
        List<Fila> porAsesor,
        List<Fila> porDesarrollo) {

    /** Lo de un grupo de comisiones. Siempre suma así: total = faltaPorGenerar + ganado; ganado = entregado +
     * porEntregar + detenido - entregadoDeMas; porEntregar = retrasado + tocaSabado + masAdelante. */
    public record Cifras(
            long comisiones,
            BigDecimal total,
            // Lo que falta que el cliente pague para que se gane el resto de la comisión.
            BigDecimal faltaPorGenerar,
            BigDecimal ganado,
            // Neto de lo que los asesores ya devolvieron.
            BigDecimal entregado,
            // Ganado que ya se puede entregar: retrasado (su sábado ya pasó), el del próximo sábado y el de después.
            BigDecimal porEntregar,
            BigDecimal retrasado,
            BigDecimal tocaSabado,
            BigDecimal masAdelante,
            // Ganado que no se puede entregar porque el contrato de la venta no está firmado (regla nueva).
            BigDecimal detenido,
            // Entregado de más: ya no lo respaldan abonos vigentes (p. ej. se anuló un recibo); el asesor lo debe.
            BigDecimal entregadoDeMas) {
    }

    /** Un asesor ("U-5" / "E-3") o un desarrollo (su nombre, "Varios desarrollos" o "Venta eliminada"). */
    public record Fila(String clave, String nombre, boolean externo, Cifras cifras) {
    }
}
