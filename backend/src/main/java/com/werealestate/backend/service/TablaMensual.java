package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto.Fila;
import com.werealestate.backend.dto.AmortizacionDto.PagoMes;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.TipoAbono;
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
 * Clientes y no se toca), aquí cada abono se ubica en el mes que dice cubrir (ver {@link #ubicar}):
 * <ul>
 *   <li>Mensualidad = lo que tocaba pagar ese mes más lo que quedó pendiente de los meses anteriores. El atraso va
 *       bajando renglón por renglón hasta el último. Lo pagado de más NO se adelanta a la mensualidad siguiente: solo
 *       cuenta en el monto pagado del mes.</li>
 *   <li>Monto pagado = los abonos vigentes de ese mes, cada uno con su fecha real, sin sumarlos entre sí. Lo que pase
 *       de lo que tocaba es un excedente: abona a la deuda total y no baja la mensualidad de ningún mes.</li>
 *   <li>Estado: Pagado si ese mes cubrió lo que le tocaba (Pagado con retraso si se terminó de cubrir después de su
 *       fecha); Abonado si recibió algún pago pero no toda la
 *       mensualidad; sin ningún pago, Atrasado (si su fecha ya pasó) o, el primero que aún no vence, "Le toca"
 *       (los que siguen, Pendiente). Un abono «a cuenta de comisión» cuenta igual por su monto y solo lleva su
 *       etiqueta.</li>
 * </ul>
 * Los abonos que no dicen qué mes cubren (apartado, extemporáneo, viejos sin tipo) van por su fecha: uno anterior al
 * primer mes del plan va al primer renglón; uno de un mes sin renglón, al renglón del mes anterior más cercano (y uno
 * posterior al plan, al último). Los renglones que aún no vencen no pasan su faltante al siguiente:
 * el atraso se muestra una sola vez, en el siguiente pago. Solo informativo: no cambia el
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

        TreeMap<YearMonth, List<PagoMes>> extemporaneos = new TreeMap<>();
        List<List<PagoMes>> porFila = new ArrayList<>();
        for (int i = 0; i < conceptos.size(); i++) porFila.add(new ArrayList<>());
        if (!primeroDelMes.isEmpty()) {
            YearMonth ultimoMesDelPlan = primeroDelMes.lastKey();
            for (PagoVenta p : pagos) {
                // Pago extemporáneo hecho después del último mes del plan: va en su propio renglón, en el mes en que se
                // pagó (decisión del usuario, 2026-11-11); los meses del plan se quedan como estaban (abonados).
                if (p.isExtemporaneo() && YearMonth.from(p.getFecha()).isAfter(ultimoMesDelPlan)) {
                    extemporaneos.computeIfAbsent(YearMonth.from(p.getFecha()), k -> new ArrayList<>())
                            .add(new PagoMes(p.getFecha(), p.getMonto(), false, p.getFolio(), null));
                    continue;
                }
                List<Parte> partes = ubicar(p, conceptos, primeroDelMes);
                boolean comision = p.getTipo() == TipoAbono.A_CUENTA_COMISION;
                for (Parte parte : partes) {
                    porFila.get(parte.fila()).add(new PagoMes(p.getFecha(), parte.monto(), comision, p.getFolio(),
                            partes.size() > 1 ? p.getMonto() : null));
                }
            }
            // Cada renglón, sus abonos del más antiguo al más nuevo (los que llegaron de otro mes se intercalan por fecha).
            porFila.forEach(l -> l.sort(java.util.Comparator.comparing(PagoMes::fecha)));
        }

        List<Fila> filas = new ArrayList<>();
        BigDecimal arrastre = BigDecimal.ZERO; // lo que quedó pendiente de meses anteriores (nunca negativo)
        BigDecimal programado = BigDecimal.ZERO;
        LocalDate proxima = null;
        int ultimoTramo = conceptos.stream().mapToInt(CalendarioPagos.Concepto::tramo).max().orElse(0);

        // Lo pagado de más en un mes (excedente) no se queda como "abono a la deuda total": se descuenta de la última
        // mensualidad, y si no alcanza, de la anterior, y así (decisión del usuario, 2026-11-11). Solo baja la parte
        // aún no pagada de esos renglones; lo que ya no quepa (todo pagado) queda sin aplicar.
        BigDecimal[] descuento = new BigDecimal[conceptos.size()];
        java.util.Arrays.fill(descuento, BigDecimal.ZERO);
        BigDecimal excedente = BigDecimal.ZERO;
        for (int i = 0; i < conceptos.size(); i++) {
            if (conceptos.get(i).tramo() < ultimoTramo) continue;
            BigDecimal pagadoFila = porFila.get(i).stream().map(PagoMes::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            excedente = excedente.add(pagadoFila.subtract(conceptos.get(i).monto()).max(BigDecimal.ZERO));
        }
        for (int j = conceptos.size() - 1; j >= 0 && excedente.signum() > 0; j--) {
            if (conceptos.get(j).tramo() < ultimoTramo) break;
            BigDecimal pagadoFila = porFila.get(j).stream().map(PagoMes::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal libre = conceptos.get(j).monto().subtract(pagadoFila).max(BigDecimal.ZERO);
            BigDecimal baja = libre.min(excedente);
            descuento[j] = baja;
            excedente = excedente.subtract(baja);
        }
        for (int i = 0; i < conceptos.size(); i++) {
            CalendarioPagos.Concepto c = conceptos.get(i);
            List<PagoMes> delMes = porFila.get(i);
            BigDecimal pagado = delMes.stream().map(PagoMes::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (c.tramo() < ultimoTramo) {
                // Plan reemplazado por una reestructuración: sus pagos son justo lo que se abonó hasta el corte, así
                // que su estado es el del reparto (pagado) y nada de él pasa al plan nuevo, que ya reparte el saldo.
                programado = programado.add(c.monto());
                filas.add(new Fila(c.numero(), c.fecha(), c.concepto(), c.monto(),
                        i < filasMotor.size() ? filasMotor.get(i).pagado() : BigDecimal.ZERO,
                        precio.subtract(programado).max(BigDecimal.ZERO),
                        i < filasMotor.size() ? filasMotor.get(i).estado() : EstadoPagoPlan.PAGADO,
                        BigDecimal.ZERO, pagado, List.copyOf(delMes), c.tramo()));
                arrastre = BigDecimal.ZERO;
                continue;
            }
            // arrastre: + lo que faltó de meses anteriores ya vencidos (pasa al siguiente, decisión del usuario 2026-11-11);
            // − lo que se le descontó por pagos de más en otros meses.
            BigDecimal arrastreRecibido = arrastre.subtract(descuento[i]);
            BigDecimal toca = c.monto().add(arrastre).subtract(descuento[i]);
            BigDecimal resta = toca.subtract(pagado); // + falta, − pagó de más (no se adelanta)
            boolean vencido = c.fecha().isBefore(hoy);

            EstadoPagoPlan estado;
            if (resta.compareTo(TOLERANCIA) <= 0) {
                LocalDate cubierto = fechaEnQueSeCubrio(delMes, toca);
                estado = cubierto != null && cubierto.isAfter(c.fecha()) ? EstadoPagoPlan.PAGADO_CON_RETRASO : EstadoPagoPlan.PAGADO;
            } else if (pagado.signum() > 0) {
                estado = EstadoPagoPlan.ABONADO;
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
                    arrastreRecibido, pagado, List.copyOf(delMes), c.tramo(), BigDecimal.ZERO));
            // Lo que le falta a un mes ya vencido (con o sin pagos) pasa a la mensualidad siguiente; el mes se queda
            // Abonado (si recibió algo) o Atrasado (si no).
            arrastre = vencido ? resta.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        }

        // Un renglón por cada mes con pagos extemporáneos después del plan: «Pago extemporáneo», pagado con lo que llegó.
        int tramoFinal = conceptos.isEmpty() ? 0 : ultimoTramo;
        BigDecimal abonadoAntes = pagos.stream()
                .filter(p -> !(p.isExtemporaneo() && !extemporaneos.isEmpty()
                        && !YearMonth.from(p.getFecha()).isBefore(extemporaneos.firstKey())))
                .map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (var e : extemporaneos.entrySet()) {
            List<PagoMes> delMes = new ArrayList<>(e.getValue());
            delMes.sort(java.util.Comparator.comparing(PagoMes::fecha));
            BigDecimal monto = delMes.stream().map(PagoMes::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            abonadoAntes = abonadoAntes.add(monto);
            filas.add(new Fila(null, delMes.get(0).fecha(), "Pago extemporáneo", monto, monto,
                    precio.subtract(abonadoAntes).max(BigDecimal.ZERO), EstadoPagoPlan.PAGADO,
                    BigDecimal.ZERO, monto, List.copyOf(delMes), tramoFinal, BigDecimal.ZERO));
        }

        // Lo que el plan no fecha: un renglón final que nunca vence (en esta vista no recibe abonos).
        if (filasMotor.size() > conceptos.size()) {
            Fila sinFecha = filasMotor.get(filasMotor.size() - 1);
            filas.add(new Fila(null, null, sinFecha.concepto(), sinFecha.monto(), BigDecimal.ZERO, BigDecimal.ZERO,
                    EstadoPagoPlan.PENDIENTE, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), sinFecha.tramo()));
        }
        return filas;
    }

    /** Una porción de un abono para un renglón de la tabla. */
    private record Parte(int fila, BigDecimal monto) {
    }

    /**
     * En qué renglón(es) va un abono. Enganche: el primer renglón (el del día de la venta). Aportación: el renglón de esa
     * aportación. Mensualidad o «a cuenta de comisión» con meses: el renglón de cada mes que dice cubrir, aunque se haya
     * pagado en otro; si cubre varios, llena la mensualidad de cada uno en orden y lo que sobra queda en el último (ahí se
     * ve como excedente, que abona a la deuda total). Lo demás, o un mes que no está en el plan, va por su fecha.
     */
    private static List<Parte> ubicar(
            PagoVenta p, List<CalendarioPagos.Concepto> conceptos, TreeMap<YearMonth, Integer> primeroDelMes) {
        TipoAbono tipo = p.getTipo();
        if (tipo == TipoAbono.ENGANCHE) {
            return List.of(new Parte(0, p.getMonto()));
        }
        if (tipo == TipoAbono.APORTACION && p.getAportacion() != null) {
            int fila = filaDelMes(conceptos, YearMonth.of(p.getAportacion().getAnio(), p.getAportacion().getMes()));
            if (fila >= 0) return List.of(new Parte(fila, p.getMonto()));
        }
        // Un apartado con meses (se eligió aplicarlo a una mensualidad) se ubica igual que una mensualidad.
        if (tipo != null && (tipo.esMensual() || tipo == TipoAbono.APARTADO) && !p.isExtemporaneo()
                && p.getMeses() != null && !p.getMeses().isEmpty()) {
            java.util.LinkedHashSet<Integer> filas = new java.util.LinkedHashSet<>();
            p.getMeses().stream()
                    .map(m -> YearMonth.of(m.getAnio(), m.getMes()))
                    .sorted()
                    .forEach(ym -> {
                        int fila = filaDelMes(conceptos, ym);
                        if (fila >= 0) filas.add(fila);
                    });
            if (!filas.isEmpty()) {
                List<Parte> partes = new ArrayList<>();
                BigDecimal resto = p.getMonto();
                List<Integer> orden = new ArrayList<>(filas);
                for (int k = 0; k < orden.size(); k++) {
                    int fila = orden.get(k);
                    BigDecimal parte = k == orden.size() - 1 ? resto : resto.min(conceptos.get(fila).monto());
                    if (parte.signum() > 0) partes.add(new Parte(fila, parte));
                    resto = resto.subtract(parte);
                }
                if (!partes.isEmpty()) return partes;
            }
        }
        Map.Entry<YearMonth, Integer> destino = primeroDelMes.floorEntry(YearMonth.from(p.getFecha()));
        if (destino == null) destino = primeroDelMes.firstEntry();
        return List.of(new Parte(destino.getValue(), p.getMonto()));
    }

    /** El renglón de un mes del plan: el de la mensualidad o aportación de ese mes (no el enganche); -1 si no hay. Si el
     * mes está en dos planes (una reestructuración), el del plan más reciente. */
    private static int filaDelMes(List<CalendarioPagos.Concepto> conceptos, YearMonth ym) {
        int elegido = -1;
        int primero = -1;
        for (int i = 0; i < conceptos.size(); i++) {
            CalendarioPagos.Concepto c = conceptos.get(i);
            if (!YearMonth.from(c.fecha()).equals(ym)) continue;
            if (c.numero() != null || c.mensualidad() || "Aportación".equals(c.concepto())) {
                if (elegido < 0 || c.tramo() >= conceptos.get(elegido).tramo()) elegido = i;
            } else if (primero < 0) {
                primero = i;
            }
        }
        return elegido >= 0 ? elegido : primero;
    }

    /** La fecha del abono con el que lo pagado en el renglón alcanzó lo que le tocaba (null si no lo alcanzó). */
    private static LocalDate fechaEnQueSeCubrio(List<PagoMes> pagos, BigDecimal toca) {
        BigDecimal acumulado = BigDecimal.ZERO;
        for (PagoMes p : pagos) {
            acumulado = acumulado.add(p.monto());
            if (toca.subtract(acumulado).compareTo(TOLERANCIA) <= 0) return p.fecha();
        }
        return null;
    }
}
