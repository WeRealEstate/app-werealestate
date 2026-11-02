package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que toca entregar al sábado indicado (o antes): por comisión, lo acumulado hasta esa fecha
 * que todavía no se entrega. */
public record ComisionesPorEntregarDto(LocalDate sabado, BigDecimal total, List<Item> items) {

    /** montoAlSabado = ganadoAlSabado - yaEntregado; abonos = de dónde sale ganadoAlSabado, en orden. */
    public record Item(
            ComisionDto comision,
            BigDecimal montoAlSabado,
            BigDecimal ganadoAlSabado,
            BigDecimal yaEntregado,
            List<Abono> abonos) {
    }

    /** Lo que aporta a la comisión un abono del cliente: su fecha, tipo (Enganche, Mensualidad…; "Venta" en una
     * comisión de una exhibición de la regla anterior), monto (null en esa), la regla con la que cuenta ("Cuenta la
     * mitad"…), cuánto aporta y el sábado en que se entrega. */
    public record Abono(
            LocalDate fecha, String concepto, BigDecimal montoAbono, String regla, BigDecimal aporte, LocalDate entrega) {
    }
}
