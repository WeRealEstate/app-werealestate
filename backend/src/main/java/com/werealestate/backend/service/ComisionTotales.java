package com.werealestate.backend.service;

import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.model.VentaComisionDevengo;
import com.werealestate.backend.model.VentaComisionDevolucion;
import com.werealestate.backend.model.VentaComisionEntrega;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Sumas por comisión: puede armarse para todas las comisiones de una sola vez (listado, resumen, sábado y
 * desglose) o acotado a una sola comisión (detalle, editar, entregar y devolver) para no consultar toda la base.
 * No consulta nada: {@link ComisionService} le entrega ya leídos los devengos, entregas, devoluciones y cobros. */
final class ComisionTotales {

    final List<VentaComision> comisiones;
    private final Map<Long, List<VentaComisionDevengo>> devengos = new HashMap<>();
    private final Map<Long, BigDecimal> devengado = new HashMap<>();
    private final Map<Long, BigDecimal> entregado = new HashMap<>();
    private final Map<Long, BigDecimal> devuelto = new HashMap<>();
    /** Comisiones con la regla nueva cuyo contrato aún no está firmado: se acumulan pero no se entregan. */
    private final Set<Long> bloqueadas = new HashSet<>();
    private final Map<Long, FinanzasService.CobroVenta> cobros;

    private ComisionTotales(List<VentaComision> comisiones, Map<Long, FinanzasService.CobroVenta> cobros) {
        this.comisiones = comisiones;
        this.cobros = cobros;
    }

    /** Todas las comisiones; los devengos llegan ya ordenados por fecha de entrega. */
    static ComisionTotales deTodas(
            List<VentaComision> comisiones,
            Map<Long, FinanzasService.CobroVenta> cobros,
            List<VentaComisionDevengo> todosLosDevengos,
            List<VentaComisionEntrega> todasLasEntregas,
            List<VentaComisionDevolucion> todasLasDevoluciones) {
        ComisionTotales t = new ComisionTotales(comisiones, cobros);
        for (VentaComisionDevengo d : todosLosDevengos) {
            t.devengos.computeIfAbsent(d.getComision().getId(), k -> new ArrayList<>()).add(d);
        }
        for (VentaComisionEntrega e : todasLasEntregas) t.entregado.merge(e.getComision().getId(), e.getMonto(), BigDecimal::add);
        for (VentaComisionDevolucion d : todasLasDevoluciones) t.devuelto.merge(d.getComision().getId(), d.getMonto(), BigDecimal::add);
        for (VentaComision c : comisiones) t.marcarSiEstaBloqueada(c);
        t.sumarDevengado();
        return t;
    }

    /** Una sola comisión; cobro es el de su venta (null si no tiene o no hay nada que cobrar). */
    static ComisionTotales deUna(
            VentaComision c,
            FinanzasService.CobroVenta cobro,
            List<VentaComisionDevengo> devengosDeComision,
            List<VentaComisionEntrega> entregasDeComision,
            List<VentaComisionDevolucion> devolucionesDeComision) {
        Map<Long, FinanzasService.CobroVenta> cobros = new HashMap<>();
        if (c.getVenta() != null && cobro != null) cobros.put(c.getVenta().getId(), cobro);
        ComisionTotales t = new ComisionTotales(List.of(c), cobros);
        t.devengos.put(c.getId(), devengosDeComision);
        for (VentaComisionEntrega e : entregasDeComision) t.entregado.merge(c.getId(), e.getMonto(), BigDecimal::add);
        for (VentaComisionDevolucion d : devolucionesDeComision) t.devuelto.merge(c.getId(), d.getMonto(), BigDecimal::add);
        t.marcarSiEstaBloqueada(c);
        t.sumarDevengado();
        return t;
    }

    private void marcarSiEstaBloqueada(VentaComision c) {
        if (c.isReglaNueva() && c.getVenta() != null && !c.getVenta().isContratoFirmado()) bloqueadas.add(c.getId());
    }

    /** Lo ganado de cada comisión se suma una sola vez (se pide varias veces por comisión al armar sus cifras). */
    private void sumarDevengado() {
        devengos.forEach((id, lista) -> devengado.put(id, sumar(lista, d -> true)));
    }

    private static BigDecimal sumar(List<VentaComisionDevengo> lista, Predicate<VentaComisionDevengo> cuales) {
        return lista.stream().filter(cuales).map(VentaComisionDevengo::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo ganado por una comisión, por porción, en el orden en que se entrega. */
    List<VentaComisionDevengo> devengos(Long id) {
        return devengos.getOrDefault(id, List.of());
    }

    BigDecimal devengado(Long id) {
        return devengado.getOrDefault(id, BigDecimal.ZERO);
    }

    boolean bloqueada(Long id) {
        return bloqueadas.contains(id);
    }

    BigDecimal devengadoHasta(Long id, LocalDate fecha) {
        if (bloqueada(id)) return BigDecimal.ZERO;
        return sumar(devengos(id), d -> !d.getFechaEntrega().isAfter(fecha));
    }

    /** Lo entregado al asesor, neto de lo que ya devolvió. */
    BigDecimal entregado(Long id) {
        return entregado.getOrDefault(id, BigDecimal.ZERO).subtract(devuelto(id));
    }

    BigDecimal devuelto(Long id) {
        return devuelto.getOrDefault(id, BigDecimal.ZERO);
    }

    /** Lo entregado de más: ya no está respaldado por abonos vigentes (p. ej. se anuló un recibo). */
    BigDecimal porDevolver(Long id) {
        return entregado(id).subtract(devengado(id)).max(BigDecimal.ZERO);
    }

    FinanzasService.CobroVenta cobroDeVenta(Long ventaId) {
        return ventaId == null
                ? FinanzasService.CobroVenta.NINGUNO
                : cobros.getOrDefault(ventaId, FinanzasService.CobroVenta.NINGUNO);
    }

    BigDecimal abonadoDeVenta(Long ventaId) {
        return cobroDeVenta(ventaId).abonado();
    }

    /** Lo ganado cuyo sábado de entrega ya pasó (anterior a hoy) y todavía no se entrega. */
    BigDecimal vencido(Long id, LocalDate hoy) {
        if (bloqueada(id)) return BigDecimal.ZERO;
        BigDecimal alCorte = sumar(devengos(id), d -> d.getFechaEntrega().isBefore(hoy));
        return alCorte.subtract(entregado(id)).max(BigDecimal.ZERO);
    }

    /** Primer sábado en que lo ganado supera lo ya entregado (los devengos van por fecha). */
    LocalDate proximaEntrega(Long id) {
        if (bloqueada(id)) return null;
        BigDecimal yaEntregado = entregado(id);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (VentaComisionDevengo d : devengos(id)) {
            acumulado = acumulado.add(d.getMonto());
            if (acumulado.compareTo(yaEntregado) > 0) return d.getFechaEntrega();
        }
        return null;
    }
}
