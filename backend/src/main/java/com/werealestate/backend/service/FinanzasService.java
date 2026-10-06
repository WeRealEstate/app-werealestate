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
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.PagoVentaRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Números de Finanzas que salen de las ventas: cuánto hay vendido (por desarrollo y por lote) y el
 * calendario de ingresos esperados contra recibidos.
 *
 * <p>Calendario esperado de una venta: el enganche/pago inicial el día de la venta; una
 * mensualidad por cada mes del plazo, en su día de pago (el último día del mes si el mes es más
 * corto), empezando el mes de la venta o el siguiente según la venta; y las aportaciones en su mes
 * (ese mes se paga la aportación en lugar de la mensualidad). Una venta de contado espera todo el
 * precio el día de la venta. Lo esperado de una venta nunca suma más que su precio. Las anualidades
 * ("Aportación anual") no se proyectan: la venta no guarda en qué mes caen ni cuántas son.
 */
@Service
@Transactional(readOnly = true)
public class FinanzasService {

    private static final Set<String> ETIQUETAS_PAGO_INICIAL = Set.of("Enganche", "Pago inicial");

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final CurrentUserProvider currentUserProvider;

    public FinanzasService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            PagoVentaRepository pagoVentaRepository,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------------- valor vendido

    public FinanzasValorDto valorVendido() {
        exigirAdminOLider();
        Datos datos = cargar();

        BigDecimal total = BigDecimal.ZERO;
        Map<Long, long[]> conteo = new LinkedHashMap<>();
        Map<Long, BigDecimal> valores = new HashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        List<FinanzasValorDto.Lote> detalle = new ArrayList<>();

        for (VentaLote vl : datos.lotes) {
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
                    venta.getNumero(),
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

        BigDecimal cobrado = datos.pagos.stream().map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanzasValorDto(
                total, datos.lotes.size(), cobrado, total.subtract(cobrado).max(BigDecimal.ZERO), porDesarrollo, detalle);
    }

    // ---------------------------------------------------------------- ingresos esperados

    /** Todos los meses con algo esperado o recibido (o solo los del rango "yyyy-MM" pedido; el
     * atraso acumulado siempre se calcula desde el primer mes). desarrolloId filtra por el
     * desarrollo del primer lote de cada venta. */
    public FinanzasIngresosDto ingresos(String desde, String hasta, Long desarrolloId) {
        exigirAdminOLider();
        Datos datos = cargar();
        YearMonth hoy = YearMonth.now();

        TreeMap<YearMonth, BigDecimal> esperado = new TreeMap<>();
        TreeMap<YearMonth, BigDecimal> recibido = new TreeMap<>();
        TreeMap<YearMonth, Set<Long>> ventasPorMes = new TreeMap<>();

        for (Venta venta : datos.ventas) {
            if (!perteneceADesarrollo(datos, venta, desarrolloId)) continue;
            for (Concepto c : calendario(datos, venta)) {
                YearMonth ym = YearMonth.from(c.fecha);
                esperado.merge(ym, c.monto, BigDecimal::add);
                ventasPorMes.computeIfAbsent(ym, k -> new java.util.HashSet<>()).add(venta.getId());
            }
            for (PagoVenta pago : datos.pagosDe(venta.getId())) {
                YearMonth ym = YearMonth.from(pago.getFecha());
                recibido.merge(ym, pago.getMonto(), BigDecimal::add);
                ventasPorMes.computeIfAbsent(ym, k -> new java.util.HashSet<>()).add(venta.getId());
            }
        }

        TreeMap<YearMonth, Boolean> meses = new TreeMap<>();
        esperado.keySet().forEach(m -> meses.put(m, true));
        recibido.keySet().forEach(m -> meses.put(m, true));

        List<FinanzasIngresosDto.IngresoMes> filas = new ArrayList<>();
        BigDecimal acumEsperado = BigDecimal.ZERO;
        BigDecimal acumRecibido = BigDecimal.ZERO;
        YearMonth ini = meses.isEmpty() ? hoy : meses.firstKey();
        YearMonth fin = meses.isEmpty() ? hoy : meses.lastKey();
        YearMonth rangoDesde = parsear(desde);
        YearMonth rangoHasta = parsear(hasta);
        FinanzasIngresosDto.IngresoMes actual = null;

        for (YearMonth ym = ini; !ym.isAfter(fin.isBefore(hoy) ? hoy : fin); ym = ym.plusMonths(1)) {
            BigDecimal esp = esperado.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal rec = recibido.getOrDefault(ym, BigDecimal.ZERO);
            acumEsperado = acumEsperado.add(esp);
            acumRecibido = acumRecibido.add(rec);
            FinanzasIngresosDto.IngresoMes fila = new FinanzasIngresosDto.IngresoMes(
                    ym.toString(),
                    esp,
                    rec,
                    rec.subtract(esp),
                    acumEsperado.subtract(acumRecibido).max(BigDecimal.ZERO),
                    ventasPorMes.getOrDefault(ym, Set.of()).size(),
                    ym.isAfter(hoy));
            if (ym.equals(hoy)) actual = fila;
            boolean dentro = (rangoDesde == null || !ym.isBefore(rangoDesde)) && (rangoHasta == null || !ym.isAfter(rangoHasta));
            if (dentro) filas.add(fila);
        }

        if (actual == null) {
            actual = new FinanzasIngresosDto.IngresoMes(
                    hoy.toString(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false);
        }
        return new FinanzasIngresosDto(actual, filas);
    }

    /** Qué se espera de cada venta en el mes, qué día, y los abonos que sí llegaron. */
    public FinanzasIngresosDto.Detalle ingresosDelMes(String mes, Long desarrolloId) {
        exigirAdminOLider();
        YearMonth ym = parsear(mes);
        if (ym == null) throw new ValidationException("Indica el mes como yyyy-MM");
        Datos datos = cargar();

        List<FinanzasIngresosDto.Concepto> esperados = new ArrayList<>();
        List<FinanzasIngresosDto.Abono> recibidos = new ArrayList<>();
        for (Venta venta : datos.ventas) {
            if (!perteneceADesarrollo(datos, venta, desarrolloId)) continue;
            String desarrollo = datos.desarrolloDe(venta.getId());
            for (Concepto c : calendario(datos, venta)) {
                if (YearMonth.from(c.fecha).equals(ym)) {
                    esperados.add(new FinanzasIngresosDto.Concepto(
                            venta.getId(), venta.getNumero(), venta.getCliente(), desarrollo, c.concepto, c.fecha, c.monto));
                }
            }
            for (PagoVenta pago : datos.pagosDe(venta.getId())) {
                if (YearMonth.from(pago.getFecha()).equals(ym)) {
                    recibidos.add(new FinanzasIngresosDto.Abono(
                            venta.getId(), venta.getNumero(), venta.getCliente(), pago.getFecha(), pago.getMonto()));
                }
            }
        }
        esperados.sort(Comparator.comparing(FinanzasIngresosDto.Concepto::fechaEsperada)
                .thenComparing(FinanzasIngresosDto.Concepto::cliente));
        recibidos.sort(Comparator.comparing(FinanzasIngresosDto.Abono::fecha).thenComparing(FinanzasIngresosDto.Abono::cliente));
        return new FinanzasIngresosDto.Detalle(ym.toString(), esperados, recibidos);
    }

    // ---------------------------------------------------------------- calendario de una venta

    private record Concepto(LocalDate fecha, String concepto, BigDecimal monto) {
    }

    /** Lo que se espera cobrar de la venta, en orden de fecha, sin pasar nunca de su precio. */
    private List<Concepto> calendario(Datos datos, Venta venta) {
        BigDecimal precio = datos.precioDe(venta.getId());
        List<Concepto> brutos = new ArrayList<>();

        boolean pagoInicial = venta.getEnganche() != null
                && venta.getEngancheLabel() != null
                && ETIQUETAS_PAGO_INICIAL.contains(venta.getEngancheLabel());
        if (pagoInicial) {
            brutos.add(new Concepto(venta.getFechaVenta(), venta.getEngancheLabel(), venta.getEnganche()));
        }

        if (venta.getMensualidad() == null || venta.getPlazoMeses() == null) {
            // Contado: todo el precio (menos el pago inicial, si lo hubo) el día de la venta.
            BigDecimal resto = precio.subtract(pagoInicial ? venta.getEnganche() : BigDecimal.ZERO);
            if (resto.signum() > 0) brutos.add(new Concepto(venta.getFechaVenta(), "Pago de contado", resto));
        } else {
            Map<YearMonth, BigDecimal> aportaciones = new TreeMap<>();
            for (VentaAportacion a : datos.aportacionesDe(venta.getId())) {
                aportaciones.put(YearMonth.of(a.getAnio(), a.getMes()), a.getMonto());
            }
            YearMonth primero = YearMonth.from(venta.getFechaVenta());
            if (!venta.isPrimeraMensualidadMesVenta()) primero = primero.plusMonths(1);

            Set<YearMonth> cubiertos = new java.util.HashSet<>();
            for (int k = 0; k < venta.getPlazoMeses(); k++) {
                YearMonth ym = primero.plusMonths(k);
                cubiertos.add(ym);
                BigDecimal aportacion = aportaciones.get(ym);
                brutos.add(aportacion != null
                        ? new Concepto(diaDePago(ym, venta.getDiaPago()), "Aportación", aportacion)
                        : new Concepto(
                                diaDePago(ym, venta.getDiaPago()),
                                "Mensualidad " + (k + 1) + "/" + venta.getPlazoMeses(),
                                venta.getMensualidad()));
            }
            aportaciones.forEach((ym, monto) -> {
                if (!cubiertos.contains(ym)) {
                    brutos.add(new Concepto(diaDePago(ym, venta.getDiaPago()), "Aportación", monto));
                }
            });
        }

        brutos.sort(Comparator.comparing(Concepto::fecha));
        List<Concepto> resultado = new ArrayList<>();
        BigDecimal restante = precio;
        for (Concepto c : brutos) {
            if (restante.signum() <= 0) break;
            BigDecimal monto = c.monto.min(restante);
            resultado.add(new Concepto(c.fecha, c.concepto, monto));
            restante = restante.subtract(monto);
        }
        return resultado;
    }

    /** El día de pago de la venta en ese mes; en un mes más corto, el último día. */
    private static LocalDate diaDePago(YearMonth ym, int diaPago) {
        return ym.atDay(Math.min(diaPago, ym.lengthOfMonth()));
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
        return desarrolloId == null || desarrolloId.equals(datos.desarrolloIdDe(venta.getId()));
    }

    // ---------------------------------------------------------------- datos

    /** Todo lo necesario en memoria y agrupado por venta (el volumen es chico, y así no hay una
     * consulta por venta). */
    private final class Datos {
        final List<Venta> ventas = ventaRepository.findAll();
        final List<VentaLote> lotes = ventaLoteRepository.findAll();
        final List<PagoVenta> pagos = pagoVentaRepository.findAll();
        final List<VentaAportacion> aportaciones = ventaAportacionRepository.findAll();
        private final Map<Long, List<VentaLote>> lotesPorVenta = new HashMap<>();
        private final Map<Long, List<PagoVenta>> pagosPorVenta = new HashMap<>();
        private final Map<Long, List<VentaAportacion>> aportacionesPorVenta = new HashMap<>();

        Datos() {
            lotes.forEach(l -> lotesPorVenta.computeIfAbsent(l.getVenta().getId(), k -> new ArrayList<>()).add(l));
            pagos.forEach(p -> pagosPorVenta.computeIfAbsent(p.getVenta().getId(), k -> new ArrayList<>()).add(p));
            aportaciones.forEach(
                    a -> aportacionesPorVenta.computeIfAbsent(a.getVenta().getId(), k -> new ArrayList<>()).add(a));
        }

        BigDecimal precioDe(Long ventaId) {
            return lotesPorVenta.getOrDefault(ventaId, List.of()).stream()
                    .map(VentaLote::getPrecio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        return new Datos();
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede consultar Finanzas");
        }
        return actual;
    }
}
