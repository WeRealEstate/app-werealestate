package com.werealestate.backend.service;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        VentaComision comision = comisionRepository.findByVentaIdAndSecundariaFalse(venta.getId()).orElse(null);
        if (comision == null) {
            BigDecimal base = valorDeVenta(venta.getId());
            BigDecimal pct = porcentaje != null ? porcentaje.setScale(4, RoundingMode.HALF_UP) : PORCENTAJE_DEFECTO;
            BigDecimal monto = porcentajeDe(base, pct);
            comision = new VentaComision(
                    venta, venta.getCliente(), venta.getUsuarioAsesor(), venta.getAsesorExterno(), base,
                    pct, monto, modalidadPara(venta, monto));
            if (ventaNueva) comision.marcarReglaNueva();
            // Un % distinto al 5% por defecto se marca como elegido a mano (✎ en Finanzas).
            if (pct.compareTo(PORCENTAJE_DEFECTO) != 0) comision.setMontoYPorcentaje(monto, pct, true);
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

        // Copropietario de la venta (un segundo asesor): su propia comisión sobre el mismo valor.
        VentaComision secundaria = comisionRepository.findByVentaIdAndSecundariaTrue(venta.getId()).orElse(null);
        if (secundaria == null && ventaNueva && (coasesorUsuario != null || coasesorExterno != null)) {
            BigDecimal base = valorDeVenta(venta.getId());
            BigDecimal pct = coasesorPorcentaje != null
                    ? coasesorPorcentaje.setScale(4, RoundingMode.HALF_UP)
                    : PORCENTAJE_DEFECTO;
            BigDecimal monto = porcentajeDe(base, pct);
            secundaria = new VentaComision(
                    venta, venta.getCliente(), coasesorUsuario, coasesorExterno, base, pct, monto,
                    modalidadPara(venta, monto));
            secundaria.marcarReglaNueva();
            secundaria.marcarSecundaria();
            if (pct.compareTo(PORCENTAJE_DEFECTO) != 0) secundaria.setMontoYPorcentaje(monto, pct, true);
            secundaria = comisionRepository.save(secundaria);
        } else if (secundaria != null && !secundaria.isCancelada()) {
            secundaria.setCliente(venta.getCliente());
        }
        if (secundaria != null && !secundaria.isCancelada()) {
            reconstruirDevengos(secundaria, venta);
        }
    }

    /** La venta se va a eliminar: su comisión se queda en el historial ("Venta eliminada"), con el
     * número de venta que tenía. Lo ya ganado sigue pudiéndose entregar; deja de acumular. */
    public void desvincularVenta(Venta venta) {
        comisionRepository.findAllByVentaId(venta.getId()).forEach(c -> c.desvincularVenta(venta.getNumero()));
        comisionRepository.flush();
    }

    /** Comisiones de las ventas que ya existían antes de este módulo (o que quedaron sin una). */
    @EventListener(ApplicationReadyEvent.class)
    public void generarFaltantes() {
        try {
            for (Venta venta : ventaRepository.findAll()) {
                if (!comisionRepository.existsByVentaIdAndSecundariaFalse(venta.getId())) {
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
            if (!totales.bloqueada(c.getId())) porEntregar = porEntregar.add(dev.subtract(ent).max(BigDecimal.ZERO));
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
            if (porcentaje.compareTo(PORCENTAJE_MAXIMO) > 0) {
                throw new ValidationException("La comisión no puede pasar de " + PORCENTAJE_MAXIMO.toPlainString() + "% del valor de la venta");
            }
        } else {
            if (request.porcentaje().signum() < 0 || request.porcentaje().compareTo(PORCENTAJE_MAXIMO) > 0) {
                throw new ValidationException("El porcentaje debe estar entre 0 y " + PORCENTAJE_MAXIMO.toPlainString());
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
     * hasta donde el cliente pagó"); puede ser menos (parcial). Crea el gasto "Comisiones" y guarda lo
     * que necesita su ticket: IVA, factura y forma de pago elegidos, cómo estaba la comisión y qué abonos
     * cubre (las entregas cubren lo ganado en orden, ver {@link #repartirEntrega}). */
    public ComisionDetalleDto entregar(Long id, ComisionEntregaRequest request) {
        Usuario actual = exigirAdminOLider();
        VentaComision comision = obtenerEntidad(id);

        Totales totales = totales();
        if (totales.bloqueada(id)) {
            throw new ConflictException("El contrato de esta venta no está firmado: la comisión se acumula, "
                    + "pero no se puede entregar hasta registrar la firma en la venta");
        }
        BigDecimal yaEntregado = totales.entregado(id);
        BigDecimal porEntregar = totales.devengado(id).subtract(yaEntregado);
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

        List<Porcion> ganado = devengoRepository.findByComisionIdOrderByFechaEntregaAscIdAsc(id).stream()
                .map(d -> new Porcion(d.getFechaOrigen(), conceptoDe(d), d.getMonto()))
                .toList();
        for (Porcion p : repartirEntrega(ganado, yaEntregado, request.monto())) {
            entregaAbonoRepository.save(new VentaComisionEntregaAbono(entrega, p.fecha(), p.concepto(), p.monto()));
        }
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
            Totales totales = totales();
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
                .sorted(java.util.Comparator.comparing(VentaComisionEntrega::getId))
                .toList();
        List<Porcion> ganado = devengoRepository.findByComisionIdOrderByFechaEntregaAscIdAsc(comisionId).stream()
                .map(d -> new Porcion(d.getFechaOrigen(), conceptoDe(d), d.getMonto()))
                .toList();
        for (VentaComisionEntrega e : afectadas) {
            e.ajustarEntregadoAcumulado(diferencia);
            if (!e.tieneTicket()) continue;
            entregaAbonoRepository.deleteByEntregaId(e.getId());
            entregaAbonoRepository.flush();
            BigDecimal yaEntregado = e.getEntregadoAcumulado().subtract(e.getMonto());
            for (Porcion p : repartirEntrega(ganado, yaEntregado, e.getMonto())) {
                entregaAbonoRepository.save(new VentaComisionEntregaAbono(e, p.fecha(), p.concepto(), p.monto()));
            }
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
                venta != null ? venta.getNumero() : comision.getVentaNumero(),
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

        BigDecimal entregado = totales().entregado(id);
        if (request.monto().compareTo(entregado) > 0) {
            throw new ConflictException("Solo hay $" + entregado.max(BigDecimal.ZERO)
                    + " entregados que se puedan devolver en esta comisión");
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
            BigDecimal mitad = pago.getMonto().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            BigDecimal aporte = mitad.min(restante);
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
     * esté firmado, lo acumulado no se puede entregar (ver Totales.bloqueada).
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
            case MENSUALIDAD, APORTACION -> pago.getMonto().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
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

    private static BigDecimal porcentajeDe(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- DTOs

    /** Sumas por comisión, calculadas de una sola vez (evita una consulta por fila). */
    private final class Totales {
        private final Map<Long, List<VentaComisionDevengo>> devengos = new HashMap<>();
        private final Map<Long, BigDecimal> entregado = new HashMap<>();
        private final Map<Long, BigDecimal> devuelto = new HashMap<>();
        /** Comisiones con la regla nueva cuyo contrato aún no está firmado: se acumulan pero no se entregan. */
        private final java.util.Set<Long> bloqueadas = new java.util.HashSet<>();
        private final Map<Long, BigDecimal> abonadoPorVenta = new HashMap<>();
        private final Map<Long, FinanzasService.CobroVenta> cobros = finanzasService.cobroPorVenta();

        Totales() {
            for (PagoVenta p : pagoVentaRepository.findAllVigentes()) {
                abonadoPorVenta.merge(p.getVenta().getId(), p.getMonto(), BigDecimal::add);
            }
            for (VentaComisionDevengo d : devengoRepository.findAllByOrderByFechaEntregaAscIdAsc()) {
                devengos.computeIfAbsent(d.getComision().getId(), k -> new ArrayList<>()).add(d);
            }
            for (VentaComisionEntrega e : entregaRepository.findAll()) {
                entregado.merge(e.getComision().getId(), e.getMonto(), BigDecimal::add);
            }
            for (VentaComisionDevolucion d : devolucionRepository.findAll()) {
                devuelto.merge(d.getComision().getId(), d.getMonto(), BigDecimal::add);
            }
            for (VentaComision c : comisionRepository.findAll()) {
                if (c.isReglaNueva() && c.getVenta() != null && !c.getVenta().isContratoFirmado()) bloqueadas.add(c.getId());
            }
        }

        BigDecimal devengado(Long id) {
            return devengos.getOrDefault(id, List.of()).stream()
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        boolean bloqueada(Long id) {
            return bloqueadas.contains(id);
        }

        BigDecimal devengadoHasta(Long id, LocalDate fecha) {
            if (bloqueada(id)) return BigDecimal.ZERO;
            return devengos.getOrDefault(id, List.of()).stream()
                    .filter(d -> !d.getFechaEntrega().isAfter(fecha))
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            return ventaId == null ? BigDecimal.ZERO : abonadoPorVenta.getOrDefault(ventaId, BigDecimal.ZERO);
        }

        /** Lo ganado cuyo sábado de entrega ya pasó (anterior a hoy) y todavía no se entrega. */
        BigDecimal vencido(Long id, LocalDate hoy) {
            if (bloqueada(id)) return BigDecimal.ZERO;
            BigDecimal alCorte = devengos.getOrDefault(id, List.of()).stream()
                    .filter(d -> d.getFechaEntrega().isBefore(hoy))
                    .map(VentaComisionDevengo::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return alCorte.subtract(entregado(id)).max(BigDecimal.ZERO);
        }

        /** Primer sábado en que lo ganado supera lo ya entregado (los devengos van por fecha). */
        LocalDate proximaEntrega(Long id) {
            if (bloqueada(id)) return null;
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
        BigDecimal porEntregar = totales.bloqueada(c.getId())
                ? BigDecimal.ZERO
                : devengado.subtract(entregado).max(BigDecimal.ZERO);

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
                        e.getFechaCreacion(),
                        e.getConIva(),
                        e.getConFactura(),
                        e.getFormaPago(),
                        e.tieneTicket()))
                .toList();
        List<ComisionDetalleDto.Devolucion> devoluciones = devolucionRepository
                .findByComisionIdOrderByFechaAscIdAsc(comision.getId())
                .stream()
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
