package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto.Fila;
import com.werealestate.backend.dto.AmortizacionDto.PagoMes;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.PagoVenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * La tabla de amortización vista mes con mes, para mostrar al cliente y en el PDF. A diferencia del reparto de
 * {@link CalendarioPagos} (todo lo abonado cubre del pago más antiguo al más nuevo, que usan Finanzas, Comisiones y
 * Clientes y no se toca), aquí cada abono se ubica en el mes en que se dio:
 * <ul>
 *   <li>Mensualidad = lo que tocaba pagar ese mes más lo que quedó pendiente de los meses anteriores (o menos lo que se
 *       pagó de más antes). El atraso va bajando renglón por renglón hasta el último.</li>
 *   <li>Monto pagado = los abonos vigentes de ese mes, cada uno con su fecha, sin sumarlos entre sí.</li>
 *   <li>Estado: Pagado si ese mes cubrió lo que le tocaba; si no, Atrasado (si su fecha ya pasó) o, el primero sin
 *       cubrir que aún no vence, "Le toca".</li>
 * </ul>
 * Un abono anterior al primer mes del plan va al primer renglón; uno de un mes sin renglón, al renglón del mes anterior
 * más cercano (y uno posterior al plan, al último). En los renglones que aún no vencen solo se arrastra lo pagado de
 * más, nunca un faltante: el atraso se muestra una sola vez, en el siguiente pago. Solo informativo: no cambia el
 * saldo, los atrasos de los demás módulos ni las comisiones.
 */
final class TablaMensual {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private TablaMensual() {
    }

    /**
     * @param conceptos lo que el plan programa (en orden), tal como lo da {@link CalendarioPagos#conceptos}
     * @param filasMotor las filas del reparto de {@link CalendarioPagos#estadoDeCuenta}: solo se usa su renglón final
     *     «Sin fecha en el plan» (lo que sobra de conceptos), si existe
     * @param pagos abonos vigentes, de la fecha más antigua a la más nueva
     */
    static List<Fila> construir(
            List<CalendarioPagos.Concepto> conceptos,
            List<Fila> filasMotor,
            BigDecimal precio,
            List<PagoVenta> pagos,
            LocalDate hoy) {
        // Primer renglón de cada mes del plan.
        TreeMap<YearMonth, Integer> primeroDelMes = new TreeMap<>();
        for (int i = 0; i < conceptos.size(); i++) primeroDelMes.putIfAbsent(YearMonth.from(conceptos.get(i).fecha()), i);

        List<List<PagoMes>> porFila = new ArrayList<>();
        for (int i = 0; i < conceptos.size(); i++) porFila.add(new ArrayList<>());
        if (!primeroDelMes.isEmpty()) {
            for (PagoVenta p : pagos) {
                Map.Entry<YearMonth, Integer> destino = primeroDelMes.floorEntry(YearMonth.from(p.getFecha()));
                if (destino == null) destino = primeroDelMes.firstEntry();
                porFila.get(destino.getValue()).add(new PagoMes(p.getFecha(), p.getMonto()));
            }
        }

        List<Fila> filas = new ArrayList<>();
        BigDecimal arrastre = BigDecimal.ZERO; // + = pendiente de antes, − = pagado de más
        BigDecimal programado = BigDecimal.ZERO;
        LocalDate proxima = null;
        for (int i = 0; i < conceptos.size(); i++) {
            CalendarioPagos.Concepto c = conceptos.get(i);
            List<PagoMes> delMes = porFila.get(i);
            BigDecimal pagado = delMes.stream().map(PagoMes::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal toca = c.monto().add(arrastre).max(BigDecimal.ZERO);
            BigDecimal resta = c.monto().add(arrastre).subtract(pagado); // + falta, − sobra
            boolean vencido = c.fecha().isBefore(hoy);

            EstadoPagoPlan estado;
            if (resta.compareTo(TOLERANCIA) <= 0) {
                estado = EstadoPagoPlan.PAGADO;
            } else if (vencido) {
                estado = EstadoPagoPlan.ATRASADO;
            } else if (proxima == null || c.fecha().equals(proxima)) {
                estado = EstadoPagoPlan.LE_TOCA;
                proxima = c.fecha();
            } else {
                estado = EstadoPagoPlan.PENDIENTE;
            }

            programado = programado.add(c.monto());
            filas.add(new Fila(
                    c.numero(), c.fecha(), c.concepto(), toca,
                    toca.subtract(resta.max(BigDecimal.ZERO)).max(BigDecimal.ZERO),
                    precio.subtract(programado).max(BigDecimal.ZERO), estado,
                    toca.subtract(c.monto()), pagado, List.copyOf(delMes)));
            // Lo que falta pasa al siguiente solo si este renglón ya venció; lo pagado de más siempre pasa.
            arrastre = vencido ? resta : resta.min(BigDecimal.ZERO);
        }

        // Lo que el plan no fecha: un renglón final que nunca vence; lo pagado de más lo va cubriendo.
        if (filasMotor.size() > conceptos.size()) {
            Fila sinFecha = filasMotor.get(filasMotor.size() - 1);
            BigDecimal sobra = arrastre.negate().max(BigDecimal.ZERO);
            BigDecimal pagado = sinFecha.monto().min(sobra);
            boolean cubierto = sinFecha.monto().subtract(pagado).compareTo(TOLERANCIA) <= 0;
            filas.add(new Fila(null, null, sinFecha.concepto(), sinFecha.monto(), pagado, BigDecimal.ZERO,
                    cubierto ? EstadoPagoPlan.PAGADO : EstadoPagoPlan.PENDIENTE, BigDecimal.ZERO,
                    BigDecimal.ZERO, List.of()));
        }
        return filas;
    }
}
