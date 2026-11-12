package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.dto.VentaDeshacerPausaRequest;
import com.werealestate.backend.dto.VentaPausaRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.model.VentaPausa;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa de mensualidades (decisiones del usuario, 2026-11-12): a un cliente que deja de pagar un tiempo se le mueve la
 * fecha de su mensualidad N a una posterior y todo lo que sigue (mensualidades y aportaciones) se recorre mes a mes
 * desde ahí, con el día de la fecha nueva como día de pago; el plazo sigue completo y los meses de la pausa no tienen
 * renglón ni cuentan como atraso. Solo se mueve hacia adelante, una mensualidad sin pagos y sin pagos posteriores
 * (los recibos guardan el mes que cubren). Puede haber varias; se deshace la más reciente. Solo admin y
 * administración, con su contraseña y un motivo. El calendario lo arma CalendarioPagos con las pausas vigentes del
 * plan; las aportaciones se guardan ya en su mes recorrido para que sus recibos las sigan encontrando.
 */
@Service
@Transactional
public class PausaMensualidadService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final ComisionService comisionService;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public PausaMensualidadService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            PagoVentaRepository pagoVentaRepository,
            ComisionService comisionService,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.comisionService = comisionService;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /** Mueve la mensualidad {@code numero} del plan vigente a {@code fecha} y recorre todo lo que sigue. */
    public void pausar(Long ventaId, VentaPausaRequest request) {
        Usuario actual = autorizar(request.password());
        Venta venta = venta(ventaId);
        CalendarioPagos.Plan plan = CalendarioPagos.planVigente(venta);
        if (!plan.conPlazoPublico() || plan.plazoMeses() < 1) {
            throw new ValidationException("Esta venta no tiene mensualidades");
        }
        int numero = request.numero();
        List<CalendarioPagos.Pausa> pausas = plan.pausas();
        if (!pausas.isEmpty() && pausas.get(pausas.size() - 1).desde() > numero) {
            throw new ConflictException("Ya hay una pausa desde la mensualidad N.º " + pausas.get(pausas.size() - 1).desde()
                    + ": deshazla primero para mover una anterior");
        }
        Corte corte = corte(venta);
        int fila = corte.filaDe(numero);
        if (fila < 0) {
            throw new ValidationException("El plan vigente no tiene la mensualidad N.º " + numero);
        }
        LocalDate anterior = corte.conceptos().get(fila).fecha();
        LocalDate nueva = request.fecha();
        if (!nueva.isAfter(anterior)) {
            throw new ValidationException("La nueva fecha debe ser posterior al " + anterior.format(DMY));
        }
        corte.exigirSinPagosDesde(fila, "La mensualidad N.º " + numero
                + " o alguna posterior ya tiene pagos: solo se mueve una mensualidad sin pagos y sin pagos después");

        Long planId = CalendarioPagos.idDelPlanVigente(venta);
        long meses = ChronoUnit.MONTHS.between(YearMonth.from(anterior), YearMonth.from(nueva));
        moverAportaciones(venta.getId(), planId, YearMonth.from(anterior), meses);
        VentaPausa pausa = new VentaPausa(venta, planId, numero, anterior, nueva, request.motivo().trim(), actual);
        entityManager.persist(pausa);
        venta.agregarPausa(pausa);
        comisionService.sincronizar(venta);
    }

    /** Deshace la pausa más reciente del plan vigente: las fechas vuelven a como estaban antes de ella. */
    public void deshacer(Long ventaId, VentaDeshacerPausaRequest request) {
        Usuario actual = autorizar(request.password());
        Venta venta = venta(ventaId);
        Long planId = CalendarioPagos.idDelPlanVigente(venta);
        VentaPausa ultima = venta.getPausas().stream()
                .filter(p -> p.isVigente() && Objects.equals(p.getReestructuracionId(), planId))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new ConflictException("La venta no tiene pausas que deshacer"));
        Corte corte = corte(venta);
        int fila = corte.filaDe(ultima.getDesdeNumero());
        if (fila >= 0) {
            corte.exigirSinPagosDesde(fila, "La mensualidad N.º " + ultima.getDesdeNumero()
                    + " o alguna posterior ya tiene pagos: ya no se puede deshacer la pausa");
        }
        long meses = ChronoUnit.MONTHS.between(YearMonth.from(ultima.getFechaAnterior()), YearMonth.from(ultima.getFechaNueva()));
        moverAportaciones(venta.getId(), planId, YearMonth.from(ultima.getFechaNueva()), -meses);
        ultima.deshacer(actual, request.motivo().trim());
        comisionService.sincronizar(venta);
    }

    /** Las pausas vigentes del plan vigente, para la tabla de amortización (la última es la que se puede deshacer). */
    public static List<AmortizacionDto.Pausa> pausasVigentes(Venta venta) {
        Long planId = CalendarioPagos.idDelPlanVigente(venta);
        List<AmortizacionDto.Pausa> lista = new ArrayList<>();
        for (VentaPausa p : venta.getPausas()) {
            if (!p.isVigente() || !Objects.equals(p.getReestructuracionId(), planId)) continue;
            lista.add(new AmortizacionDto.Pausa(p.getDesdeNumero(), p.getFechaAnterior(), p.getFechaNueva(), p.getMotivo(),
                    p.getUsuario() != null ? p.getUsuario().getNombre() : null, p.getFechaRegistro()));
        }
        return lista;
    }

    /**
     * Recorre {@code meses} las aportaciones del plan desde el mes {@code desde} (inclusive). Se guardan en su mes
     * recorrido para que los recibos que las paguen las sigan ubicando. Hacia adelante se mueven de la más nueva a la
     * más vieja (y al regresar, al revés) para no chocar con la regla de una aportación por mes en cada plan.
     */
    private void moverAportaciones(Long ventaId, Long planId, YearMonth desde, long meses) {
        if (meses == 0) return;
        List<VentaAportacion> aMover = new ArrayList<>(ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(ventaId).stream()
                .filter(a -> Objects.equals(a.getReestructuracionId(), planId))
                .filter(a -> !YearMonth.of(a.getAnio(), a.getMes()).isBefore(desde))
                .toList());
        Comparator<VentaAportacion> porMes = Comparator.comparing(a -> YearMonth.of(a.getAnio(), a.getMes()));
        aMover.sort(meses > 0 ? porMes.reversed() : porMes);
        for (VentaAportacion a : aMover) {
            a.moverAMes(YearMonth.of(a.getAnio(), a.getMes()).plusMonths(meses));
            entityManager.flush();
        }
    }

    private Usuario autorizar(String password) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede mover fechas de pago");
        }
        if (password == null || !passwordEncoder.matches(password, actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        return actual;
    }

    private Venta venta(Long id) {
        return ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
    }

    /** El calendario de la venta hoy y su tabla mes con mes (la misma que se muestra), para ubicar renglones y pagos. */
    private Corte corte(Venta venta) {
        BigDecimal precio = ventaLoteRepository.findByVentaId(venta.getId()).stream()
                .map(VentaLote::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PagoVenta> pagos = pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId());
        List<CalendarioPagos.Concepto> conceptos = CalendarioPagos.conceptos(
                venta, precio, ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(venta.getId()));
        CalendarioPagos.EstadoDeCuenta cuenta = CalendarioPagos.estadoDeCuenta(conceptos, precio, pagos, LocalDate.now());
        return new Corte(conceptos, cuenta.mensual());
    }

    private record Corte(List<CalendarioPagos.Concepto> conceptos, List<AmortizacionDto.Fila> mensual) {

        /** Índice del renglón con ese número en el plan vigente (el último tramo); -1 si no hay. */
        int filaDe(int numero) {
            int tramo = conceptos.stream().mapToInt(CalendarioPagos.Concepto::tramo).max().orElse(0);
            for (int i = 0; i < conceptos.size(); i++) {
                CalendarioPagos.Concepto c = conceptos.get(i);
                if (c.tramo() == tramo && c.numero() != null && c.numero() == numero) return i;
            }
            return -1;
        }

        /** 409 si ese renglón o alguno posterior de la tabla ya recibió pagos. */
        void exigirSinPagosDesde(int fila, String mensaje) {
            for (int j = fila; j < mensual.size(); j++) {
                if (!mensual.get(j).pagos().isEmpty()) throw new ConflictException(mensaje);
            }
        }
    }
}
