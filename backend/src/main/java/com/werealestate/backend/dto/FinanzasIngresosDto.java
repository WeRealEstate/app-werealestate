package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ingresos esperados por mes (a partir del enganche, las mensualidades y las aportaciones de cada
 * venta, con su día de pago) contra lo realmente recibido (abonos registrados). */
public record FinanzasIngresosDto(IngresoMes mesActual, List<IngresoMes> meses) {

    /** mes = "yyyy-MM". atrasoAcumulado = lo esperado hasta ese mes que aún no se ha recibido
     * (nunca negativo); un cliente atrasado se recupera cuando sus abonos llegan en un mes posterior. */
    public record IngresoMes(
            String mes,
            BigDecimal esperado,
            BigDecimal recibido,
            BigDecimal diferencia,
            BigDecimal atrasoAcumulado,
            int ventas,
            boolean futuro) {
    }

    public record Concepto(
            Long ventaId,
            Long ventaNumero,
            String cliente,
            String desarrollo,
            String concepto,
            LocalDate fechaEsperada,
            BigDecimal esperado) {
    }

    public record Abono(Long ventaId, Long ventaNumero, String cliente, LocalDate fecha, BigDecimal monto) {
    }

    /** Detalle de un mes: qué se espera de quién y qué día, y los abonos que sí llegaron. */
    public record Detalle(String mes, List<Concepto> esperados, List<Abono> recibidos) {
    }
}
