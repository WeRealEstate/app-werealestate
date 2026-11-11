package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaReestructuracion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Qué le toca pagar al cliente de una venta y cuándo (el calendario), y cómo va a una fecha (el estado de cuenta).
 * Es la única regla para esto: la usan los ingresos esperados de Finanzas, el atraso del cliente en Comisiones y la
 * tabla de amortización de Clientes y Ventas, así los números coinciden en todos lados.
 *
 * <p>Calendario: el enganche o pago inicial el día de la venta; una mensualidad por cada mes del plazo, en su día de
 * pago (el último día del mes si el mes es más corto), empezando el mes de la venta o el siguiente según la venta; y
 * las aportaciones en su mes (ese mes se paga la aportación en lugar de la mensualidad). Una venta de contado espera
 * todo el precio el día de la venta. Lo esperado nunca suma más que el precio, y si le falta $1 o menos para llegar
 * (la mensualidad se redondea a centavos) la última mensualidad absorbe la diferencia. Las anualidades de ventas
 * viejas ("Aportación anual" en el enganche) no se proyectan: la venta no guarda en qué mes caen ni cuántas son; en
 * la tabla de amortización esa parte queda en un renglón final "Sin fecha en el plan".
 *
 * <p>Estado de cuenta: lo abonado (recibos vigentes) va cubriendo los pagos del más antiguo al más nuevo. Un pago que
 * no quedó cubierto está atrasado desde el día siguiente a su fecha. Excepción (decisión del usuario): una mensualidad
 * vencida a la que sí llegó dinero pero no alcanzó se da por pagada con lo que se abonó, y lo que le faltó se suma al
 * pago siguiente (la fila baja su monto a lo pagado y la siguiente lo sube); así el total siempre cuadra con el precio
 * y el atraso lo lleva el pago siguiente. Si no hay un pago siguiente, queda atrasada como cualquier otra.
 */
public final class CalendarioPagos {

    /** Etiquetas del enganche que sí son un pago del calendario ("Aportación anual" no lo es). */
    public static final Set<String> ETIQUETAS_PAGO_INICIAL = Set.of("Enganche", "Pago inicial");
    public static final String SIN_FECHA = "Sin fecha en el plan";

    /** Hasta cuánto le puede faltar al calendario para que la última mensualidad lo absorba (redondeo). */
    private static final BigDecimal AJUSTE_MAXIMO = new BigDecimal("1.00");
    /** Diferencias de un centavo o menos no cuentan: el pago se da por cubierto. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private CalendarioPagos() {
    }

    /** Un pago esperado. numero: su lugar en el plazo (1 = primera mensualidad); null en el enganche, el pago de
     * contado y una aportación fuera del plazo. mensualidad: true en una mensualidad (no en una aportación). */
    public record Concepto(
            Integer numero, LocalDate fecha, String concepto, BigDecimal monto, boolean mensualidad, int tramo, Integer plazo) {

        /** Pago del plan original (tramo 0). */
        public Concepto(Integer numero, LocalDate fecha, String concepto, BigDecimal monto, boolean mensualidad) {
            this(numero, fecha, concepto, monto, mensualidad, 0, null);
        }

        Concepto conMonto(BigDecimal nuevo) {
            return new Concepto(numero, fecha, concepto, nuevo, mensualidad, tramo, plazo);
        }
    }

    /**
     * Un plan de pagos: desde {@code inicio} (fecha de venta o de corte), con su pago inicial (solo el plan original),
     * mensualidad, plazo y día de pago. Sin mensualidad o plazo es de contado.
     */
    public record Plan(
            LocalDate inicio,
            BigDecimal mensualidad,
            Integer plazoMeses,
            int diaPago,
            boolean primeraEnMesInicio,
            String engancheLabel,
            BigDecimal enganche) {

        boolean conPlazo() {
            return mensualidad != null && plazoMeses != null;
        }

        public boolean conPlazoPublico() {
            return conPlazo();
        }

        boolean conPagoInicial() {
            return enganche != null && engancheLabel != null && ETIQUETAS_PAGO_INICIAL.contains(engancheLabel);
        }

        public YearMonth primerMes() {
            YearMonth ym = YearMonth.from(inicio);
            return primeraEnMesInicio ? ym : ym.plusMonths(1);
        }
    }

    /** El plan vigente de la venta (el último, si se reestructuró: ese ya no lleva el pago inicial). */
    public static Plan planVigente(Venta venta) {
        boolean reestructurada = !venta.getReestructuraciones().isEmpty();
        return new Plan(
                venta.getInicioPlan() != null ? venta.getInicioPlan() : venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getDiaPago(),
                venta.isPrimeraMensualidadMesVenta(),
                reestructurada ? null : venta.getEngancheLabel(),
                reestructurada ? null : venta.getEnganche());
    }

    /** El plan que reemplazó una reestructuración. */
    public static Plan planAnterior(VentaReestructuracion r) {
        return new Plan(r.getAntFechaInicio(), r.getAntMensualidad(), r.getAntPlazoMeses(), r.getAntDiaPago(),
                r.isAntPrimeraMesInicio(), r.getAntEngancheLabel(), r.getAntEnganche());
    }

    /** Primer y último mes en que una mensualidad puede caer, sumando todos los planes de la venta; null si ningún
     * plan tiene mensualidades. Lo usa la validación de los meses de un recibo. */
    public static YearMonth[] rangoDeMeses(Venta venta) {
        List<Plan> planes = new ArrayList<>();
        venta.getReestructuraciones().forEach(r -> planes.add(planAnterior(r)));
        planes.add(planVigente(venta));
        YearMonth primero = null;
        YearMonth ultimo = null;
        for (Plan p : planes) {
            if (!p.conPlazo() || p.plazoMeses() < 1) continue;
            YearMonth a = p.primerMes();
            YearMonth b = a.plusMonths(p.plazoMeses() - 1L);
            if (primero == null || a.isBefore(primero)) primero = a;
            if (ultimo == null || b.isAfter(ultimo)) ultimo = b;
        }
        return primero == null ? null : new YearMonth[] {primero, ultimo};
    }

    /**
     * Cómo va la venta a una fecha. saldo = precio menos lo abonado. atrasoMonto: lo que falta de los pagos ya
     * vencidos; pagosAtrasados: cuántos pagos (renglones) están atrasados y cuántos de ellos son mensualidades;
     * atrasadoDesde: la fecha del pago atrasado más antiguo. proximaFecha / proximoMonto: el siguiente pago sin cubrir
     * que aún no vence (lo que falta de él), null si ya no hay.
     */
    public record EstadoDeCuenta(
            BigDecimal precio,
            BigDecimal abonado,
            BigDecimal saldo,
            BigDecimal atrasoMonto,
            int pagosAtrasados,
            int mensualidadesAtrasadas,
            LocalDate atrasadoDesde,
            LocalDate proximaFecha,
            BigDecimal proximoMonto,
            List<AmortizacionDto.Fila> filas) {

        public boolean atrasada() {
            return pagosAtrasados > 0;
        }
    }

    /** true si el enganche de la venta es un pago del calendario (Enganche o Pago inicial con monto). */
    public static boolean tienePagoInicial(Venta venta) {
        return venta.getEnganche() != null
                && venta.getEngancheLabel() != null
                && ETIQUETAS_PAGO_INICIAL.contains(venta.getEngancheLabel());
    }

    /**
     * Lo que se espera cobrar de la venta, sin pasar nunca de su precio. Sin reestructuraciones es su plan, en orden de
     * fecha. Con reestructuraciones va por tramos: de cada plan reemplazado solo quedan sus pagos más antiguos hasta
     * sumar lo que se abonó mientras estuvo vigente (lo que se pagó con él), y el plan siguiente reparte lo que faltaba
     * del precio en ese corte. Así lo pagado antes del cambio sale igual y el total sigue siendo el precio.
     */
    public static List<Concepto> conceptos(Venta venta, BigDecimal precio, Collection<VentaAportacion> aportacionesDeLaVenta) {
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        if (rs.isEmpty()) return conceptosDePlan(planVigente(venta), precio, aportacionesDeLaVenta, 0);

        List<Concepto> resultado = new ArrayList<>();
        BigDecimal abonadoAntes = BigDecimal.ZERO;
        for (int i = 0; i <= rs.size(); i++) {
            Long etiqueta = i == 0 ? null : rs.get(i - 1).getId();
            List<VentaAportacion> propias = aportacionesDeLaVenta.stream()
                    .filter(a -> java.util.Objects.equals(a.getReestructuracionId(), etiqueta))
                    .toList();
            BigDecimal base = precio.subtract(abonadoAntes).max(BigDecimal.ZERO);
            if (i == rs.size()) {
                resultado.addAll(conceptosDePlan(planVigente(venta), base, propias, i));
                break;
            }
            VentaReestructuracion r = rs.get(i);
            BigDecimal cubierto = r.getAbonadoAlCorte().subtract(abonadoAntes).max(BigDecimal.ZERO);
            for (Concepto c : conceptosDePlan(planAnterior(r), base, propias, i)) {
                if (cubierto.signum() <= 0) break;
                BigDecimal monto = c.monto().min(cubierto);
                resultado.add(c.conMonto(monto));
                cubierto = cubierto.subtract(monto);
            }
            abonadoAntes = abonadoAntes.max(r.getAbonadoAlCorte());
        }
        return resultado;
    }

    /** Los pagos de un plan, en orden de fecha, sin pasar de {@code precio} (lo que ese plan reparte). */
    static List<Concepto> conceptosDePlan(Plan plan, BigDecimal precio, Collection<VentaAportacion> aportacionesDelPlan, int tramo) {
        List<Concepto> brutos = new ArrayList<>();
        boolean pagoInicial = plan.conPagoInicial();
        Integer plazo = plan.conPlazo() ? plan.plazoMeses() : null;
        if (pagoInicial) {
            brutos.add(new Concepto(null, plan.inicio(), plan.engancheLabel(), plan.enganche(), false, tramo, plazo));
        }

        boolean conPlazo = plan.conPlazo();
        if (!conPlazo) {
            // Contado: todo el precio (menos el pago inicial, si lo hubo) el día de la venta.
            BigDecimal resto = precio.subtract(pagoInicial ? plan.enganche() : BigDecimal.ZERO);
            if (resto.signum() > 0) brutos.add(new Concepto(null, plan.inicio(), "Pago de contado", resto, false, tramo, null));
        } else {
            Map<YearMonth, BigDecimal> aportaciones = new TreeMap<>();
            for (VentaAportacion a : aportacionesDelPlan) {
                aportaciones.put(YearMonth.of(a.getAnio(), a.getMes()), a.getMonto());
            }
            YearMonth primero = plan.primerMes();

            Set<YearMonth> cubiertos = new HashSet<>();
            for (int k = 0; k < plan.plazoMeses(); k++) {
                YearMonth ym = primero.plusMonths(k);
                cubiertos.add(ym);
                BigDecimal aportacion = aportaciones.get(ym);
                LocalDate fecha = diaDePago(ym, plan.diaPago());
                brutos.add(aportacion != null
                        ? new Concepto(k + 1, fecha, "Aportación", aportacion, false, tramo, plazo)
                        : new Concepto(k + 1, fecha, "Mensualidad", plan.mensualidad(), true, tramo, plazo));
            }
            aportaciones.forEach((ym, monto) -> {
                if (!cubiertos.contains(ym)) {
                    brutos.add(new Concepto(null, diaDePago(ym, plan.diaPago()), "Aportación", monto, false, tramo, plazo));
                }
            });
        }

        // Orden estable: en la misma fecha, el enganche va antes que la primera mensualidad.
        brutos.sort(Comparator.comparing(Concepto::fecha));
        List<Concepto> resultado = new ArrayList<>();
        BigDecimal restante = precio;
        for (Concepto c : brutos) {
            if (restante.signum() <= 0) break;
            BigDecimal monto = c.monto().min(restante);
            if (monto.signum() <= 0) continue;
            resultado.add(c.conMonto(monto));
            restante = restante.subtract(monto);
        }

        // Centavos que deja el redondeo de la mensualidad: los absorbe la última mensualidad.
        if (conPlazo && restante.signum() > 0 && restante.compareTo(AJUSTE_MAXIMO) <= 0) {
            for (int i = resultado.size() - 1; i >= 0; i--) {
                if (resultado.get(i).mensualidad()) {
                    resultado.set(i, resultado.get(i).conMonto(resultado.get(i).monto().add(restante)));
                    break;
                }
            }
        }
        return resultado;
    }

    /** El estado de cuenta a la fecha {@code hoy}: lo abonado cubre los pagos del más antiguo al más nuevo. */
    public static EstadoDeCuenta estadoDeCuenta(List<Concepto> conceptos, BigDecimal precio, BigDecimal abonado, LocalDate hoy) {
        BigDecimal porAplicar = abonado.max(BigDecimal.ZERO);
        BigDecimal programado = BigDecimal.ZERO;
        BigDecimal atraso = BigDecimal.ZERO;
        int pagosAtrasados = 0;
        int mensualidadesAtrasadas = 0;
        LocalDate atrasadoDesde = null;
        LocalDate proximaFecha = null;
        BigDecimal proximoMonto = BigDecimal.ZERO;
        List<AmortizacionDto.Fila> filas = new ArrayList<>();

        BigDecimal arrastre = BigDecimal.ZERO;
        for (int i = 0; i < conceptos.size(); i++) {
            Concepto c = conceptos.get(i);
            BigDecimal arrastreRecibido = arrastre;
            BigDecimal monto = c.monto().add(arrastreRecibido);
            arrastre = BigDecimal.ZERO;
            BigDecimal pagado = monto.min(porAplicar);
            porAplicar = porAplicar.subtract(pagado);
            BigDecimal falta = monto.subtract(pagado);
            BigDecimal ajuste = arrastreRecibido;
            EstadoPagoPlan estado;
            if (falta.compareTo(TOLERANCIA) <= 0) {
                estado = EstadoPagoPlan.PAGADO;
            } else if (c.mensualidad() && c.fecha().isBefore(hoy) && pagado.compareTo(TOLERANCIA) > 0
                    && i < conceptos.size() - 1) {
                // Pago parcial de una mensualidad vencida: se da por pagada y su faltante pasa a la siguiente.
                estado = EstadoPagoPlan.PAGADO;
                arrastre = falta;
                ajuste = falta.negate();
                monto = pagado;
            } else if (c.fecha().isBefore(hoy)) {
                estado = EstadoPagoPlan.ATRASADO;
                atraso = atraso.add(falta);
                pagosAtrasados++;
                if (c.mensualidad()) mensualidadesAtrasadas++;
                if (atrasadoDesde == null) atrasadoDesde = c.fecha();
            } else if (proximaFecha == null || c.fecha().equals(proximaFecha)) {
                estado = EstadoPagoPlan.LE_TOCA;
                proximaFecha = c.fecha();
                proximoMonto = proximoMonto.add(falta);
            } else {
                estado = EstadoPagoPlan.PENDIENTE;
            }
            programado = programado.add(monto);
            filas.add(new AmortizacionDto.Fila(c.numero(), c.fecha(), c.concepto(), monto, pagado,
                    precio.subtract(programado).max(BigDecimal.ZERO), estado, ajuste, BigDecimal.ZERO, List.of(), c.tramo()));
        }

        // Lo del precio que el plan no fecha (anualidades viejas): un renglón final que nunca vence; lo abonado de más
        // lo va cubriendo. Así la tabla siempre suma el precio.
        BigDecimal sinFecha = precio.subtract(programado);
        if (sinFecha.compareTo(TOLERANCIA) > 0) {
            BigDecimal pagado = sinFecha.min(porAplicar);
            boolean cubierto = sinFecha.subtract(pagado).compareTo(TOLERANCIA) <= 0;
            filas.add(new AmortizacionDto.Fila(null, null, SIN_FECHA, sinFecha, pagado, BigDecimal.ZERO,
                    cubierto ? EstadoPagoPlan.PAGADO : EstadoPagoPlan.PENDIENTE, BigDecimal.ZERO));
        }

        return new EstadoDeCuenta(
                precio,
                abonado,
                precio.subtract(abonado).max(BigDecimal.ZERO),
                atraso,
                pagosAtrasados,
                mensualidadesAtrasadas,
                atrasadoDesde,
                proximaFecha,
                proximaFecha == null ? null : proximoMonto,
                filas);
    }

    /** El día de pago de la venta en ese mes; en un mes más corto, el último día. */
    static LocalDate diaDePago(YearMonth ym, int diaPago) {
        return ym.atDay(Math.max(1, Math.min(diaPago, ym.lengthOfMonth())));
    }
}
