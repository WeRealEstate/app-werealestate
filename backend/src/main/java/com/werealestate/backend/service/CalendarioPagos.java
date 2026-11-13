package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.PagoVenta;
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
import java.util.Objects;
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
 * no quedó cubierto está atrasado desde el día siguiente a su fecha. Ese reparto por monto no arrastra faltantes (una
 * mensualidad pagada solo en parte queda atrasada con su faltante en su propio renglón). El estado de cuenta que ven el
 * cliente y los demás módulos usa la regla por mes (ver {@code estadoDeCuenta} con pagos y {@link TablaMensual}), donde
 * lo que le falta a un mes vencido pasa a la mensualidad siguiente.
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
            BigDecimal enganche,
            List<Pausa> pausas) {

        public Plan(LocalDate inicio, BigDecimal mensualidad, Integer plazoMeses, int diaPago, boolean primeraEnMesInicio,
                String engancheLabel, BigDecimal enganche) {
            this(inicio, mensualidad, plazoMeses, diaPago, primeraEnMesInicio, engancheLabel, enganche, List.of());
        }

        Plan conPausas(List<Pausa> nuevas) {
            return new Plan(inicio, mensualidad, plazoMeses, diaPago, primeraEnMesInicio, engancheLabel, enganche, nuevas);
        }

        /** Fecha de la mensualidad número {@code k} (1 = la primera): la de su mes en el plan o, si una pausa la movió,
         * la que le toca desde la pausa más reciente que empieza en ella o antes (mes a mes, con el día de la pausa). */
        public LocalDate fechaDe(int k) {
            Pausa p = pausaDe(k);
            if (p == null) return diaDePago(primerMes().plusMonths(k - 1L), diaPago);
            return diaDePago(YearMonth.from(p.fecha()).plusMonths(k - (long) p.desde()), p.fecha().getDayOfMonth());
        }

        /** Día de pago de un mes del calendario: el de la pausa más reciente que ya corre en ese mes, o el del plan. */
        int diaDelMes(YearMonth ym) {
            int dia = diaPago;
            for (Pausa p : pausas) {
                if (!YearMonth.from(p.fecha()).isAfter(ym)) dia = p.fecha().getDayOfMonth();
            }
            return dia;
        }

        private Pausa pausaDe(int k) {
            Pausa elegida = null;
            for (Pausa p : pausas) {
                if (p.desde() <= k) elegida = p;
            }
            return elegida;
        }

        boolean conPlazo() {
            return mensualidad != null && plazoMeses != null;
        }

        public boolean conPagoInicialPublico() {
            return conPagoInicial();
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

    /**
     * Una pausa de mensualidades vigente (ver VentaPausa): la mensualidad {@code desde} cae en {@code fecha} y las
     * siguientes mes a mes desde ahí.
     */
    public record Pausa(int desde, LocalDate fecha) {
    }

    /** El plan vigente de la venta (el último, si se reestructuró: ese ya no lleva el pago inicial). */
    public static Plan planVigente(Venta venta) {
        // Con una reestructuración por saldo el plan vigente ya no lleva el pago inicial; con una completa sí (rehace
        // todo el plan desde la venta y conserva el enganche capturado).
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        boolean reestructurada = !rs.isEmpty() && !rs.get(rs.size() - 1).isCompleta();
        return new Plan(
                venta.getInicioPlan() != null ? venta.getInicioPlan() : venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getDiaPago(),
                venta.isPrimeraMensualidadMesVenta(),
                reestructurada ? null : venta.getEngancheLabel(),
                reestructurada ? null : venta.getEnganche(),
                pausasDelPlan(venta, idDelPlanVigente(venta)));
    }

    /** Con qué id se etiquetan las aportaciones y pausas del plan vigente: la última reestructuración (null = el
     * plan original de la venta). */
    public static Long idDelPlanVigente(Venta venta) {
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        return rs.isEmpty() ? null : rs.get(rs.size() - 1).getId();
    }

    /** Las pausas vigentes de un plan de la venta, de la más antigua a la más reciente. */
    public static List<Pausa> pausasDelPlan(Venta venta, Long planId) {
        return venta.getPausas().stream()
                .filter(p -> p.isVigente() && Objects.equals(p.getReestructuracionId(), planId))
                .map(p -> new Pausa(p.getDesdeNumero(), p.getFechaNueva()))
                .toList();
    }

    /** El plan con que se registró la venta (antes de cualquier reestructuración): la primera guarda una copia. */
    public static Plan planOriginal(Venta venta) {
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        return rs.isEmpty() ? planVigente(venta) : planAnterior(rs.get(0));
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
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        for (int i = 0; i < rs.size(); i++) {
            planes.add(planAnterior(rs.get(i)).conPausas(pausasDelPlan(venta, i == 0 ? null : rs.get(i - 1).getId())));
        }
        planes.add(planVigente(venta));
        YearMonth primero = null;
        YearMonth ultimo = null;
        for (Plan p : planes) {
            if (!p.conPlazo() || p.plazoMeses() < 1) continue;
            YearMonth a = p.primerMes();
            YearMonth b = YearMonth.from(p.fechaDe(p.plazoMeses()));
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
            List<AmortizacionDto.Fila> filas,
            List<AmortizacionDto.Fila> mensual) {

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
        VentaReestructuracion ultima = rs.get(rs.size() - 1);
        if (ultima.isCompleta()) {
            // Rehace todo el plan: un solo tramo desde el primer mes de la venta que reparte el precio completo; lo
            // abonado se aplica en orden sobre él (y los meses vencidos sin cubrir salen atrasados).
            return conceptosDePlan(
                    planVigente(venta), precio, aportacionesDelPlan(aportacionesDeLaVenta, ultima.getId()), rs.size());
        }
        return conceptosPorTramos(venta, precio, aportacionesDeLaVenta, rs);
    }

    /** Las aportaciones que pertenecen a un plan (null = el original de la venta). */
    private static List<VentaAportacion> aportacionesDelPlan(Collection<VentaAportacion> aportaciones, Long planId) {
        return aportaciones.stream().filter(a -> Objects.equals(a.getReestructuracionId(), planId)).toList();
    }

    /** Una reestructuración por saldo: de cada plan reemplazado quedan sus pagos más antiguos hasta sumar lo que se
     * abonó mientras estuvo vigente, y el último plan reparte lo que faltaba del precio en ese corte. */
    private static List<Concepto> conceptosPorTramos(
            Venta venta, BigDecimal precio, Collection<VentaAportacion> aportacionesDeLaVenta, List<VentaReestructuracion> rs) {
        List<Concepto> resultado = new ArrayList<>();
        BigDecimal abonadoAntes = BigDecimal.ZERO;
        for (int i = 0; i <= rs.size(); i++) {
            Long etiqueta = i == 0 ? null : rs.get(i - 1).getId();
            List<VentaAportacion> propias = aportacionesDelPlan(aportacionesDeLaVenta, etiqueta);
            BigDecimal base = precio.subtract(abonadoAntes).max(BigDecimal.ZERO);
            if (i == rs.size()) {
                resultado.addAll(conceptosDePlan(planVigente(venta), base, propias, i));
                break;
            }
            VentaReestructuracion r = rs.get(i);
            BigDecimal cubierto = r.getAbonadoAlCorte().subtract(abonadoAntes).max(BigDecimal.ZERO);
            for (Concepto c : conceptosDePlan(planAnterior(r).conPausas(pausasDelPlan(venta, etiqueta)), base, propias, i)) {
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
            // Cada mensualidad en su mes (recorrida si una pausa la movió: ver Plan.fechaDe); las aportaciones ya se
            // guardan en el mes recorrido, así que la del mes de la mensualidad k la reemplaza igual que sin pausa.
            Set<YearMonth> cubiertos = new HashSet<>();
            for (int k = 0; k < plan.plazoMeses(); k++) {
                LocalDate fecha = plan.fechaDe(k + 1);
                YearMonth ym = YearMonth.from(fecha);
                cubiertos.add(ym);
                BigDecimal aportacion = aportaciones.get(ym);
                brutos.add(aportacion != null
                        ? new Concepto(k + 1, fecha, "Aportación", aportacion, false, tramo, plazo)
                        : new Concepto(k + 1, fecha, "Mensualidad", plan.mensualidad(), true, tramo, plazo));
            }
            aportaciones.forEach((ym, monto) -> {
                if (!cubiertos.contains(ym)) {
                    brutos.add(new Concepto(null, diaDePago(ym, plan.diaDelMes(ym)), "Aportación", monto, false, tramo, plazo));
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

        for (Concepto c : conceptos) {
            BigDecimal monto = c.monto();
            BigDecimal pagado = monto.min(porAplicar);
            porAplicar = porAplicar.subtract(pagado);
            BigDecimal falta = monto.subtract(pagado);
            EstadoPagoPlan estado;
            if (falta.compareTo(TOLERANCIA) <= 0) {
                estado = EstadoPagoPlan.PAGADO;
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
                    precio.subtract(programado).max(BigDecimal.ZERO), estado, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), c.tramo()));
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
                filas,
                List.of());
    }

    /**
     * El estado de cuenta con la regla por mes (la que se ve en la tabla y en el PDF, decisión del usuario 2026-11-11):
     * cada abono cuenta en el mes que dice cubrir (ver TablaMensual), lo que le falta a un mes se queda en ese mes y lo
     * pagado de más se descuenta de la última mensualidad. Atraso: lo que falta de los meses ya vencidos (también de los
     * que recibieron algo); pero solo cuentan como pagos atrasados (y marcan "desde cuándo") los meses vencidos sin
     * ningún pago. Lo usan la tabla de amortización, Clientes, Finanzas y Comisiones para que todos digan lo mismo.
     */
    public static EstadoDeCuenta estadoDeCuenta(
            List<Concepto> conceptos, BigDecimal precio, List<PagoVenta> pagos, LocalDate hoy) {
        BigDecimal abonado = pagos.stream().map(PagoVenta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EstadoDeCuenta reparto = estadoDeCuenta(conceptos, precio, abonado, hoy);
        List<AmortizacionDto.Fila> mensual = TablaMensual.construir(conceptos, reparto.filas(), precio, pagos, hoy);

        BigDecimal atraso = BigDecimal.ZERO;
        int pagosAtrasados = 0;
        int mensualidadesAtrasadas = 0;
        LocalDate atrasadoDesde = null;
        LocalDate primeraParcialVencida = null;
        LocalDate proximaFecha = null;
        BigDecimal proximoMonto = BigDecimal.ZERO;
        for (AmortizacionDto.Fila f : mensual) {
            if (f.fecha() == null) continue; // "Sin fecha en el plan": nunca vence
            BigDecimal falta = f.monto().subtract(f.pagado()).max(BigDecimal.ZERO);
            // Lo que falta de un mes vencido ya va sumado en el siguiente (arrastre): el atraso es lo que falta del
            // último mes vencido (cero si ese mes ya cubrió todo lo que traía).
            if (f.fecha().isBefore(hoy)) atraso = falta;
            if (falta.compareTo(TOLERANCIA) <= 0) continue;
            if (f.fecha().isBefore(hoy)) {
                if (f.estado() == EstadoPagoPlan.ATRASADO) {
                    pagosAtrasados++;
                    if ("Mensualidad".equals(f.concepto())) mensualidadesAtrasadas++;
                    if (atrasadoDesde == null) atrasadoDesde = f.fecha();
                } else if (primeraParcialVencida == null) {
                    primeraParcialVencida = f.fecha();
                }
            } else if (proximaFecha == null || f.fecha().equals(proximaFecha)) {
                proximaFecha = f.fecha();
                proximoMonto = proximoMonto.add(falta);
            }
        }
        if (atrasadoDesde == null && atraso.compareTo(TOLERANCIA) > 0) atrasadoDesde = primeraParcialVencida;
        // Liquidada (p. ej. con pagos extemporáneos después del plan): los meses abonados se siguen viendo con su faltante,
        // pero ya no hay atraso que cobrar.
        if (precio.subtract(abonado).compareTo(TOLERANCIA) <= 0) {
            atraso = BigDecimal.ZERO;
            pagosAtrasados = 0;
            mensualidadesAtrasadas = 0;
            atrasadoDesde = null;
        } else if (atraso.compareTo(precio.subtract(abonado)) > 0) {
            atraso = precio.subtract(abonado); // nunca se debe más de lo que falta del precio
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
                reparto.filas(),
                mensual);
    }

    /** El día de pago de la venta en ese mes; en un mes más corto, el último día. */
    static LocalDate diaDePago(YearMonth ym, int diaPago) {
        return ym.atDay(Math.max(1, Math.min(diaPago, ym.lengthOfMonth())));
    }
}
