package com.werealestate.backend.service;

import com.werealestate.backend.dto.ComisionDetalleDto;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.ComisionEntregaRequest;
import com.werealestate.backend.dto.ComisionResumenDto;
import com.werealestate.backend.dto.ComisionUpdateRequest;
import com.werealestate.backend.dto.ComisionesPorEntregarDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.EstadoComision;
import com.werealestate.backend.model.EstadoPagoCliente;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.ModalidadComision;
import com.werealestate.backend.model.OrigenGasto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.model.VentaComisionDevengo;
import com.werealestate.backend.model.VentaComisionEntrega;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.TipoGastoRepository;
import com.werealestate.backend.repository.VentaComisionDevengoRepository;
import com.werealestate.backend.repository.VentaComisionEntregaRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * </ul>
 * Lo ganado ({@link VentaComisionDevengo}) se reconstruye por completo a partir de los abonos cada
 * vez que algo cambia, así que siempre es consistente con ellos.
 */
@Service
@Transactional
public class ComisionService {

    private static final Logger log = LoggerFactory.getLogger(ComisionService.class);

    public static final BigDecimal PORCENTAJE_DEFECTO = new BigDecimal("5");
    private static final String TIPO_GASTO_COMISIONES = "Comisiones";
    private static final Set<String> ETIQUETAS_PAGO_INICIAL = Set.of("Enganche", "Pago inicial");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final VentaComisionRepository comisionRepository;
    private final VentaComisionDevengoRepository devengoRepository;
    private final VentaComisionEntregaRepository entregaRepository;
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
        VentaComision comision = comisionRepository.findByVentaId(venta.getId()).orElse(null);
        if (comision == null) {
            BigDecimal base = valorDeVenta(venta.getId());
            BigDecimal monto = porcentajeDe(base, PORCENTAJE_DEFECTO);
            comision = comisionRepository.save(new VentaComision(
                    venta, venta.getCliente(), venta.getUsuarioAsesor(), venta.getAsesorExterno(), base,
                    PORCENTAJE_DEFECTO, monto, modalidadPara(venta, monto)));
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

    /** La venta se va a eliminar: su comisión se queda en el historial ("Venta eliminada"), con el
     * número de venta que tenía. Lo ya ganado sigue pudiéndose entregar; deja de acumular. */
    public void desvincularVenta(Venta venta) {
        comisionRepository.findByVentaId(venta.getId()).ifPresent(c -> c.desvincularVenta(venta.getNumero()));
        comisionRepository.flush();
    }

    /** Comisiones de las ventas que ya existían antes de este módulo (o que quedaron sin una). */
    @EventListener(ApplicationReadyEvent.class)
    public void generarFaltantes() {
        try {
            for (Venta venta : ventaRepository.findAll()) {
                if (!comisionRepository.existsByVentaId(venta.getId())) {
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
        Totales totales = totales();
        return comisionRepository.findAllByOrderByIdDesc().stream().map(c -> aDto(c, totales)).toList();
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
        Totales totales = totales();
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal devengado = BigDecimal.ZERO;
        BigDecimal entregado = BigDecimal.ZERO;
        BigDecimal porEntregar = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        long retrasadas = 0;
        BigDecimal montoRetrasado = BigDecimal.ZERO;
        LocalDate hoy = LocalDate.now();
        for (VentaComision c : comisionRepository.findAll()) {
            BigDecimal vencido = totales.vencido(c.getId(), hoy);
            if (vencido.signum() > 0) {
                retrasadas++;
                montoRetrasado = montoRetrasado.add(vencido);
            }
            if (c.isCancelada()) continue;
            BigDecimal dev = totales.devengado(c.getId());
            BigDecimal ent = totales.entregado(c.getId());
            cantidad++;
            total = total.add(c.getMonto());
            devengado = devengado.add(dev);
            entregado = entregado.add(ent);
            porEntregar = porEntregar.add(dev.subtract(ent).max(BigDecimal.ZERO));
            pendiente = pendiente.add(c.getMonto().subtract(dev).max(BigDecimal.ZERO));
        }
        return new ComisionResumenDto(
                cantidad, total, devengado, entregado, porEntregar, pendiente, retrasadas, montoRetrasado);
    }

    /** Qué toca entregar al sábado indicado (null = el próximo sábado, hoy si es sábado). */
    @Transactional(readOnly = true)
    public ComisionesPorEntregarDto porEntregar(LocalDate fecha) {
        exigirAdminOLider();
        LocalDate sabado = fecha != null
                ? fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                : LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        Totales totales = totales();
        List<ComisionesPorEntregarDto.Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (VentaComision c : comisionRepository.findAllByOrderByIdDesc()) {
            BigDecimal alSabado = totales.devengadoHasta(c.getId(), sabado)
                    .subtract(totales.entregado(c.getId()))
                    .max(BigDecimal.ZERO);
            if (alSabado.signum() > 0) {
                items.add(new ComisionesPorEntregarDto.Item(aDto(c, totales), alSabado));
                total = total.add(alSabado);
            }
        }
        return new ComisionesPorEntregarDto(sabado, total, items);
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

        BigDecimal monto;
        BigDecimal porcentaje;
        if (request.monto() != null) {
            if (request.monto().signum() < 0) throw new ValidationException("El monto no puede ser negativo");
            monto = request.monto().setScale(2, RoundingMode.HALF_UP);
            porcentaje = comision.getBase().signum() == 0
                    ? BigDecimal.ZERO
                    : monto.multiply(CIEN).divide(comision.getBase(), 4, RoundingMode.HALF_UP);
        } else {
            if (request.porcentaje().signum() < 0 || request.porcentaje().compareTo(CIEN) > 0) {
                throw new ValidationException("El porcentaje debe estar entre 0 y 100");
            }
            porcentaje = request.porcentaje().setScale(4, RoundingMode.HALF_UP);
            monto = porcentajeDe(comision.getBase(), porcentaje);
        }

        BigDecimal entregado = totales().entregado(comision.getId());
        if (monto.compareTo(entregado) < 0) {
            throw new ConflictException("La comisión no puede ser menor a lo ya entregado ($" + entregado + ")");
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

    public ComisionDetalleDto cancelar(Long id) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        comision.setCancelada(true);
        return detalle(comision);
    }

    public ComisionDetalleDto reactivar(Long id) {
        exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);
        comision.setCancelada(false);
        if (comision.getVenta() != null) reconstruirDevengos(comision, comision.getVenta());
        return detalle(comision);
    }

    /** Registra una entrega al asesor. No puede pasar de lo ya ganado y aún no entregado ("se paga
     * hasta donde el cliente pagó"); puede ser menos (parcial). Crea el gasto "Comisiones". */
    public ComisionDetalleDto entregar(Long id, ComisionEntregaRequest request) {
        Usuario actual = exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);

        Totales totales = totales();
        BigDecimal porEntregar = totales.devengado(id).subtract(totales.entregado(id));
        if (request.monto().compareTo(porEntregar) > 0) {
            throw new ConflictException("Solo hay $" + porEntregar.max(BigDecimal.ZERO)
                    + " ganados por entregar en esta comisión; el cliente no ha pagado más");
        }

        LocalDate fecha = request.fecha() != null ? request.fecha() : LocalDate.now();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        String concepto = "Comisión · " + comision.getCliente()
                + (comision.getVenta() != null && comision.getVenta().getNumero() != null
                        ? " (venta #" + comision.getVenta().getNumero() + ")"
                        : "");
        Gasto gasto = gastoRepository.save(new Gasto(
                concepto, OrigenGasto.COMISION, tipoGastoComisiones(), fecha, request.monto(), actual));
        entregaRepository.save(new VentaComisionEntrega(comision, fecha, request.monto(), notas, gasto, actual));
        return detalle(comision);
    }

    /** Anula una entrega y borra el gasto que generó. */
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

    /** Borra y vuelve a calcular lo ganado: la comisión completa en la fecha de venta si es una
     * exhibición, o la mitad de cada abono (en orden) hasta completar el monto. */
    private void reconstruirDevengos(VentaComision comision, Venta venta) {
        devengoRepository.deleteByComisionId(comision.getId());
        devengoRepository.flush();

        if (comision.getMonto().signum() <= 0) return;

        if (comision.getModalidad() == ModalidadComision.UNA_EXHIBICION) {
            devengoRepository.save(new VentaComisionDevengo(
                    comision, null, venta.getFechaVenta(), sabadoSiguiente(venta.getFechaVenta()), comision.getMonto()));
            return;
        }

        BigDecimal restante = comision.getMonto();
        for (PagoVenta pago : pagoVentaRepository.findByVentaIdOrderByFechaAscIdAsc(venta.getId())) {
            if (restante.signum() <= 0) break;
            BigDecimal mitad = pago.getMonto().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            BigDecimal aporte = mitad.min(restante);
            if (aporte.signum() <= 0) continue;
            devengoRepository.save(
                    new VentaComisionDevengo(comision, pago, pago.getFecha(), sabadoSiguiente(pago.getFecha()), aporte));
            restante = restante.subtract(aporte);
        }
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

    private static BigDecimal porcentajeDe(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- DTOs

    /** Sumas por comisión, calculadas de una sola vez (evita una consulta por fila). */
    private final class Totales {
        private final Map<Long, List<VentaComisionDevengo>> devengos = new HashMap<>();
        private final Map<Long, BigDecimal> entregado = new HashMap<>();
        private final Map<Long, BigDecimal> abonadoPorVenta = new HashMap<>();
        private final Map<Long, FinanzasService.CobroVenta> cobros = finanzasService.cobroPorVenta();

        Totales() {
            for (PagoVenta p : pagoVentaRepository.findAll()) {
                abonadoPorVenta.merge(p.getVenta().getId(), p.getMonto(), BigDecimal::add);
            }
            for (VentaComisionDevengo d : devengoRepository.findAllByOrderByFechaEntregaAscIdAsc()) {
                devengos.computeIfAbsent(d.getComision().getId(), k -> new ArrayList<>()).add(d);
            }
            for (VentaComisionEntrega e : entregaRepository.findAll()) {
                entregado.merge(e.getComision().getId(), e.getMonto(), BigDecimal::add);
            }
        }

        BigDecimal devengado(Long id) {
            return devengos.getOrDefault(id, List.of()).stream()
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal devengadoHasta(Long id, LocalDate fecha) {
            return devengos.getOrDefault(id, List.of()).stream()
                    .filter(d -> !d.getFechaEntrega().isAfter(fecha))
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal entregado(Long id) {
            return entregado.getOrDefault(id, BigDecimal.ZERO);
        }

        FinanzasService.CobroVenta cobroDeVenta(Long ventaId) {
            return ventaId == null
                    ? FinanzasService.CobroVenta.NINGUNO
                    : cobros.getOrDefault(ventaId, FinanzasService.CobroVenta.NINGUNO);
        }

        BigDecimal abonadoDeVenta(Long ventaId) {
            return ventaId == null ? BigDecimal.ZERO : abonadoPorVenta.getOrDefault(ventaId, BigDecimal.ZERO);
        }

        /** Lo ganado cuyo sábado de entrega ya pasó (anterior a hoy) y todavía no se entrega. */
        BigDecimal vencido(Long id, LocalDate hoy) {
            BigDecimal alCorte = devengos.getOrDefault(id, List.of()).stream()
                    .filter(d -> d.getFechaEntrega().isBefore(hoy))
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return alCorte.subtract(entregado(id)).max(BigDecimal.ZERO);
        }

        /** Primer sábado en que lo ganado supera lo ya entregado (los devengos van por fecha). */
        LocalDate proximaEntrega(Long id) {
            BigDecimal yaEntregado = entregado(id);
            BigDecimal acumulado = BigDecimal.ZERO;
            for (VentaComisionDevengo d : devengos.getOrDefault(id, List.of())) {
                acumulado = acumulado.add(d.getMonto());
                if (acumulado.compareTo(yaEntregado) > 0) return d.getFechaEntrega();
            }
            return null;
        }
    }

    private Totales totales() {
        return new Totales();
    }

    private ComisionDto aDto(VentaComision c, Totales totales) {
        BigDecimal devengado = totales.devengado(c.getId());
        BigDecimal entregado = totales.entregado(c.getId());
        BigDecimal porEntregar = devengado.subtract(entregado).max(BigDecimal.ZERO);

        EstadoComision estado;
        if (c.isCancelada()) estado = EstadoComision.CANCELADA;
        else if (c.getMonto().signum() > 0 && entregado.compareTo(c.getMonto()) >= 0) estado = EstadoComision.PAGADA;
        else if (entregado.signum() > 0) estado = EstadoComision.PARCIAL;
        else if (devengado.signum() > 0) estado = EstadoComision.ACUMULANDO;
        else estado = EstadoComision.PENDIENTE;

        BigDecimal vencido = totales.vencido(c.getId(), LocalDate.now());
        Venta venta = c.getVenta();
        FinanzasService.CobroVenta cobro = totales.cobroDeVenta(venta != null ? venta.getId() : null);
        boolean externo = c.getAsesorExterno() != null;
        String asesor = externo ? c.getAsesorExterno().getNombre() : c.getUsuarioAsesor().getNombre();
        return new ComisionDto(
                c.getId(),
                venta != null ? venta.getId() : null,
                venta != null ? venta.getNumero() : c.getVentaNumero(),
                venta == null,
                c.getCliente(),
                asesor,
                externo,
                c.getBase(),
                venta != null ? venta.getMensualidad() : null,
                totales.abonadoDeVenta(venta != null ? venta.getId() : null),
                cobro.mensualidadesAtrasadas(),
                cobro.atrasoMonto(),
                estadoPagoMes(cobro),
                cobro.esperadoMes(),
                cobro.recibidoMes(),
                cobro.fechaMes(),
                c.getPorcentaje(),
                c.getMonto(),
                c.isMontoManual(),
                c.getModalidad(),
                estado,
                c.isCancelada(),
                devengado,
                entregado,
                porEntregar,
                porEntregar.signum() > 0 ? totales.proximaEntrega(c.getId()) : null,
                vencido.signum() > 0,
                vencido,
                c.getFechaCreacion());
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
        Totales totales = totales();
        List<ComisionDetalleDto.Devengo> devengos = devengoRepository
                .findByComisionIdOrderByFechaEntregaAscIdAsc(comision.getId())
                .stream()
                .map(d -> new ComisionDetalleDto.Devengo(d.getFechaOrigen(), d.getFechaEntrega(), d.getMonto()))
                .toList();
        List<ComisionDetalleDto.Entrega> entregas = entregaRepository
                .findByComisionIdOrderByFechaDescIdDesc(comision.getId())
                .stream()
                .map(e -> new ComisionDetalleDto.Entrega(
                        e.getId(),
                        e.getFecha(),
                        e.getMonto(),
                        e.getNotas(),
                        e.getGasto() != null ? e.getGasto().getId() : null,
                        e.getRegistradaPor().getNombre(),
                        e.getFechaCreacion()))
                .toList();
        return new ComisionDetalleDto(aDto(comision, totales), devengos, entregas);
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
