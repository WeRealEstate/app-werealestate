package com.werealestate.backend.service;

import com.werealestate.backend.dto.FinanzasIngresosDto;
import com.werealestate.backend.dto.FinanzasValorDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaComisionDevolucion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaComisionDevolucionRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Números de Finanzas que salen de las ventas: cuánto hay vendido (por desarrollo y por lote) y el
 * calendario de ingresos esperados contra recibidos.
 *
 * <p>Lo esperado de cada venta sale de {@link CalendarioPagos} (la misma regla que usan el atraso del cliente y la
 * tabla de amortización): el enganche/pago inicial el día de la venta, una mensualidad por mes del plazo en su día de
 * pago, las aportaciones en su mes y, de contado, todo el precio el día de la venta.
 */
@Service
@Transactional(readOnly = true)
public class FinanzasService {

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final VentaComisionDevolucionRepository devolucionRepository;
    private final CurrentUserProvider currentUserProvider;

    public FinanzasService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            PagoVentaRepository pagoVentaRepository,
            VentaComisionDevolucionRepository devolucionRepository,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.devolucionRepository = devolucionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------------- valor vendido

    public FinanzasValorDto valorVendido() {
        exigirAdminOLider();
        List<VentaLote> lotes = ventaLoteRepository.findAllConLoteYDesarrollo();

        BigDecimal total = BigDecimal.ZERO;
        Map<Long, long[]> conteo = new LinkedHashMap<>();
        Map<Long, BigDecimal> valores = new HashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        List<FinanzasValorDto.Lote> detalle = new ArrayList<>();

        for (VentaLote vl : lotes) {
            Venta venta = vl.getVenta();
            Long desId = vl.getLote().getDesarrollo().getId();
            total = total.add(vl.getPrecio());
            conteo.computeIfAbsent(desId, k -> new long[1])[0]++;
            valores.merge(desId, vl.getPrecio(), BigDecimal::add);
            nombres.put(desId, vl.getLote().getDesarrollo().getNombre());
            detalle.add(new FinanzasValorDto.Lote(
                    vl.getLote().getDesarrollo().getNombre(),
                    vl.getLote().getManzana(),
                    vl.getLote().getNumeroLote(),
                    venta.getId(),
                    venta.getEtiqueta(),
                    venta.getCliente(),
                    vl.getPrecio()));
        }

        List<FinanzasValorDto.PorDesarrollo> porDesarrollo = conteo.keySet().stream()
                .map(id -> new FinanzasValorDto.PorDesarrollo(id, nombres.get(id), conteo.get(id)[0], valores.get(id)))
                .sorted(Comparator.comparing(FinanzasValorDto.PorDesarrollo::valor).reversed())
                .toList();
        detalle.sort(Comparator.comparing(FinanzasValorDto.Lote::desarrollo)
                .thenComparing(FinanzasValorDto.Lote::manzana)
                .thenComparing(FinanzasValorDto.Lote::numeroLote));

        BigDecimal cobrado = pagoVentaRepository.totalVigente();
        return new FinanzasValorDto(
                total, lotes.size(), cobrado, total.subtract(cobrado).max(BigDecimal.ZERO), porDesarrollo, detalle);
    }

    // ---------------------------------------------------------------- ingresos esperados

    /** Todos los meses con algo esperado o recibido (o solo los del rango "yyyy-MM" pedido; el
     * atraso acumulado siempre se calcula desde el primer mes). desarrolloId filtra por el
     * desarrollo del primer lote de cada venta. */
    public FinanzasIngresosDto ingresos(String desde, String hasta, Long desarrolloId) {
        exigirAdminOLider();
        Datos datos = cargar();
        YearMonth hoy = YearMonth.now();
        LibroMensual libro = armarLibro(datos, desarrolloId);

        TreeSet<YearMonth> meses = new TreeSet<>();
        meses.addAll(libro.esperado().keySet());
        meses.addAll(libro.recibido().keySet());
        meses.addAll(libro.devoluciones().keySet());

        List<FinanzasIngresosDto.IngresoMes> filas = new ArrayList<>();
        BigDecimal acumEsperado = BigDecimal.ZERO;
        BigDecimal acumRecibido = BigDecimal.ZERO;
        YearMonth ini = meses.isEmpty() ? hoy : meses.first();
        YearMonth fin = meses.isEmpty() ? hoy : meses.last();
        YearMonth rangoDesde = parsear(desde);
        YearMonth rangoHasta = parsear(hasta);
        FinanzasIngresosDto.IngresoMes actual = null;

        for (YearMonth ym = ini; !ym.isAfter(fin.isBefore(hoy) ? hoy : fin); ym = ym.plusMonths(1)) {
            BigDecimal esp = libro.esperado().getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal rec = libro.recibido().getOrDefault(ym, BigDecimal.ZERO);
            acumEsperado = acumEsperado.add(esp);
            acumRecibido = acumRecibido.add(rec);
            FinanzasIngresosDto.IngresoMes fila = new FinanzasIngresosDto.IngresoMes(
                    ym.toString(),
                    esp,
                    rec,
                    rec.subtract(esp),
                    acumEsperado.subtract(acumRecibido).max(BigDecimal.ZERO),
                    libro.ventasPorMes().getOrDefault(ym, Set.of()).size(),
                    ym.isAfter(hoy),
                    libro.devoluciones().getOrDefault(ym, BigDecimal.ZERO));
            if (ym.equals(hoy)) actual = fila;
            boolean dentro = (rangoDesde == null || !ym.isBefore(rangoDesde)) && (rangoHasta == null || !ym.isAfter(rangoHasta));
            if (dentro) filas.add(fila);
        }

        if (actual == null) {
            actual = new FinanzasIngresosDto.IngresoMes(
                    hoy.toString(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false, BigDecimal.ZERO);
        }
        return new FinanzasIngresosDto(actual, filas);
    }

    /** Lo esperado, lo recibido y lo devuelto en comisiones por mes, y qué ventas tuvieron movimiento en cada mes. */
    private record LibroMensual(
            TreeMap<YearMonth, BigDecimal> esperado,
            TreeMap<YearMonth, BigDecimal> recibido,
            TreeMap<YearMonth, BigDecimal> devoluciones,
            TreeMap<YearMonth, Set<Long>> ventasPorMes) {
    }

    private LibroMensual armarLibro(Datos datos, Long desarrolloId) {
        TreeMap<YearMonth, BigDecimal> esperado = new TreeMap<>();
        TreeMap<YearMonth, BigDecimal> recibido = new TreeMap<>();
        TreeMap<YearMonth, BigDecimal> devoluciones = new TreeMap<>();
        TreeMap<YearMonth, Set<Long>> ventasPorMes = new TreeMap<>();

        for (Venta venta : datos.ventas) {
            if (!perteneceADesarrollo(datos, venta, desarrolloId)) continue;
            for (CalendarioPagos.Concepto c : calendario(datos, venta)) {
                YearMonth ym = YearMonth.from(c.fecha());
                esperado.merge(ym, c.monto(), BigDecimal::add);
                ventasPorMes.computeIfAbsent(ym, k -> new HashSet<>()).add(venta.getId());
            }
            // Libro: cada abono en su fecha y, si su recibo se anuló, un contra-asiento negativo en la
            // fecha de la anulación (el mes original no cambia).
            for (Movimiento m : datos.movimientosDe(venta.getId())) {
                YearMonth ym = YearMonth.from(m.fecha());
                recibido.merge(ym, m.monto(), BigDecimal::add);
                ventasPorMes.computeIfAbsent(ym, k -> new HashSet<>()).add(venta.getId());
            }
        }
        for (Devolucion d : devolucionesDelDesarrollo(datos, desarrolloId)) {
            devoluciones.merge(YearMonth.from(d.fecha()), d.monto(), BigDecimal::add);
        }
        return new LibroMensual(esperado, recibido, devoluciones, ventasPorMes);
    }

    /** Las devoluciones de comisión del desarrollo pedido (todas si es null; una sin venta no es de ningún desarrollo). */
    private static List<Devolucion> devolucionesDelDesarrollo(Datos datos, Long desarrolloId) {
        if (desarrolloId == null) return datos.devoluciones;
        return datos.devoluciones.stream()
                .filter(d -> d.ventaId() != null && perteneceADesarrollo(datos, d.ventaId(), desarrolloId))
                .toList();
    }

    /** Qué se espera de cada venta en el mes, qué día, y los abonos que sí llegaron. */
    public FinanzasIngresosDto.Detalle ingresosDelMes(String mes, Long desarrolloId) {
        exigirAdminOLider();
        YearMonth ym = parsear(mes);
        if (ym == null) throw new ValidationException("Indica el mes como yyyy-MM");
        Datos datos = cargar();

        List<FinanzasIngresosDto.Concepto> esperados = new ArrayList<>();
        List<FinanzasIngresosDto.Abono> recibidos = new ArrayList<>();
        List<FinanzasIngresosDto.Abono> devolucionesComision = new ArrayList<>();
        for (Venta venta : datos.ventas) {
            if (!perteneceADesarrollo(datos, venta, desarrolloId)) continue;
            String desarrollo = datos.desarrolloDe(venta.getId());
            for (CalendarioPagos.Concepto c : calendario(datos, venta)) {
                if (YearMonth.from(c.fecha()).equals(ym)) {
                    esperados.add(new FinanzasIngresosDto.Concepto(
                            venta.getId(), venta.getEtiqueta(), venta.getCliente(), desarrollo, descripcion(c, venta),
                            c.fecha(), c.monto()));
                }
            }
            for (Movimiento m : datos.movimientosDe(venta.getId())) {
                if (YearMonth.from(m.fecha()).equals(ym)) {
                    recibidos.add(new FinanzasIngresosDto.Abono(
                            venta.getId(), venta.getEtiqueta(), venta.getCliente(), m.fecha(), m.monto(),
                            m.anulacion() ? "Anulación de recibo" : "Abono"));
                }
            }
        }
        for (Devolucion d : devolucionesDelDesarrollo(datos, desarrolloId)) {
            if (YearMonth.from(d.fecha()).equals(ym)) {
                devolucionesComision.add(new FinanzasIngresosDto.Abono(
                        d.ventaId(), d.ventaNumero(), d.cliente(), d.fecha(), d.monto(), "Devolución de comisión"));
            }
        }
        esperados.sort(Comparator.comparing(FinanzasIngresosDto.Concepto::fechaEsperada)
                .thenComparing(FinanzasIngresosDto.Concepto::cliente));
        recibidos.sort(Comparator.comparing(FinanzasIngresosDto.Abono::fecha).thenComparing(FinanzasIngresosDto.Abono::cliente));
        devolucionesComision.sort(Comparator.comparing(FinanzasIngresosDto.Abono::fecha));
        return new FinanzasIngresosDto.Detalle(ym.toString(), esperados, recibidos, devolucionesComision);
    }

    // ---------------------------------------------------------------- cobro del cliente

    /** Cómo va el cliente: lo que debe por pagos ya vencidos (monto, cuántos pagos son y cuántos de ellos
     * mensualidades), lo de este mes (esperado, recibido y fecha en que le toca pagar; esperadoMes es cero si este
     * mes no le toca nada) y el total abonado vigente de la venta. */
    public record CobroVenta(
            BigDecimal atrasoMonto,
            int mensualidadesAtrasadas,
            int pagosAtrasados,
            BigDecimal esperadoMes,
            BigDecimal recibidoMes,
            LocalDate fechaMes,
            BigDecimal abonado) {
        public static final CobroVenta NINGUNO =
                new CobroVenta(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO);

        public CobroVenta(
                BigDecimal atrasoMonto,
                int mensualidadesAtrasadas,
                int pagosAtrasados,
                BigDecimal esperadoMes,
                BigDecimal recibidoMes,
                LocalDate fechaMes) {
            this(atrasoMonto, mensualidadesAtrasadas, pagosAtrasados, esperadoMes, recibidoMes, fechaMes, BigDecimal.ZERO);
        }
    }

    /**
     * Cobro de cada venta (también las de contado). Atraso: lo que falta de los pagos con fecha anterior a hoy una vez
     * que lo abonado cubre los pagos del más antiguo al más nuevo (ver CalendarioPagos; mismo criterio que el atraso
     * acumulado de Ingresos y que la tabla de amortización del cliente). Uso interno (no revisa rol): lo llaman
     * servicios que ya lo hicieron.
     */
    public Map<Long, CobroVenta> cobroPorVenta() {
        Datos datos = new Datos(false);
        LocalDate hoy = LocalDate.now();
        YearMonth mesActual = YearMonth.from(hoy);
        Map<Long, CobroVenta> resultado = new HashMap<>();
        for (Venta venta : datos.ventas) {
            resultado.put(venta.getId(), calcularCobro(
                    venta, datos.precioDe(venta.getId()), datos.aportacionesDe(venta.getId()),
                    datos.pagosDe(venta.getId()), hoy, mesActual));
        }
        return resultado;
    }

    /** Cobro de una sola venta (para detalle o acciones puntuales sobre una comisión sin cargar toda la cartera). */
    public CobroVenta cobroDeVenta(Venta venta) {
        if (venta == null) return CobroVenta.NINGUNO;
        BigDecimal precio = ventaLoteRepository.findByVentaId(venta.getId()).stream()
                .map(VentaLote::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<VentaAportacion> aportaciones = ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(venta.getId());
        List<PagoVenta> pagos = pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId());
        LocalDate hoy = LocalDate.now();
        return calcularCobro(venta, precio, aportaciones, pagos, hoy, YearMonth.from(hoy));
    }

    private static CobroVenta calcularCobro(
            Venta venta,
            BigDecimal precio,
            List<VentaAportacion> aportaciones,
            List<PagoVenta> pagosVigentes,
            LocalDate hoy,
            YearMonth mesActual) {
        List<CalendarioPagos.Concepto> conceptos = CalendarioPagos.conceptos(venta, precio, aportaciones, pagosVigentes);
        BigDecimal esperadoMes = BigDecimal.ZERO;
        LocalDate fechaMes = null;
        for (CalendarioPagos.Concepto c : conceptos) {
            if (YearMonth.from(c.fecha()).equals(mesActual)) {
                esperadoMes = esperadoMes.add(c.monto());
                if (fechaMes == null || c.fecha().isBefore(fechaMes)) fechaMes = c.fecha();
            }
        }
        BigDecimal abonado = BigDecimal.ZERO;
        BigDecimal recibidoMes = BigDecimal.ZERO;
        for (PagoVenta p : pagosVigentes) {
            abonado = abonado.add(p.getMonto());
            if (YearMonth.from(p.getFecha()).equals(mesActual)) recibidoMes = recibidoMes.add(p.getMonto());
        }
        CalendarioPagos.EstadoDeCuenta cuenta = CalendarioPagos.estadoDeCuenta(conceptos, precio, pagosVigentes, hoy);
        return new CobroVenta(
                cuenta.atrasoMonto(), cuenta.mensualidadesAtrasadas(), cuenta.pagosAtrasados(),
                esperadoMes, recibidoMes, fechaMes, abonado);
    }

    // ---------------------------------------------------------------- calendario de una venta

    /** Un movimiento del libro de ingresos: monto negativo = contra-asiento de un recibo anulado. */
    private record Movimiento(LocalDate fecha, BigDecimal monto, boolean anulacion) {
    }

    /** Dinero que un asesor devolvió de una comisión ya entregada. */
    private record Devolucion(Long ventaId, String ventaNumero, String cliente, LocalDate fecha, BigDecimal monto) {
        static Devolucion de(VentaComisionDevolucion d) {
            var c = d.getComision();
            return new Devolucion(
                    c.getVenta() != null ? c.getVenta().getId() : null,
                    c.getVenta() != null ? c.getVenta().getEtiqueta() : c.getVentaEtiqueta(),
                    c.getCliente(),
                    d.getFecha(),
                    d.getMonto());
        }
    }

    /** Lo que se espera cobrar de la venta, en orden de fecha, sin pasar nunca de su precio. */
    private static List<CalendarioPagos.Concepto> calendario(Datos datos, Venta venta) {
        return CalendarioPagos.conceptos(
                venta, datos.precioDe(venta.getId()), datos.aportacionesDe(venta.getId()), datos.pagosDe(venta.getId()));
    }

    /** Cómo se lee el pago en el detalle de Ingresos: "Mensualidad 3/24", "Aportación", "Enganche"… */
    private static String descripcion(CalendarioPagos.Concepto c, Venta venta) {
        return c.mensualidad() ? "Mensualidad " + c.numero() + "/" + (c.plazo() != null ? c.plazo() : venta.getPlazoMeses()) : c.concepto();
    }

    private static YearMonth parsear(String mes) {
        if (mes == null || mes.isBlank()) return null;
        try {
            return YearMonth.parse(mes.trim());
        } catch (RuntimeException e) {
            throw new ValidationException("Mes inválido: usa el formato yyyy-MM");
        }
    }

    private static boolean perteneceADesarrollo(Datos datos, Venta venta, Long desarrolloId) {
        return perteneceADesarrollo(datos, venta.getId(), desarrolloId);
    }

    private static boolean perteneceADesarrollo(Datos datos, Long ventaId, Long desarrolloId) {
        return desarrolloId == null || desarrolloId.equals(datos.desarrolloIdDe(ventaId));
    }

    // ---------------------------------------------------------------- datos

    /** Todo lo necesario en memoria y agrupado por venta (el volumen es chico, y así no hay una
     * consulta por venta). Cuando incluirLibroIngresos es true se leen los recibos con anulados y se derivan
     * de ahí los vigentes (una sola consulta a pago_venta en vez de dos); cuando es false (cobroPorVenta)
     * se omiten anulados y devoluciones de comisión.
     *
     * <p>Las colecciones perezosas que lee CalendarioPagos (pausas y reestructuraciones de cada venta y, para el
     * atraso por mes, los meses de cada pago) se cargan aquí de una vez para toda la cartera: sin eso Hibernate las
     * pide por bloques de 100 (25 consultas solo para los meses de ~2,500 pagos). Las listas de ventas y pagos
     * conservan su consulta y su orden de siempre; las consultas extra solo dejan llenas las colecciones de esas
     * mismas entidades en la sesión. */
    private final class Datos {
        final List<Venta> ventas = ventaRepository.findAll();
        final List<VentaLote> lotes = ventaLoteRepository.findAllConLoteYDesarrollo();
        /** Solo los abonos vigentes (recibo no anulado): lo que cuenta para saldos y atrasos. */
        final List<PagoVenta> pagos;
        /** Libro de ingresos: abonos con su contra-asiento si el recibo se anuló. */
        final List<PagoVenta> pagosConAnulados;
        final List<Devolucion> devoluciones;
        final List<VentaAportacion> aportaciones = ventaAportacionRepository.findAll();
        private final Map<Long, List<VentaLote>> lotesPorVenta = new HashMap<>();
        private final Map<Long, List<PagoVenta>> pagosPorVenta = new HashMap<>();
        private final Map<Long, List<VentaAportacion>> aportacionesPorVenta = new HashMap<>();
        private final Map<Long, List<Movimiento>> movimientosPorVenta = new HashMap<>();

        Datos(boolean incluirLibroIngresos) {
            ventaRepository.findAllConPausas();
            ventaRepository.findAllConReestructuraciones();
            if (incluirLibroIngresos) {
                this.pagosConAnulados = pagoVentaRepository.findAllConRecibo();
                this.pagos = pagosConAnulados.stream().filter(p -> !p.getRecibo().isAnulado()).toList();
                this.devoluciones = devolucionRepository.findAll().stream().map(Devolucion::de).toList();
            } else {
                this.pagos = pagoVentaRepository.findAllVigentes();
                pagoVentaRepository.findAllVigentesConMeses();
                this.pagosConAnulados = List.of();
                this.devoluciones = List.of();
            }
            lotes.forEach(l -> lotesPorVenta.computeIfAbsent(l.getVenta().getId(), k -> new ArrayList<>()).add(l));
            pagos.forEach(p -> pagosPorVenta.computeIfAbsent(p.getVenta().getId(), k -> new ArrayList<>()).add(p));
            for (PagoVenta p : pagosConAnulados) {
                List<Movimiento> lista = movimientosPorVenta.computeIfAbsent(p.getVenta().getId(), k -> new ArrayList<>());
                lista.add(new Movimiento(p.getFecha(), p.getMonto(), false));
                if (p.getRecibo().isAnulado() && p.getRecibo().getAnuladoFecha() != null) {
                    lista.add(new Movimiento(p.getRecibo().getAnuladoFecha(), p.getMonto().negate(), true));
                }
            }
            aportaciones.forEach(
                    a -> aportacionesPorVenta.computeIfAbsent(a.getVenta().getId(), k -> new ArrayList<>()).add(a));
        }

        BigDecimal precioDe(Long ventaId) {
            return lotesPorVenta.getOrDefault(ventaId, List.of()).stream()
                    .map(VentaLote::getPrecio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<Movimiento> movimientosDe(Long ventaId) {
            return movimientosPorVenta.getOrDefault(ventaId, List.of());
        }

        List<PagoVenta> pagosDe(Long ventaId) {
            return pagosPorVenta.getOrDefault(ventaId, List.of());
        }

        List<VentaAportacion> aportacionesDe(Long ventaId) {
            return aportacionesPorVenta.getOrDefault(ventaId, List.of());
        }

        Long desarrolloIdDe(Long ventaId) {
            return lotesPorVenta.getOrDefault(ventaId, List.of()).stream()
                    .findFirst()
                    .map(l -> l.getLote().getDesarrollo().getId())
                    .orElse(null);
        }

        String desarrolloDe(Long ventaId) {
            return lotesPorVenta.getOrDefault(ventaId, List.of()).stream()
                    .findFirst()
                    .map(l -> l.getLote().getDesarrollo().getNombre())
                    .orElse("");
        }
    }

    private Datos cargar() {
        return new Datos(true);
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede consultar Finanzas");
        }
        return actual;
    }
}
