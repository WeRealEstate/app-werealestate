package com.werealestate.backend.dto;

import java.math.BigDecimal;

/** Resumen del mes (mes = "yyyy-MM"): lo recibido por abonos, lo gastado y la diferencia, más lo que
 * falta por pagar de los gastos recurrentes. */
public record GastoResumenDto(
        String mes,
        BigDecimal ingresosMes,
        // Devoluciones de comisión del mes (ingreso aparte, suma a la ganancia).
        BigDecimal devolucionesComisionMes,
        BigDecimal gastadoMes,
        BigDecimal gananciaMes,
        BigDecimal pendientePorPagar,
        BigDecimal vencidoMonto,
        int vencidosCantidad) {
}
