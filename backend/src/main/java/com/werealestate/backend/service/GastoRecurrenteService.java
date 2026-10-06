package com.werealestate.backend.service;

import com.werealestate.backend.dto.GastoDto;
import com.werealestate.backend.dto.NominaPagadaDto;
import com.werealestate.backend.dto.GastoRecurrenteDto;
import com.werealestate.backend.dto.GastoRecurrentePagoDto;
import com.werealestate.backend.dto.GastoRecurrenteRequest;
import com.werealestate.backend.dto.GastoResumenDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.FrecuenciaGasto;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.GastoRecurrente;
import com.werealestate.backend.model.GastoRecurrentePago;
import com.werealestate.backend.model.OrigenGasto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.GastoRecurrentePagoRepository;
import com.werealestate.backend.repository.GastoRecurrenteRepository;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gastos recurrentes (renta, luz, agua, nómina...). Cada uno genera sus vencimientos (con 30 días de
 * anticipación) como pagos pendientes; al pagar uno se captura el monto real, la fecha y el
 * comprobante opcional, y se crea el gasto correspondiente. Un pendiente cuya fecha ya pasó está
 * vencido. Crear, editar y eliminar los recurrentes es solo de admin; pagar, omitir o deshacer un
 * pago también lo puede hacer Administración (líder de área).
 */
@Service
@Transactional
public class GastoRecurrenteService {

    private static final Logger log = LoggerFactory.getLogger(GastoRecurrenteService.class);
    private static final int DIAS_DE_ANTICIPACION = 30;

    private final GastoRecurrenteRepository recurrenteRepository;
    private final GastoRecurrentePagoRepository pagoRepository;
    private final GastoRepository gastoRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final GastoService gastoService;
    private final CurrentUserProvider currentUserProvider;

    public GastoRecurrenteService(
            GastoRecurrenteRepository recurrenteRepository,
            GastoRecurrentePagoRepository pagoRepository,
            GastoRepository gastoRepository,
            PagoVentaRepository pagoVentaRepository,
            GastoService gastoService,
            CurrentUserProvider currentUserProvider) {
        this.recurrenteRepository = recurrenteRepository;
        this.pagoRepository = pagoRepository;
        this.gastoRepository = gastoRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.gastoService = gastoService;
        this.currentUserProvider = currentUserProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generarAlArrancar() {
        try {
            generarPendientes();
        } catch (RuntimeException e) {
            log.warn("No se pudieron generar los vencimientos de los gastos recurrentes", e);
        }
    }

    // ---------------------------------------------------------------- catálogo

    public List<GastoRecurrenteDto> listar() {
        exigirAdminOLider();
        generarPendientes();
        List<GastoRecurrentePago> sinPagar = pagoRepository.findSinPagar();
        return recurrenteRepository.findAllByOrderByNombreAsc().stream()
                .map(g -> {
                    List<GastoRecurrentePago> suyos = sinPagar.stream()
                            .filter(p -> p.getRecurrente().getId().equals(g.getId()))
                            .toList();
                    return GastoRecurrenteDto.from(
                            g, suyos.isEmpty() ? null : suyos.get(0).getFechaVencimiento(), suyos.size());
                })
                .toList();
    }

    public GastoRecurrenteDto crear(GastoRecurrenteRequest request) {
        exigirAdmin();
        validar(request);
        GastoRecurrente g = recurrenteRepository.save(new GastoRecurrente(
                request.nombre().trim(),
                request.montoEstimado(),
                request.frecuencia(),
                diaDe(request),
                dia2De(request),
                request.primerVencimiento()));
        generarPendientes();
        return dto(g);
    }

    public GastoRecurrenteDto editar(Long id, GastoRecurrenteRequest request) {
        exigirAdmin();
        validar(request);
        GastoRecurrente g = obtenerRecurrente(id);
        exigirNoNomina(g);
        g.actualizar(
                request.nombre().trim(),
                request.montoEstimado(),
                request.frecuencia(),
                diaDe(request),
                dia2De(request),
                request.primerVencimiento());
        if (request.activo() != null) g.setActivo(request.activo());
        // Lo ya pagado u omitido se respeta; lo demás se vuelve a calcular con las condiciones nuevas.
        pagoRepository.borrarSinPagarDe(g.getId());
        pagoRepository.flush();
        generarPendientes();
        return dto(g);
    }

    /** Solo si nunca se pagó nada (si no, hay que desactivarlo para conservar el historial). */
    public void eliminar(Long id) {
        exigirAdmin();
        GastoRecurrente g = obtenerRecurrente(id);
        exigirNoNomina(g);
        if (pagoRepository.existsByRecurrenteIdAndGastoIsNotNull(id)) {
            throw new ConflictException(
                    "No se puede eliminar \"" + g.getNombre() + "\": ya tiene pagos registrados. Desactívalo para conservar el historial.");
        }
        recurrenteRepository.delete(g);
    }

    // ---------------------------------------------------------------- nómina por usuario

    /**
     * Deja la nómina semanal de un usuario reflejada como un gasto recurrente SEMANAL (cada sábado,
     * desde el primer sábado a partir de su fecha de inicio). Sin sueldo o con el usuario inactivo
     * se desactiva y se quitan los sábados sin pagar; lo ya pagado queda en el historial. Lo llama
     * UsuarioService al configurar la nómina o al cambiar el usuario.
     */
    public void sincronizarNomina(Usuario usuario) {
        GastoRecurrente g = recurrenteRepository.findByUsuarioId(usuario.getId()).orElse(null);
        BigDecimal sueldo = usuario.getNominaSemanal();
        boolean aplica = usuario.isActivo() && sueldo != null && sueldo.signum() > 0;

        if (!aplica) {
            if (g != null) {
                g.setActivo(false);
                pagoRepository.borrarSinPagarDe(g.getId());
            }
            return;
        }

        LocalDate desde = usuario.getNominaDesde() != null ? usuario.getNominaDesde() : LocalDate.now();
        LocalDate primerSabado = desde.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        String nombre = "Nómina · " + usuario.getNombre();
        if (g == null) {
            g = new GastoRecurrente(nombre, sueldo, FrecuenciaGasto.SEMANAL, null, null, primerSabado);
            g.setUsuario(usuario);
            g = recurrenteRepository.save(g);
        } else {
            g.actualizar(nombre, sueldo, FrecuenciaGasto.SEMANAL, null, null, primerSabado);
            g.setActivo(true);
        }
        pagoRepository.borrarSinPagarDe(g.getId());
        pagoRepository.flush();
        generarPendientes();
    }

    /** ¿Tiene pagos de nómina ya registrados? (para no borrar a un usuario con ese historial). */
    public boolean tieneNominaPagada(Long usuarioId) {
        return recurrenteRepository
                .findByUsuarioId(usuarioId)
                .map(g -> pagoRepository.existsByRecurrenteIdAndGastoIsNotNull(g.getId()))
                .orElse(false);
    }

    /** Quita la nómina de un usuario que se elimina (solo si nunca se le pagó nada). */
    public void eliminarNomina(Long usuarioId) {
        recurrenteRepository.findByUsuarioId(usuarioId).ifPresent(recurrenteRepository::delete);
    }

    /** Paga de una vez la nómina de un sábado: todas las líneas de nómina sin pagar de ese día, con su
     * monto estimado y la fecha de pago indicada (null = hoy). Se pueden ajustar una por una antes. */
    public NominaPagadaDto pagarNomina(LocalDate sabado, LocalDate fechaPago) {
        Usuario actual = exigirAdminOLider();
        if (sabado == null) throw new ValidationException("Indica el sábado");
        int pagados = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (GastoRecurrentePago pago : pagoRepository.findSinPagar()) {
            if (pago.getRecurrente().getUsuario() == null || !pago.getFechaVencimiento().equals(sabado)) continue;
            Gasto gasto = gastoService.crearGasto(
                    pago.getRecurrente().getNombre(),
                    OrigenGasto.RECURRENTE,
                    fechaPago != null ? fechaPago : LocalDate.now(),
                    pago.getMontoEstimado(),
                    null,
                    actual);
            pago.setGasto(gasto);
            pagados++;
            total = total.add(pago.getMontoEstimado());
        }
        if (pagados == 0) throw new ConflictException("No hay nómina pendiente en ese sábado");
        return new NominaPagadaDto(pagados, total);
    }

    private static void exigirNoNomina(GastoRecurrente g) {
        if (g.getUsuario() != null) {
            throw new ConflictException(
                    g.getNombre() + " es una nómina: se administra desde Usuarios (campo de nómina semanal)");
        }
    }

    // ---------------------------------------------------------------- pagos

    /** Los vencimientos sin pagar (pendientes y vencidos), del más próximo al más lejano. */
    public List<GastoRecurrentePagoDto> pagosSinPagar() {
        exigirAdminOLider();
        generarPendientes();
        LocalDate hoy = LocalDate.now();
        return pagoRepository.findSinPagar().stream()
                .map(p -> new GastoRecurrentePagoDto(
                        p.getId(),
                        p.getRecurrente().getId(),
                        p.getRecurrente().getNombre(),
                        p.getFechaVencimiento(),
                        p.getMontoEstimado(),
                        p.getFechaVencimiento().isBefore(hoy) ? "VENCIDO" : "PENDIENTE",
                        p.getRecurrente().getUsuario() != null))
                .toList();
    }

    /** Paga un vencimiento con el monto real, la fecha y el comprobante opcional; crea su gasto. */
    public GastoDto pagar(Long pagoId, BigDecimal monto, LocalDate fecha, MultipartFile ticket) {
        Usuario actual = exigirAdminOLider();
        GastoRecurrentePago pago = obtenerPago(pagoId);
        if (pago.getGasto() != null) throw new ConflictException("Este vencimiento ya está pagado");
        Gasto gasto = gastoService.crearGasto(
                pago.getRecurrente().getNombre(),
                OrigenGasto.RECURRENTE,
                fecha != null ? fecha : LocalDate.now(),
                monto,
                ticket,
                actual);
        pago.setGasto(gasto);
        pago.setOmitido(false);
        return GastoDto.from(gasto);
    }

    /** Salta ese periodo (no se pagará): deja de aparecer como pendiente o vencido. */
    public void omitir(Long pagoId) {
        exigirAdminOLider();
        GastoRecurrentePago pago = obtenerPago(pagoId);
        if (pago.getGasto() != null) throw new ConflictException("Este vencimiento ya está pagado");
        pago.setOmitido(true);
    }

    /** Deshace un pago: borra su gasto (y comprobante) y el vencimiento vuelve a quedar sin pagar. */
    public void deshacerPago(Long gastoId) {
        exigirAdminOLider();
        GastoRecurrentePago pago = pagoRepository
                .findByGastoId(gastoId)
                .orElseThrow(() -> new ResourceNotFoundException("Ese gasto no es el pago de un gasto recurrente"));
        Gasto gasto = pago.getGasto();
        pago.setGasto(null);
        pagoRepository.flush();
        gastoService.eliminarGasto(gasto);
    }

    // ---------------------------------------------------------------- resumen

    /** Del mes ("yyyy-MM"; null = el actual): abonos recibidos, gastos hechos y la diferencia. */
    @Transactional(readOnly = false)
    public GastoResumenDto resumen(String mes) {
        exigirAdminOLider();
        generarPendientes();
        YearMonth ym;
        try {
            ym = mes == null || mes.isBlank() ? YearMonth.now() : YearMonth.parse(mes.trim());
        } catch (RuntimeException e) {
            throw new ValidationException("Mes inválido: usa el formato yyyy-MM");
        }
        BigDecimal ingresos = pagoVentaRepository.findAll().stream()
                .filter(p -> YearMonth.from(p.getFecha()).equals(ym))
                .map(PagoVenta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gastado = gastoRepository.findAll().stream()
                .filter(g -> YearMonth.from(g.getFecha()).equals(ym))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate hoy = LocalDate.now();
        BigDecimal pendiente = BigDecimal.ZERO;
        BigDecimal vencido = BigDecimal.ZERO;
        int vencidos = 0;
        for (GastoRecurrentePago p : pagoRepository.findSinPagar()) {
            pendiente = pendiente.add(p.getMontoEstimado());
            if (p.getFechaVencimiento().isBefore(hoy)) {
                vencido = vencido.add(p.getMontoEstimado());
                vencidos++;
            }
        }
        return new GastoResumenDto(ym.toString(), ingresos, gastado, ingresos.subtract(gastado), pendiente, vencido, vencidos);
    }

    // ---------------------------------------------------------------- vencimientos

    /** Crea los vencimientos que falten, hasta 30 días adelante, de los recurrentes activos. */
    void generarPendientes() {
        LocalDate hasta = LocalDate.now().plusDays(DIAS_DE_ANTICIPACION);
        for (GastoRecurrente g : recurrenteRepository.findByActivoTrue()) {
            for (LocalDate fecha : vencimientos(g, hasta)) {
                if (!pagoRepository.existsByRecurrenteIdAndFechaVencimiento(g.getId(), fecha)) {
                    pagoRepository.save(new GastoRecurrentePago(g, fecha, g.getMontoEstimado()));
                }
            }
        }
    }

    /** Las fechas de vencimiento desde el primer vencimiento hasta la fecha dada (inclusive). */
    static List<LocalDate> vencimientos(GastoRecurrente g, LocalDate hasta) {
        TreeSet<LocalDate> fechas = new TreeSet<>();
        LocalDate primero = g.getPrimerVencimiento();
        if (primero.isAfter(hasta)) return List.of();

        if (g.getFrecuencia() == FrecuenciaGasto.SEMANAL) {
            for (LocalDate d = primero; !d.isAfter(hasta); d = d.plusDays(7)) fechas.add(d);
            return new ArrayList<>(fechas);
        }

        int paso = switch (g.getFrecuencia()) {
            case BIMESTRAL -> 2;
            case ANUAL -> 12;
            default -> 1;
        };
        for (YearMonth ym = YearMonth.from(primero); !ym.atDay(1).isAfter(hasta); ym = ym.plusMonths(paso)) {
            agregar(fechas, ym, g.getDia(), primero, hasta);
            if (g.getFrecuencia() == FrecuenciaGasto.QUINCENAL) agregar(fechas, ym, g.getDia2(), primero, hasta);
        }
        return new ArrayList<>(fechas);
    }

    private static void agregar(TreeSet<LocalDate> fechas, YearMonth ym, Integer dia, LocalDate desde, LocalDate hasta) {
        if (dia == null) return;
        LocalDate fecha = ym.atDay(Math.min(dia, ym.lengthOfMonth()));
        if (!fecha.isBefore(desde) && !fecha.isAfter(hasta)) fechas.add(fecha);
    }

    // ---------------------------------------------------------------- utilidades

    private void validar(GastoRecurrenteRequest r) {
        if (r.frecuencia() != FrecuenciaGasto.SEMANAL && r.dia() == null) {
            throw new ValidationException("Indica el día del mes del vencimiento");
        }
        if (r.frecuencia() == FrecuenciaGasto.QUINCENAL) {
            if (r.dia2() == null) throw new ValidationException("La frecuencia quincenal necesita dos días del mes");
            if (r.dia().equals(r.dia2())) throw new ValidationException("Los dos días de un pago quincenal deben ser distintos");
        }
    }

    private static Integer diaDe(GastoRecurrenteRequest r) {
        return r.frecuencia() == FrecuenciaGasto.SEMANAL ? null : r.dia();
    }

    private static Integer dia2De(GastoRecurrenteRequest r) {
        return r.frecuencia() == FrecuenciaGasto.QUINCENAL ? r.dia2() : null;
    }

    private GastoRecurrenteDto dto(GastoRecurrente g) {
        List<GastoRecurrentePago> sinPagar =
                pagoRepository.findSinPagar().stream().filter(p -> p.getRecurrente().getId().equals(g.getId())).toList();
        return GastoRecurrenteDto.from(g, sinPagar.isEmpty() ? null : sinPagar.get(0).getFechaVencimiento(), sinPagar.size());
    }

    private GastoRecurrente obtenerRecurrente(Long id) {
        return recurrenteRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto recurrente no encontrado"));
    }

    private GastoRecurrentePago obtenerPago(Long id) {
        return pagoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vencimiento no encontrado"));
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar gastos");
        }
        return actual;
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede crear, editar o eliminar gastos recurrentes");
        }
        return actual;
    }
}
