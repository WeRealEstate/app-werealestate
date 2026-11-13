package com.werealestate.backend.service;

import com.werealestate.backend.dto.ComisionDesgloseDto;
import com.werealestate.backend.dto.ComisionDetalleDto;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.ComisionDevolucionRequest;
import com.werealestate.backend.dto.ComisionEntregaRequest;
import com.werealestate.backend.dto.ComisionResumenDto;
import com.werealestate.backend.dto.ComisionTicketDto;
import com.werealestate.backend.dto.ComisionUpdateRequest;
import com.werealestate.backend.dto.ComisionesPorEntregarDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoComision;
import com.werealestate.backend.model.EstadoPagoCliente;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.ModalidadComision;
import com.werealestate.backend.model.OrigenGasto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.model.VentaComisionDevengo;
import com.werealestate.backend.model.VentaComisionDevolucion;
import com.werealestate.backend.model.VentaComisionEntrega;
import com.werealestate.backend.model.VentaComisionEntregaAbono;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.TipoGastoRepository;
import com.werealestate.backend.repository.VentaComisionDevengoRepository;
import com.werealestate.backend.repository.VentaComisionDevolucionRepository;
import com.werealestate.backend.repository.VentaComisionEntregaAbonoRepository;
import com.werealestate.backend.repository.VentaComisionEntregaRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comisiones de venta. Reglas:
 * <ul>
 *   <li>Cada venta genera una comisión del {@link #PORCENTAJE_DEFECTO}% de su valor (suma de los
 *       precios de sus lotes); un admin o Administración puede cambiar el monto o el porcentaje
 *       (el otro se recalcula).
 *   <li>Si el enganche/pago inicial de la venta cubre la comisión, se genera completa de una vez
 *       (UNA_EXHIBICION). Si no, la mitad de cada abono registrado va a la comisión hasta
 *       completarla (PARCIALIDADES): se paga solo hasta donde el cliente ha pagado.
 *   <li>Lo ganado se entrega el sábado siguiente al día del abono (siempre posterior, nunca el
 *       mismo día). Entregar es manual y genera un gasto "Comisiones" ligado a la entrega.
 *   <li>Cada entrega tiene su ticket de pago (PDF que arma el frontend con {@link #ticket}): al entregar se
 *       marca con o sin IVA, con o sin factura y la forma de pago, y se guarda qué abonos cubrió.
 * </ul>
 * Lo ganado ({@link VentaComisionDevengo}) se reconstruye por completo a partir de los abonos cada
 * vez que algo cambia, así que siempre es consistente con ellos.
 */
@Service
@Transactional
public class ComisionService {

    private static final Logger log = LoggerFactory.getLogger(ComisionService.class);

    public static final BigDecimal PORCENTAJE_DEFECTO = new BigDecimal("5");
    /** Tope de la comisión de cada beneficiario, en % del valor de la venta. */
    public static final BigDecimal PORCENTAJE_MAXIMO = new BigDecimal("10");
    private static final String TIPO_GASTO_COMISIONES = "Comisiones";
    private static final Set<String> ETIQUETAS_PAGO_INICIAL = Set.of("Enganche", "Pago inicial");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final String VARIOS_DESARROLLOS = "Varios desarrollos";
    private static final String VENTA_ELIMINADA = "Venta eliminada";
    private static final String SIN_LOTES = "Sin lotes";

    private final VentaComisionRepository comisionRepository;
    private final VentaComisionDevengoRepository devengoRepository;
    private final VentaComisionEntregaRepository entregaRepository;
    private final VentaComisionEntregaAbonoRepository entregaAbonoRepository;
    private final VentaComisionDevolucionRepository devolucionRepository;
    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final GastoRepository gastoRepository;
    private final TipoGastoRepository tipoGastoRepository;
    private final FinanzasService finanzasService;
    private final CurrentUserProvider currentUserProvider;

    public ComisionService(
            VentaComisionRepository comisionRepository,
            VentaComisionDevengoRepository devengoRepository,
            VentaComisionEntregaRepository entregaRepository,
            VentaComisionEntregaAbonoRepository entregaAbonoRepository,
            VentaComisionDevolucionRepository devolucionRepository,
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            PagoVentaRepository pagoVentaRepository,
            GastoRepository gastoRepository,
            TipoGastoRepository tipoGastoRepository,
            FinanzasService finanzasService,
            CurrentUserProvider currentUserProvider) {
        this.comisionRepository = comisionRepository;
        this.devengoRepository = devengoRepository;
        this.entregaRepository = entregaRepository;
        this.entregaAbonoRepository = entregaAbonoRepository;
        this.devolucionRepository = devolucionRepository;
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.gastoRepository = gastoRepository;
        this.tipoGastoRepository = tipoGastoRepository;
        this.finanzasService = finanzasService;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------------- enganche a la venta

    /** Llamado por VentaService al registrar una venta, editarla o registrarle un abono: crea la
     * comisión si no existe y deja al día a quién va, su modalidad y lo que lleva ganado. */
    public void sincronizar(Venta venta) {
        sincronizar(venta, false, null, null, null, null);
    }

    /** Igual que {@link #sincronizar(Venta)} pero para una venta recién creada: su comisión usa la
     * regla por tipo de abono (enganche completo, mensualidad y aportación a la mitad, apartado sin
     * comisión, entrega frenada hasta que el contrato esté firmado). Las comisiones que ya existían
     * (y las que se generan para ventas anteriores) conservan su cálculo de siempre. */
    public void sincronizarVentaNueva(
            Venta venta,
            BigDecimal porcentaje,
            Usuario coasesorUsuario,
            AsesorExterno coasesorExterno,
            BigDecimal coasesorPorcentaje) {
        sincronizar(venta, true, porcentaje, coasesorUsuario, coasesorExterno, coasesorPorcentaje);
    }

    /** Crea la comisión (con el % elegido al registrar la venta, 5% por defecto) y la del copropietario si
     * se indicó uno, y deja al día lo ganado de cada una. */
    private void sincronizar(
            Venta venta,
            boolean ventaNueva,
            BigDecimal porcentaje,
            Usuario coasesorUsuario,
            AsesorExterno coasesorExterno,
            BigDecimal coasesorPorcentaje) {
        sincronizarPrincipal(venta, ventaNueva, porcentaje);
        sincronizarSecundaria(venta, ventaNueva, coasesorUsuario, coasesorExterno, coasesorPorcentaje);
    }

    private void sincronizarPrincipal(Venta venta, boolean ventaNueva, BigDecimal porcentaje) {
        VentaComision comision = comisionRepository.findByVentaIdAndSecundariaFalse(venta.getId()).orElse(null);
        if (comision == null) {
            if (venta.isSinComision()) {
                return;
            }
            comision = crearComision(venta, venta.getUsuarioAsesor(), venta.getAsesorExterno(), porcentaje);
            if (ventaNueva) comision.marcarReglaNueva();
            comision = comisionRepository.save(comision);
        } else if (!comision.isCancelada()) {
            boolean sinEntregas = !entregaRepository.existsByComisionId(comision.getId());
            comision.setCliente(venta.getCliente());
            if (sinEntregas) {
                // Mientras no se haya entregado nada, la comisión sigue al asesor y a las condiciones de la venta.
                comision.setAsesor(venta.getUsuarioAsesor(), venta.getAsesorExterno());
                comision.setModalidad(modalidadPara(venta, comision.getMonto()));
            }
        }
        if (!comision.isCancelada()) {
            reconstruirDevengos(comision, venta);
        }
    }

    /** Copropietario de la venta (un segundo asesor): su propia comisión sobre el mismo valor. */
    private void sincronizarSecundaria(
            Venta venta,
            boolean ventaNueva,
            Usuario coasesorUsuario,
            AsesorExterno coasesorExterno,
            BigDecimal coasesorPorcentaje) {
        VentaComision secundaria = comisionRepository.findByVentaIdAndSecundariaTrue(venta.getId()).orElse(null);
        if (secundaria == null && ventaNueva && (coasesorUsuario != null || coasesorExterno != null)) {
            secundaria = crearComision(venta, coasesorUsuario, coasesorExterno, coasesorPorcentaje);
            secundaria.marcarReglaNueva();
            secundaria.marcarSecundaria();
            secundaria = comisionRepository.save(secundaria);
        } else if (secundaria != null && !secundaria.isCancelada()) {
            secundaria.setCliente(venta.getCliente());
        }
        if (secundaria != null && !secundaria.isCancelada()) {
            reconstruirDevengos(secundaria, venta);
        }
    }

    /** Comisión nueva (sin guardar) sobre el valor de los lotes de la venta, con el % elegido (5% por defecto). */
    private VentaComision crearComision(Venta venta, Usuario asesor, AsesorExterno asesorExterno, BigDecimal porcentaje) {
        BigDecimal base = valorDeVenta(venta.getId());
        BigDecimal pct = porcentaje != null ? porcentaje.setScale(4, RoundingMode.HALF_UP) : PORCENTAJE_DEFECTO;
        BigDecimal monto = porcentajeDe(base, pct);
        VentaComision comision = new VentaComision(
                venta, venta.getCliente(), asesor, asesorExterno, base, pct, monto, modalidadPara(venta, monto));
        // Un % distinto al 5% por defecto se marca como elegido a mano (✎ en Finanzas).
        if (pct.compareTo(PORCENTAJE_DEFECTO) != 0) comision.setMontoYPorcentaje(monto, pct, true);
        return comision;
    }

    /** La venta se va a eliminar: su comisión se queda en el historial ("Venta eliminada"), con el
     * número de venta que tenía. Lo ya ganado sigue pudiéndose entregar; deja de acumular. */
    public void desvincularVenta(Venta venta) {
        comisionRepository.findAllByVentaId(venta.getId()).forEach(c -> c.desvincularVenta(venta.getNumeroGlobal(), venta.getEtiqueta()));
        comisionRepository.flush();
    }

    /** Comisiones de las ventas que ya existían antes de este módulo (o que quedaron sin una). */
    @EventListener(ApplicationReadyEvent.class)
    public void generarFaltantes() {
        try {
            for (Venta venta : ventaRepository.findAll()) {
                if (!venta.isSinComision() && !comisionRepository.existsByVentaIdAndSecundariaFalse(venta.getId())) {
                    sincronizar(venta);
                }
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron generar las comisiones de las ventas existentes", e);
        }
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<ComisionDto> listar() {
        exigirAdminOLider();
        ComisionTotales totales = totales();
        return totales.comisiones.stream().map(c -> aDto(c, totales)).toList();
    }

    @Transactional(readOnly = true)
    public ComisionDetalleDto obtener(Long id) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        return detalle(comision);
    }

    @Transactional(readOnly = true)
    public ComisionResumenDto resumen() {
        exigirAdminOLider();
        ComisionTotales totales = totales();
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal devengado = BigDecimal.ZERO;
        BigDecimal entregado = BigDecimal.ZERO;
        BigDecimal porEntregar = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        long retrasadas = 0;
        BigDecimal montoRetrasado = BigDecimal.ZERO;
        LocalDate hoy = LocalDate.now();
        for (VentaComision c : totales.comisiones) {
            // Una comisión cancelada no cuenta en nada, tampoco en las retrasadas: ya no se entrega.
            if (c.isCancelada()) continue;
            BigDecimal vencido = totales.vencido(c.getId(), hoy);
            if (vencido.signum() > 0) {
                retrasadas++;
                montoRetrasado = montoRetrasado.add(vencido);
            }
            BigDecimal dev = totales.devengado(c.getId());
            BigDecimal ent = totales.entregado(c.getId());
            cantidad++;
            total = total.add(c.getMonto());
            devengado = devengado.add(dev);
            entregado = entregado.add(ent);
            if (!totales.bloqueada(c.getId())) porEntregar = porEntregar.add(dev.subtract(ent).max(BigDecimal.ZERO));
            pendiente = pendiente.add(c.getMonto().subtract(dev).max(BigDecimal.ZERO));
        }
        return new ComisionResumenDto(
                cantidad, total, devengado, entregado, porEntregar, pendiente, retrasadas, montoRetrasado);
    }

    /** Qué toca entregar al sábado indicado (null = el próximo sábado, hoy si es sábado), sin las canceladas. De cada
     * comisión van también los abonos de los que sale lo ganado hasta ese sábado y lo ya entregado. */
    @Transactional(readOnly = true)
    public ComisionesPorEntregarDto porEntregar(LocalDate fecha) {
        exigirAdminOLider();
        LocalDate sabado = fecha != null
                ? fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                : LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        ComisionTotales totales = totales();
        List<ComisionesPorEntregarDto.Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (VentaComision c : totales.comisiones) {
            if (c.isCancelada()) continue;
            BigDecimal ganadoAlSabado = totales.devengadoHasta(c.getId(), sabado);
            BigDecimal yaEntregado = totales.entregado(c.getId());
            BigDecimal alSabado = ganadoAlSabado.subtract(yaEntregado).max(BigDecimal.ZERO);
            if (alSabado.signum() > 0) {
                List<ComisionesPorEntregarDto.Abono> abonos = totales.devengos(c.getId()).stream()
                        .filter(d -> !d.getFechaEntrega().isAfter(sabado))
                        .map(d -> abonoDe(c, d))
                        .toList();
                items.add(new ComisionesPorEntregarDto.Item(aDto(c, totales), alSabado, ganadoAlSabado, yaEntregado, abonos));
                total = total.add(alSabado);
            }
        }
        return new ComisionesPorEntregarDto(sabado, total, items);
    }

    /** De dónde sale una porción de la comisión generada: el abono del cliente y el porcentaje que se le aplica. */
    private static ComisionesPorEntregarDto.Abono abonoDe(VentaComision comision, VentaComisionDevengo devengo) {
        PagoVenta pago = devengo.getPagoVenta();
        if (pago == null) {
            // Regla anterior, una exhibición: la comisión completa se generó en la fecha de la venta.
            return new ComisionesPorEntregarDto.Abono(
                    devengo.getFechaOrigen(), "Venta", null, null, false, devengo.getMonto(), devengo.getFechaEntrega());
        }
        boolean completo = comision.isReglaNueva() && pago.getTipo() == TipoAbono.ENGANCHE;
        BigDecimal segunRegla = completo
                ? pago.getMonto()
                : mitad(pago.getMonto());
        // El último abono puede aportar menos: solo lo que faltaba para completar la comisión.
        boolean parcial = devengo.getMonto().compareTo(segunRegla) < 0;
        return new ComisionesPorEntregarDto.Abono(
                devengo.getFechaOrigen(), conceptoDe(devengo), pago.getMonto(), completo ? 100 : 50, parcial,
                devengo.getMonto(), devengo.getFechaEntrega());
    }

    /** Cómo se reparte el total de las comisiones no canceladas, y lo mismo por asesor y por desarrollo (ver
     * ComisionDesgloseDto: las cifras siempre suman). Una venta con lotes de dos desarrollos va en "Varios
     * desarrollos"; la comisión de una venta eliminada, en "Venta eliminada". */
    @Transactional(readOnly = true)
    public ComisionDesgloseDto desglose() {
        exigirAdminOLider();
        ComisionTotales totales = totales();
        LocalDate hoy = LocalDate.now();
        LocalDate sabado = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        Map<Long, Set<String>> desarrollosDeVenta = new HashMap<>();
        for (Object[] fila : ventaLoteRepository.desarrollosPorVenta()) {
            desarrollosDeVenta.computeIfAbsent((Long) fila[0], k -> new TreeSet<>()).add((String) fila[1]);
        }

        Suma total = new Suma();
        Map<String, Suma> porAsesor = new LinkedHashMap<>();
        Map<String, String> nombreAsesor = new HashMap<>();
        Map<String, Suma> porDesarrollo = new LinkedHashMap<>();
        long detenidas = 0;
        for (VentaComision c : totales.comisiones) {
            if (c.isCancelada()) continue;
            ComisionDesgloseDto.Cifras cifras = cifrasDe(c, totales, hoy, sabado);
            total.sumar(cifras);
            if (cifras.detenido().signum() > 0) detenidas++;

            String asesor = claveAsesor(c);
            nombreAsesor.putIfAbsent(asesor, c.getAsesorExterno() != null
                    ? c.getAsesorExterno().getNombre()
                    : c.getUsuarioAsesor().getNombre());
            porAsesor.computeIfAbsent(asesor, k -> new Suma()).sumar(cifras);

            String desarrollo = desarrolloDe(c, desarrollosDeVenta);
            porDesarrollo.computeIfAbsent(desarrollo, k -> new Suma()).sumar(cifras);
        }

        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-MX"));
        orden.setStrength(Collator.PRIMARY);
        return new ComisionDesgloseDto(
                sabado, total.cifras(), detenidas,
                filasPorAsesor(porAsesor, nombreAsesor, orden), filasPorDesarrollo(porDesarrollo, orden));
    }

    /** El desarrollo de la comisión: el único de los lotes de su venta, o una de las etiquetas especiales. */
    private static String desarrolloDe(VentaComision c, Map<Long, Set<String>> desarrollosDeVenta) {
        if (c.getVenta() == null) return VENTA_ELIMINADA;
        Set<String> desarrollos = desarrollosDeVenta.getOrDefault(c.getVenta().getId(), Set.of());
        if (desarrollos.size() == 1) return desarrollos.iterator().next();
        return desarrollos.isEmpty() ? SIN_LOTES : VARIOS_DESARROLLOS;
    }

    private static List<ComisionDesgloseDto.Fila> filasPorAsesor(
            Map<String, Suma> porAsesor, Map<String, String> nombreAsesor, Collator orden) {
        return porAsesor.entrySet().stream()
                .map(e -> new ComisionDesgloseDto.Fila(
                        e.getKey(), nombreAsesor.get(e.getKey()), e.getKey().startsWith("E-"), e.getValue().cifras()))
                .sorted(Comparator.comparing(ComisionDesgloseDto.Fila::nombre, orden))
                .toList();
    }

    /** Los desarrollos por nombre; al final las filas especiales. */
    private static List<ComisionDesgloseDto.Fila> filasPorDesarrollo(Map<String, Suma> porDesarrollo, Collator orden) {
        List<String> especiales = List.of(VARIOS_DESARROLLOS, SIN_LOTES, VENTA_ELIMINADA);
        return porDesarrollo.entrySet().stream()
                .map(e -> new ComisionDesgloseDto.Fila(e.getKey(), e.getKey(), false, e.getValue().cifras()))
                .sorted(Comparator.<ComisionDesgloseDto.Fila>comparingInt(f -> especiales.indexOf(f.nombre()))
                        .thenComparing(ComisionDesgloseDto.Fila::nombre, orden))
                .toList();
    }

    /** Las cifras de una comisión (no cancelada) para el desglose; ver ComisionDesgloseDto.Cifras. */
    private static ComisionDesgloseDto.Cifras cifrasDe(VentaComision c, ComisionTotales totales, LocalDate hoy, LocalDate sabado) {
        BigDecimal ganado = totales.devengado(c.getId());
        BigDecimal entregado = totales.entregado(c.getId());
        BigDecimal pendiente = ganado.subtract(entregado).max(BigDecimal.ZERO);
        boolean bloqueada = totales.bloqueada(c.getId());
        BigDecimal porEntregar = bloqueada ? BigDecimal.ZERO : pendiente;
        // vencido y devengadoHasta ya dan 0 en una comisión detenida.
        BigDecimal retrasado = totales.vencido(c.getId(), hoy);
        BigDecimal alSabado = totales.devengadoHasta(c.getId(), sabado).subtract(entregado).max(BigDecimal.ZERO);
        BigDecimal tocaSabado = alSabado.subtract(retrasado).max(BigDecimal.ZERO);
        return new ComisionDesgloseDto.Cifras(
                1,
                c.getMonto(),
                c.getMonto().subtract(ganado).max(BigDecimal.ZERO),
                ganado,
                entregado,
                porEntregar,
                retrasado,
                tocaSabado,
                porEntregar.subtract(retrasado).subtract(tocaSabado).max(BigDecimal.ZERO),
                bloqueada ? pendiente : BigDecimal.ZERO,
                entregado.subtract(ganado).max(BigDecimal.ZERO));
    }

    /** "U-5" (usuario interno) / "E-3" (asesor externo). */
    private static String claveAsesor(VentaComision c) {
        return c.getAsesorExterno() != null ? "E-" + c.getAsesorExterno().getId() : "U-" + c.getUsuarioAsesor().getId();
    }

    /** Acumula las cifras de varias comisiones. */
    private static final class Suma {
        private long comisiones;
        private BigDecimal total = BigDecimal.ZERO, faltaPorGenerar = BigDecimal.ZERO, ganado = BigDecimal.ZERO,
                entregado = BigDecimal.ZERO, porEntregar = BigDecimal.ZERO, retrasado = BigDecimal.ZERO,
                tocaSabado = BigDecimal.ZERO, masAdelante = BigDecimal.ZERO, detenido = BigDecimal.ZERO,
                entregadoDeMas = BigDecimal.ZERO;

        void sumar(ComisionDesgloseDto.Cifras c) {
            comisiones += c.comisiones();
            total = total.add(c.total());
            faltaPorGenerar = faltaPorGenerar.add(c.faltaPorGenerar());
            ganado = ganado.add(c.ganado());
            entregado = entregado.add(c.entregado());
            porEntregar = porEntregar.add(c.porEntregar());
            retrasado = retrasado.add(c.retrasado());
            tocaSabado = tocaSabado.add(c.tocaSabado());
            masAdelante = masAdelante.add(c.masAdelante());
            detenido = detenido.add(c.detenido());
            entregadoDeMas = entregadoDeMas.add(c.entregadoDeMas());
        }

        ComisionDesgloseDto.Cifras cifras() {
            return new ComisionDesgloseDto.Cifras(comisiones, total, faltaPorGenerar, ganado, entregado, porEntregar,
                    retrasado, tocaSabado, masAdelante, detenido, entregadoDeMas);
        }
    }

    // ---------------------------------------------------------------- acciones

    /** Cambia el monto o el porcentaje (exactamente uno); el otro se calcula sobre el valor de la
     * venta. No puede quedar por debajo de lo ya entregado. */
    public ComisionDetalleDto editar(Long id, ComisionUpdateRequest request) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        if ((request.porcentaje() == null) == (request.monto() == null)) {
            throw new ValidationException("Indica el porcentaje o el monto (solo uno)");
        }

        MontoYPorcentaje nuevo = request.monto() != null
                ? desdeMonto(comision, request.monto())
                : desdePorcentaje(comision, request.porcentaje());
        BigDecimal monto = nuevo.monto();
        BigDecimal porcentaje = nuevo.porcentaje();

        BigDecimal entregado = totalesDe(comision).entregado(comision.getId());
        if (monto.compareTo(entregado) < 0) {
            throw new ConflictException("La comisión no puede ser menor que lo ya pagado ($" + entregado + ")");
        }

        comision.setMontoYPorcentaje(monto, porcentaje, true);
        if (comision.getVenta() != null && !comision.isCancelada()) {
            if (!entregaRepository.existsByComisionId(comision.getId())) {
                comision.setModalidad(modalidadPara(comision.getVenta(), monto));
            }
            reconstruirDevengos(comision, comision.getVenta());
        }
        return detalle(comision);
    }

    private record MontoYPorcentaje(BigDecimal monto, BigDecimal porcentaje) {
    }

    private static MontoYPorcentaje desdeMonto(VentaComision comision, BigDecimal montoPedido) {
        if (montoPedido.signum() < 0) throw new ValidationException("El monto no puede ser negativo");
        BigDecimal monto = montoPedido.setScale(2, RoundingMode.HALF_UP);
        BigDecimal porcentaje = comision.getBase().signum() == 0
                ? BigDecimal.ZERO
                : monto.multiply(CIEN).divide(comision.getBase(), 4, RoundingMode.HALF_UP);
        if (porcentaje.compareTo(PORCENTAJE_MAXIMO) > 0) {
            throw new ValidationException("La comisión no puede pasar de " + PORCENTAJE_MAXIMO.toPlainString() + "% del valor de la venta");
        }
        return new MontoYPorcentaje(monto, porcentaje);
    }

    private static MontoYPorcentaje desdePorcentaje(VentaComision comision, BigDecimal porcentajePedido) {
        if (porcentajePedido.signum() < 0 || porcentajePedido.compareTo(PORCENTAJE_MAXIMO) > 0) {
            throw new ValidationException("El porcentaje debe estar entre 0 y " + PORCENTAJE_MAXIMO.toPlainString());
        }
        BigDecimal porcentaje = porcentajePedido.setScale(4, RoundingMode.HALF_UP);
        return new MontoYPorcentaje(porcentajeDe(comision.getBase(), porcentaje), porcentaje);
    }

    public void cancelar(Long id) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        if (entregaRepository.existsByComisionId(id) || devolucionRepository.existsByComisionId(id)) {
            throw new ConflictException(
                    "No se puede cancelar ni eliminar una comisión que ya tiene pagos o devoluciones registrados");
        }
        Venta venta = comision.getVenta();
        if (venta != null && !comision.isSecundaria()) {
            venta.setSinComision(true);
            ventaRepository.save(venta);
        }
        devengoRepository.deleteByComisionId(id);
        devengoRepository.flush();
        comisionRepository.delete(comision);
        comisionRepository.flush();
    }

    public ComisionDetalleDto reactivar(Long id) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        comision.setCancelada(false);
        if (comision.getVenta() != null) reconstruirDevengos(comision, comision.getVenta());
        return detalle(comision);
    }

    /** Registra una entrega al asesor. No puede pasar de lo ya ganado y aún no entregado ("se paga
     * hasta donde el cliente pagó"); puede ser menos (parcial). Crea el gasto "Comisiones" y guarda lo
     * que necesita su ticket: IVA, factura y forma de pago elegidos, cómo estaba la comisión y qué abonos
     * cubre (las entregas cubren lo ganado en orden, ver {@link #repartirEntrega}). */
    public ComisionDetalleDto entregar(Long id, ComisionEntregaRequest request) {
        Usuario actual = exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);

        if (comision.isCancelada()) {
            throw new ConflictException("Esta comisión está cancelada: reactívala para registrar su pago");
        }
        ComisionTotales totales = totalesDe(comision);
        if (totales.bloqueada(id)) {
            throw new ConflictException("El contrato de esta venta no está firmado: la comisión queda retenida "
                    + "hasta registrar la firma en la venta");
        }
        BigDecimal yaEntregado = totales.entregado(id);
        BigDecimal porEntregar = totales.devengado(id).subtract(yaEntregado);
        if (request.monto().compareTo(porEntregar) > 0) {
            throw new ConflictException("El monto máximo a pagar es $" + porEntregar.max(BigDecimal.ZERO)
                    + ": es la comisión generada pendiente de pago");
        }

        LocalDate fecha = request.fecha() != null ? request.fecha() : LocalDate.now();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        String concepto = "Comisión · " + comision.getCliente()
                + (comision.getVenta() != null
                        ? " (venta " + comision.getVenta().getEtiqueta() + ")"
                        : "");
        Gasto gasto = gastoRepository.save(new Gasto(
                concepto, OrigenGasto.COMISION, tipoGastoComisiones(), fecha, request.monto(), actual));
        VentaComisionEntrega entrega = new VentaComisionEntrega(
                comision, fecha, request.monto(), notas, gasto, actual,
                request.conIva(), request.conFactura(), request.formaPago());
        List<VentaLote> lotes = comision.getVenta() != null
                ? ventaLoteRepository.findByVentaId(comision.getVenta().getId())
                : List.of();
        entrega.fijarDatosDelTicket(
                comision.getMonto(), comision.getPorcentaje(), yaEntregado.add(request.monto()),
                desarrollosDe(lotes), lotesDe(lotes));
        entrega = entregaRepository.save(entrega);

        guardarAbonosDelTicket(entrega, porcionesGanadas(id), yaEntregado);
        return detalle(comision);
    }

    /** Corrige la fecha y el monto de la entrega que originó un egreso (se llama al editar ese gasto en Gastos), para
     * que la comisión, el ticket y el egreso sigan cuadrando. El monto nuevo no puede pasar de lo ganado y aún no
     * entregado, contando lo que esta misma entrega ya tenía. Si cambia, se recalcula el acumulado del ticket de esta
     * entrega y de las posteriores, y qué abonos cubre cada una. */
    public void corregirEntregaDeGasto(Long gastoId, LocalDate fecha, BigDecimal monto) {
        exigirAdminOLider();
        VentaComisionEntrega entrega = entregaRepository
                .findByGastoId(gastoId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la entrega de comisión de este gasto"));
        Long comisionId = entrega.getComision().getId();
        BigDecimal anterior = entrega.getMonto();
        BigDecimal diferencia = monto.subtract(anterior);
        if (diferencia.signum() > 0) {
            ComisionTotales totales = totalesDe(entrega.getComision());
            BigDecimal disponible = totales.devengado(comisionId).subtract(totales.entregado(comisionId));
            if (diferencia.compareTo(disponible) > 0) {
                throw new ConflictException("Solo hay $" + anterior.add(disponible.max(BigDecimal.ZERO))
                        + " ganados en esta comisión para esta entrega; el cliente no ha pagado más");
            }
        }
        entrega.corregir(fecha, monto);
        entregaRepository.save(entrega);
        if (diferencia.signum() == 0) return;

        List<VentaComisionEntrega> afectadas = entregaRepository.findByComisionIdOrderByFechaDescIdDesc(comisionId).stream()
                .filter(e -> e.getId() >= entrega.getId())
                .sorted(Comparator.comparing(VentaComisionEntrega::getId))
                .toList();
        List<Porcion> ganado = porcionesGanadas(comisionId);
        for (VentaComisionEntrega e : afectadas) {
            e.ajustarEntregadoAcumulado(diferencia);
            if (!e.tieneTicket()) continue;
            entregaAbonoRepository.deleteByEntregaId(e.getId());
            entregaAbonoRepository.flush();
            guardarAbonosDelTicket(e, ganado, e.getEntregadoAcumulado().subtract(e.getMonto()));
        }
    }

    /** Lo ganado por la comisión, porción por porción y en el orden en que se entrega. */
    private List<Porcion> porcionesGanadas(Long comisionId) {
        return devengoRepository.findByComisionIdOrderByFechaEntregaAscIdAsc(comisionId).stream()
                .map(d -> new Porcion(d.getFechaOrigen(), conceptoDe(d), d.getMonto()))
                .toList();
    }

    /** Guarda en el ticket de la entrega de qué abonos sale (ver {@link #repartirEntrega}). */
    private void guardarAbonosDelTicket(VentaComisionEntrega entrega, List<Porcion> ganado, BigDecimal yaEntregado) {
        for (Porcion p : repartirEntrega(ganado, yaEntregado, entrega.getMonto())) {
            entregaAbonoRepository.save(new VentaComisionEntregaAbono(entrega, p.fecha(), p.concepto(), p.monto()));
        }
    }

    /** Anula la entrega ligada a un egreso de comisión (se llama al eliminar ese gasto en Gastos): la comisión vuelve
     * a quedar pendiente por pagar. El gasto se borra aquí, igual que al anular la entrega desde Finanzas. */
    public void anularEntregaDeGasto(Long gastoId) {
        exigirAdminOLider();
        VentaComisionEntrega entrega = entregaRepository
                .findByGastoId(gastoId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la entrega de comisión de este gasto"));
        anularEntrega(entrega.getComision().getId(), entrega.getId());
    }

    /** Datos del ticket de pago de una entrega (solo las registradas desde que existen los tickets). */
    @Transactional(readOnly = true)
    public ComisionTicketDto ticket(Long comisionId, Long entregaId) {
        exigirAdminOLider();
        VentaComisionEntrega entrega = entregaRepository
                .findById(entregaId)
                .filter(e -> e.getComision().getId().equals(comisionId))
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada"));
        if (!entrega.tieneTicket()) {
            throw new ConflictException("Esta entrega se registró antes de que existieran los tickets de pago: no tiene ticket");
        }
        VentaComision comision = entrega.getComision();
        Venta venta = comision.getVenta();
        Gasto gasto = entrega.getGasto();
        boolean externo = comision.getAsesorExterno() != null;
        List<ComisionTicketDto.Abono> abonos = entregaAbonoRepository
                .findByEntregaIdOrderByFechaAbonoAscIdAsc(entregaId)
                .stream()
                .map(a -> new ComisionTicketDto.Abono(a.getFechaAbono(), a.getConcepto(), a.getMonto()))
                .toList();
        return new ComisionTicketDto(
                entrega.getId(),
                entrega.getFechaCreacion(),
                entrega.getFecha(),
                entrega.getMonto(),
                entrega.getConIva(),
                entrega.getConFactura(),
                entrega.getFormaPago(),
                gasto != null ? gasto.getId() : null,
                gasto != null ? gasto.getFecha() : null,
                gasto != null && gasto.getTipoGasto() != null ? gasto.getTipoGasto().getNombre() : null,
                externo ? comision.getAsesorExterno().getNombre() : comision.getUsuarioAsesor().getNombre(),
                externo,
                comision.isSecundaria(),
                venta != null ? venta.getEtiqueta() : comision.getVentaEtiqueta(),
                venta == null,
                comision.getCliente(),
                entrega.getDesarrollo(),
                entrega.getLotes(),
                entrega.getComisionPorcentaje(),
                entrega.getComisionMonto(),
                entrega.getEntregadoAcumulado(),
                abonos);
    }

    /** El asesor devuelve (total o parcialmente) una comisión ya entregada. Se captura a mano. No toca
     * la entrega ni su gasto: queda como un ingreso aparte en Finanzas. No puede pasar de lo que
     * queda entregado (neto de devoluciones anteriores). */
    public ComisionDetalleDto devolver(Long id, ComisionDevolucionRequest request) {
        Usuario actual = exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);

        BigDecimal entregado = totalesDe(comision).entregado(id);
        if (request.monto().compareTo(entregado) > 0) {
            throw new ConflictException("El monto máximo a devolver es $" + entregado.max(BigDecimal.ZERO)
                    + " (lo pagado al asesor en esta comisión)");
        }
        LocalDate fecha = request.fecha() != null ? request.fecha() : LocalDate.now();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        devolucionRepository.save(new VentaComisionDevolucion(comision, fecha, request.monto(), notas, actual));
        return detalle(comision);
    }

    /** Anula una entrega y borra el gasto que generó (y su desglose de ticket, por ON DELETE CASCADE). */
    public ComisionDetalleDto anularEntrega(Long comisionId, Long entregaId) {
        exigirAdminOLider();
        VentaComisionEntrega entrega = entregaRepository
                .findById(entregaId)
                .filter(e -> e.getComision().getId().equals(comisionId))
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada"));
        VentaComision comision = entrega.getComision();
        Gasto gasto = entrega.getGasto();
        entregaRepository.delete(entrega);
        entregaRepository.flush();
        if (gasto != null) gastoRepository.delete(gasto);
        return detalle(comision);
    }

    // ---------------------------------------------------------------- cálculo

    /** Una porción de lo ganado (o de una entrega) con la fecha y el tipo del abono de donde sale. */
    record Porcion(LocalDate fecha, String concepto, BigDecimal monto) {
    }

    /** Qué parte de una entrega sale de cada porción de lo ganado. Las entregas van cubriendo lo ganado en
     * orden (la primera porción primero), así que esta cubre el tramo que va de lo ya entregado antes
     * (yaEntregado, neto de devoluciones) a yaEntregado + monto. */
    static List<Porcion> repartirEntrega(List<Porcion> ganado, BigDecimal yaEntregado, BigDecimal monto) {
        BigDecimal desde = yaEntregado.max(BigDecimal.ZERO);
        BigDecimal hasta = desde.add(monto);
        BigDecimal acumulado = BigDecimal.ZERO;
        List<Porcion> partes = new ArrayList<>();
        for (Porcion p : ganado) {
            BigDecimal inicio = acumulado;
            acumulado = acumulado.add(p.monto());
            BigDecimal parte = acumulado.min(hasta).subtract(inicio.max(desde));
            if (parte.signum() > 0) partes.add(new Porcion(p.fecha(), p.concepto(), parte));
            if (acumulado.compareTo(hasta) >= 0) break;
        }
        return partes;
    }

    /** Cómo se llama en el ticket el abono de donde sale una porción de lo ganado. */
    private static String conceptoDe(VentaComisionDevengo devengo) {
        PagoVenta pago = devengo.getPagoVenta();
        if (pago == null) return "Venta"; // comisión completa de una exhibición (regla anterior)
        if (pago.getTipo() == null) return "Abono";
        return switch (pago.getTipo()) {
            case ENGANCHE -> "Enganche";
            case MENSUALIDAD -> "Mensualidad";
            case A_CUENTA_COMISION -> "A cuenta de comisión";
            case APORTACION -> "Aportación";
            case APARTADO -> "Apartado";
        };
    }

    /** Desarrollo(s) de los lotes de la venta, para el ticket ("Samai Campestre"). */
    private static String desarrollosDe(List<VentaLote> lotes) {
        String texto = lotes.stream()
                .map(vl -> vl.getLote().getDesarrollo().getNombre())
                .distinct()
                .collect(Collectors.joining(", "));
        return texto.isEmpty() ? null : recortar(texto, 200);
    }

    /** Lotes de la venta, para el ticket ("Mz 4 Lt 12, Mz 4 Lt 13"). */
    private static String lotesDe(List<VentaLote> lotes) {
        String texto = lotes.stream()
                .map(vl -> "Mz " + vl.getLote().getManzana() + " Lt " + vl.getLote().getNumeroLote())
                .collect(Collectors.joining(", "));
        return texto.isEmpty() ? null : recortar(texto, 300);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    /** Borra y vuelve a calcular lo ganado: la comisión completa en la fecha de venta si es una
     * exhibición, o la mitad de cada abono (en orden) hasta completar el monto. */
    private void reconstruirDevengos(VentaComision comision, Venta venta) {
        devengoRepository.deleteByComisionId(comision.getId());
        devengoRepository.flush();

        if (comision.getMonto().signum() <= 0) return;

        if (comision.isReglaNueva()) {
            reconstruirDevengosPorTipo(comision, venta);
            return;
        }

        if (comision.getModalidad() == ModalidadComision.UNA_EXHIBICION) {
            devengoRepository.save(new VentaComisionDevengo(
                    comision, null, venta.getFechaVenta(), sabadoSiguiente(venta.getFechaVenta()), comision.getMonto()));
            return;
        }

        BigDecimal restante = comision.getMonto();
        for (PagoVenta pago : pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId())) {
            if (restante.signum() <= 0) break;
            BigDecimal aporte = mitad(pago.getMonto()).min(restante);
            if (aporte.signum() <= 0) continue;
            devengoRepository.save(
                    new VentaComisionDevengo(comision, pago, pago.getFecha(), sabadoSiguiente(pago.getFecha()), aporte));
            restante = restante.subtract(aporte);
        }
    }

    /**
     * Regla por tipo de abono (ventas nuevas), sobre los abonos vigentes en orden: el ENGANCHE cuenta
     * completo; la MENSUALIDAD y la APORTACION, la mitad; el APARTADO no cuenta (suelen ser $500 o
     * $1,000); un abono sin tipo ("por completar") tampoco cuenta hasta que se clasifique. Se acumula
     * hasta completar la comisión. Cada aporte se entrega el sábado siguiente a su abono y, si el
     * contrato se firmó después, no antes del sábado siguiente a la firma. Mientras el contrato no
     * esté firmado, lo acumulado no se puede entregar (ver ComisionTotales.bloqueada).
     */
    private void reconstruirDevengosPorTipo(VentaComision comision, Venta venta) {
        LocalDate sabadoFirma = venta.isContratoFirmado() && venta.getContratoFechaFirma() != null
                ? sabadoSiguiente(venta.getContratoFechaFirma())
                : null;
        BigDecimal restante = comision.getMonto();
        BigDecimal enganche = BigDecimal.ZERO;
        for (PagoVenta pago : pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId())) {
            if (pago.getTipo() == TipoAbono.ENGANCHE) enganche = enganche.add(pago.getMonto());
            if (restante.signum() <= 0) continue;
            BigDecimal aporte = aporteDe(pago).min(restante);
            if (aporte.signum() <= 0) continue;
            LocalDate entrega = sabadoSiguiente(pago.getFecha());
            if (sabadoFirma != null && sabadoFirma.isAfter(entrega)) entrega = sabadoFirma;
            devengoRepository.save(new VentaComisionDevengo(comision, pago, pago.getFecha(), entrega, aporte));
            restante = restante.subtract(aporte);
        }
        // Solo informativo: si el enganche cubrió la comisión completa se muestra como una exhibición.
        comision.setModalidad(
                enganche.compareTo(comision.getMonto()) >= 0 ? ModalidadComision.UNA_EXHIBICION : ModalidadComision.PARCIALIDADES);
    }

    /** Lo que aporta un abono a la comisión según su tipo (ver reconstruirDevengosPorTipo). */
    private static BigDecimal aporteDe(PagoVenta pago) {
        if (pago.getTipo() == null) return BigDecimal.ZERO;
        return switch (pago.getTipo()) {
            case ENGANCHE -> pago.getMonto();
            case MENSUALIDAD, A_CUENTA_COMISION, APORTACION -> mitad(pago.getMonto());
            case APARTADO -> BigDecimal.ZERO;
        };
    }

    /** Si el enganche/pago inicial cubre la comisión se entrega completa; si no, por parcialidades.
     * Una "Aportación anual" no cuenta como pago inicial. */
    private ModalidadComision modalidadPara(Venta venta, BigDecimal montoComision) {
        boolean cuentaComoPagoInicial = venta.getEnganche() != null
                && venta.getEngancheLabel() != null
                && ETIQUETAS_PAGO_INICIAL.contains(venta.getEngancheLabel());
        return cuentaComoPagoInicial && venta.getEnganche().compareTo(montoComision) >= 0
                ? ModalidadComision.UNA_EXHIBICION
                : ModalidadComision.PARCIALIDADES;
    }

    /** El sábado siguiente (siempre posterior: un abono en sábado se entrega el sábado que sigue). */
    static LocalDate sabadoSiguiente(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    }

    private BigDecimal valorDeVenta(Long ventaId) {
        return ventaLoteRepository.findByVentaId(ventaId).stream()
                .map(VentaLote::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal mitad(BigDecimal monto) {
        return monto.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentajeDe(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- DTOs

    private ComisionTotales totales() {
        return ComisionTotales.deTodas(
                comisionRepository.findAllByOrderByIdDesc(),
                finanzasService.cobroPorVenta(),
                devengoRepository.findAllConPagoOrdenados(),
                entregaRepository.findAll(),
                devolucionRepository.findAll());
    }

    private ComisionTotales totalesDe(VentaComision c) {
        return totalesDe(
                c,
                devengoRepository.findByComisionIdOrderByFechaEntregaAscIdAsc(c.getId()),
                entregaRepository.findByComisionIdOrderByFechaDescIdDesc(c.getId()),
                devolucionRepository.findByComisionIdOrderByFechaAscIdAsc(c.getId()));
    }

    private ComisionTotales totalesDe(
            VentaComision c,
            List<VentaComisionDevengo> devengos,
            List<VentaComisionEntrega> entregas,
            List<VentaComisionDevolucion> devoluciones) {
        FinanzasService.CobroVenta cobro = c.getVenta() != null ? finanzasService.cobroDeVenta(c.getVenta()) : null;
        return ComisionTotales.deUna(c, cobro, devengos, entregas, devoluciones);
    }

    private ComisionDto aDto(VentaComision c, ComisionTotales totales) {
        BigDecimal devengado = totales.devengado(c.getId());
        BigDecimal entregado = totales.entregado(c.getId());
        // Cancelada o detenida (contrato sin firmar): no hay nada que se pueda entregar.
        BigDecimal porEntregar = c.isCancelada() || totales.bloqueada(c.getId())
                ? BigDecimal.ZERO
                : devengado.subtract(entregado).max(BigDecimal.ZERO);

        BigDecimal vencido = c.isCancelada() ? BigDecimal.ZERO : totales.vencido(c.getId(), LocalDate.now());
        Venta venta = c.getVenta();
        FinanzasService.CobroVenta cobro = totales.cobroDeVenta(venta != null ? venta.getId() : null);
        boolean externo = c.getAsesorExterno() != null;
        String asesor = externo ? c.getAsesorExterno().getNombre() : c.getUsuarioAsesor().getNombre();
        return new ComisionDto(
                c.getId(),
                venta != null ? venta.getId() : null,
                venta != null ? venta.getEtiqueta() : c.getVentaEtiqueta(),
                venta == null,
                c.getCliente(),
                asesor,
                externo,
                claveAsesor(c),
                c.getBase(),
                venta != null ? venta.getMensualidad() : null,
                totales.abonadoDeVenta(venta != null ? venta.getId() : null),
                cobro.mensualidadesAtrasadas(),
                cobro.pagosAtrasados(),
                cobro.atrasoMonto(),
                estadoPagoMes(cobro),
                cobro.esperadoMes(),
                cobro.recibidoMes(),
                cobro.fechaMes(),
                c.getPorcentaje(),
                c.getMonto(),
                c.isMontoManual(),
                c.getModalidad(),
                estadoDe(c, devengado, entregado),
                c.isCancelada(),
                devengado,
                entregado,
                totales.devuelto(c.getId()),
                totales.porDevolver(c.getId()),
                porEntregar,
                porEntregar.signum() > 0 ? totales.proximaEntrega(c.getId()) : null,
                vencido.signum() > 0,
                vencido,
                c.getFechaCreacion(),
                c.isReglaNueva(),
                c.getVenta() != null && c.getVenta().isContratoFirmado(),
                c.getVenta() != null ? c.getVenta().getContratoFechaFirma() : null,
                totales.bloqueada(c.getId()),
                c.isSecundaria());
    }

    private static EstadoComision estadoDe(VentaComision c, BigDecimal devengado, BigDecimal entregado) {
        if (c.isCancelada()) return EstadoComision.CANCELADA;
        if (c.getMonto().signum() > 0 && entregado.compareTo(c.getMonto()) >= 0) return EstadoComision.PAGADA;
        if (entregado.signum() > 0) return EstadoComision.PARCIAL;
        if (devengado.signum() > 0) return EstadoComision.ACUMULANDO;
        return EstadoComision.PENDIENTE;
    }

    /** Cómo va el cliente este mes; null si este mes no le toca pagar nada. */
    private static EstadoPagoCliente estadoPagoMes(FinanzasService.CobroVenta cobro) {
        if (cobro.esperadoMes().signum() <= 0) return null;
        if (cobro.recibidoMes().add(new BigDecimal("0.01")).compareTo(cobro.esperadoMes()) >= 0) {
            return EstadoPagoCliente.YA_ABONO;
        }
        if (cobro.recibidoMes().signum() > 0) return EstadoPagoCliente.ABONO_PARCIAL;
        return cobro.fechaMes() != null && cobro.fechaMes().isBefore(LocalDate.now())
                ? EstadoPagoCliente.SIN_ABONAR
                : EstadoPagoCliente.POR_VENCER;
    }

    private ComisionDetalleDto detalle(VentaComision comision) {
        List<VentaComisionDevengo> listaDevengos =
                devengoRepository.findByComisionIdOrderByFechaEntregaAscIdAsc(comision.getId());
        List<VentaComisionEntrega> listaEntregas =
                entregaRepository.findByComisionIdOrderByFechaDescIdDesc(comision.getId());
        List<VentaComisionDevolucion> listaDevoluciones =
                devolucionRepository.findByComisionIdOrderByFechaAscIdAsc(comision.getId());
        ComisionTotales totales = totalesDe(comision, listaDevengos, listaEntregas, listaDevoluciones);
        List<ComisionDetalleDto.Devengo> devengos = listaDevengos.stream()
                .map(d -> new ComisionDetalleDto.Devengo(d.getFechaOrigen(), d.getFechaEntrega(), d.getMonto()))
                .toList();
        List<ComisionDetalleDto.Entrega> entregas = listaEntregas.stream()
                .map(e -> new ComisionDetalleDto.Entrega(
                        e.getId(),
                        e.getFecha(),
                        e.getMonto(),
                        e.getNotas(),
                        e.getGasto() != null ? e.getGasto().getId() : null,
                        e.getRegistradaPor().getNombre(),
                        e.getFechaCreacion(),
                        e.getConIva(),
                        e.getConFactura(),
                        e.getFormaPago(),
                        e.tieneTicket()))
                .toList();
        List<ComisionDetalleDto.Devolucion> devoluciones = listaDevoluciones.stream()
                .map(d -> new ComisionDetalleDto.Devolucion(
                        d.getId(),
                        d.getFecha(),
                        d.getMonto(),
                        d.getNotas(),
                        d.getRegistradaPor().getNombre(),
                        d.getFechaCreacion()))
                .toList();
        return new ComisionDetalleDto(aDto(comision, totales), devengos, entregas, devoluciones);
    }

    // ---------------------------------------------------------------- utilidades

    private TipoGasto tipoGastoComisiones() {
        return tipoGastoRepository
                .findFirstByNombreIgnoreCase(TIPO_GASTO_COMISIONES)
                .orElseGet(() -> tipoGastoRepository.save(new TipoGasto(TIPO_GASTO_COMISIONES, false)));
    }

    private VentaComision obtenerEntidad(Long id) {
        return comisionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comisión no encontrada"));
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar comisiones");
        }
        return actual;
    }
}
