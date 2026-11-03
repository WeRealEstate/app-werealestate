package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
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
 * no quedó cubierto está atrasado desde el día siguiente a su fecha.
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
    public record Concepto(Integer numero, LocalDate fecha, String concepto, BigDecimal monto, boolean mensualidad) {
        Concepto conMonto(BigDecimal nuevo) {
            return new Concepto(numero, fecha, concepto, nuevo, mensualidad);
        }
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

    /** Lo que se espera cobrar de la venta, en orden de fecha, sin pasar nunca de su precio. */
    public static List<Concepto> conceptos(Venta venta, BigDecimal precio, Collection<VentaAportacion> aportacionesDeLaVenta) {
        List<Concepto> brutos = new ArrayList<>();
        boolean pagoInicial = tienePagoInicial(venta);
        if (pagoInicial) {
            brutos.add(new Concepto(null, venta.getFechaVenta(), venta.getEngancheLabel(), venta.getEnganche(), false));
        }

        boolean conPlazo = venta.getMensualidad() != null && venta.getPlazoMeses() != null;
        if (!conPlazo) {
            // Contado: todo el precio (menos el pago inicial, si lo hubo) el día de la venta.
            BigDecimal resto = precio.subtract(pagoInicial ? venta.getEnganche() : BigDecimal.ZERO);
            if (resto.signum() > 0) brutos.add(new Concepto(null, venta.getFechaVenta(), "Pago de contado", resto, false));
        } else {
            Map<YearMonth, BigDecimal> aportaciones = new TreeMap<>();
            for (VentaAportacion a : aportacionesDeLaVenta) {
                aportaciones.put(YearMonth.of(a.getAnio(), a.getMes()), a.getMonto());
            }
            YearMonth primero = YearMonth.from(venta.getFechaVenta());
            if (!venta.isPrimeraMensualidadMesVenta()) primero = primero.plusMonths(1);

            Set<YearMonth> cubiertos = new HashSet<>();
            for (int k = 0; k < venta.getPlazoMeses(); k++) {
                YearMonth ym = primero.plusMonths(k);
                cubiertos.add(ym);
                BigDecimal aportacion = aportaciones.get(ym);
                LocalDate fecha = diaDePago(ym, venta.getDiaPago());
                brutos.add(aportacion != null
                        ? new Concepto(k + 1, fecha, "Aportación", aportacion, false)
                        : new Concepto(k + 1, fecha, "Mensualidad", venta.getMensualidad(), true));
            }
            aportaciones.forEach((ym, monto) -> {
                if (!cubiertos.contains(ym)) {
                    brutos.add(new Concepto(null, diaDePago(ym, venta.getDiaPago()), "Aportación", monto, false));
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
            BigDecimal pagado = c.monto().min(porAplicar);
            porAplicar = porAplicar.subtract(pagado);
            programado = programado.add(c.monto());
            BigDecimal falta = c.monto().subtract(pagado);
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
            filas.add(new AmortizacionDto.Fila(c.numero(), c.fecha(), c.concepto(), c.monto(), pagado,
                    precio.subtract(programado).max(BigDecimal.ZERO), estado));
        }

        // Lo del precio que el plan no fecha (anualidades viejas): un renglón final que nunca vence; lo abonado de más
        // lo va cubriendo. Así la tabla siempre suma el precio.
        BigDecimal sinFecha = precio.subtract(programado);
        if (sinFecha.compareTo(TOLERANCIA) > 0) {
            BigDecimal pagado = sinFecha.min(porAplicar);
            boolean cubierto = sinFecha.subtract(pagado).compareTo(TOLERANCIA) <= 0;
            filas.add(new AmortizacionDto.Fila(null, null, SIN_FECHA, sinFecha, pagado, BigDecimal.ZERO,
                    cubierto ? EstadoPagoPlan.PAGADO : EstadoPagoPlan.PENDIENTE));
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
