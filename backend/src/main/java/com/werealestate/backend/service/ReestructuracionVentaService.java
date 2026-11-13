package com.werealestate.backend.service;

import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaReestructurarRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaReestructuracion;
import com.werealestate.backend.repository.VentaAportacionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reestructuración del plan de pagos de una venta: cada una queda registrada y el plan nuevo reemplaza al vigente. */
@Service
@Transactional
public class ReestructuracionVentaService {

    private final VentaAportacionRepository ventaAportacionRepository;
    private final ComisionService comisionService;
    private final ConsultasVenta consultas;
    private final AutorizacionVentas autorizacion;
    private final VentaDtoArmador dtoArmador;
    private final EntityManager entityManager;

    public ReestructuracionVentaService(
            VentaAportacionRepository ventaAportacionRepository,
            ComisionService comisionService,
            ConsultasVenta consultas,
            AutorizacionVentas autorizacion,
            VentaDtoArmador dtoArmador,
            EntityManager entityManager) {
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.comisionService = comisionService;
        this.consultas = consultas;
        this.autorizacion = autorizacion;
        this.dtoArmador = dtoArmador;
        this.entityManager = entityManager;
    }

    /**
     * Reestructura el plan de pagos de la venta (p. ej. de mensualidades a anualidades). Lo abonado hasta hoy se queda
     * como estaba y el plan nuevo reparte el saldo de hoy, atrasos incluidos; el precio no cambia (decisiones del
     * usuario). Los meses con aportación pagan esa aportación y lo demás se reparte en mensualidades iguales. Solo
     * admin y administración, con su contraseña y un motivo; se puede hacer varias veces y cada una queda registrada.
     */
    public VentaDto reestructurar(Long id, VentaReestructurarRequest request) {
        Usuario actual = autorizacion.exigirAdminOLiderConContrasena(
                "Solo un administrador o personal de administración puede reestructurar una venta", request.password());
        Venta venta = consultas.obtenerEntidad(id);
        if (venta.getDepositoPendiente() != null) {
            throw new ConflictException("Primero aplica el depósito pendiente de la venta");
        }
        BigDecimal precio = consultas.precioVenta(id);
        BigDecimal abonado = consultas.totalAbonado(id);
        if (precio.subtract(abonado).compareTo(UN_CENTAVO) <= 0) {
            throw new ConflictException("La venta ya está liquidada: no hay saldo que reestructurar");
        }

        int plazo = request.plazoMeses();
        // Se rehace TODO el plan (decisión del usuario): arranca en el primer mes del plan original de la venta y reparte
        // el precio completo menos el enganche capturado; lo abonado se vuelve a aplicar en orden y los meses vencidos
        // sin cubrir salen atrasados.
        CalendarioPagos.Plan original = CalendarioPagos.planOriginal(venta);
        EngancheNuevo engancheNuevo = engancheNuevoDeLaReestructuracion(original, request, precio);
        BigDecimal enganche = engancheNuevo != null
                ? engancheNuevo.monto()
                : original.conPagoInicialPublico() ? original.enganche() : BigDecimal.ZERO;
        BigDecimal aRepartir = precio.subtract(enganche);
        if (aRepartir.compareTo(UN_CENTAVO) <= 0) {
            throw new ConflictException("El enganche cubre todo el precio: no hay mensualidades que reestructurar");
        }
        YearMonth primero = original.primerMes();
        Map<YearMonth, BigDecimal> aportaciones =
                aportacionesDelPlanNuevo(request, primero, primero.plusMonths(plazo - 1L));
        BigDecimal aportado = aportaciones.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mensualidad = mensualidadDelPlanNuevo(aRepartir, aportado, plazo - aportaciones.size());

        CalendarioPagos.Plan anterior = CalendarioPagos.planVigente(venta);
        String formaPago = "Reestructurada · " + (enganche.signum() > 0 ? "con enganche · " : "") + plazo + " meses"
                + (aportaciones.isEmpty() ? "" : " + aportaciones");
        if (formaPago.length() > 50) formaPago = formaPago.substring(0, 50);
        String resumenNuevo = describirPlan(formaPago, primero, plazo, mensualidad, aportaciones.size(), aportado);
        VentaReestructuracion r = new VentaReestructuracion(
                venta, LocalDate.now(), abonado, aRepartir, request.motivo().trim(), actual,
                anterior.inicio(), venta.getFormaPago(), anterior.mensualidad(), anterior.plazoMeses(), anterior.diaPago(),
                anterior.primeraEnMesInicio(), anterior.engancheLabel(), anterior.enganche(),
                describirPlanVigente(venta), resumenNuevo).comoCompleta();
        entityManager.persist(r);
        venta.agregarReestructuracion(r);
        if (engancheNuevo != null) venta.ponerEnganche(engancheNuevo.etiqueta(), engancheNuevo.monto());
        venta.aplicarPlanReestructurado(
                formaPago, original.inicio(), mensualidad, plazo, request.diaPago(), original.primeraEnMesInicio());
        entityManager.flush();
        aportaciones.forEach((ym, monto) -> ventaAportacionRepository.save(
                new VentaAportacion(venta, ym.getYear(), ym.getMonthValue(), monto, r.getId())));
        comisionService.sincronizar(venta);
        return dtoArmador.armar(venta);
    }

    private static final BigDecimal UN_CENTAVO = new BigDecimal("0.01");

    private record EngancheNuevo(String etiqueta, BigDecimal monto) {
    }

    /** Enganche nuevo solo si la venta no tenía (si ya tenía, se conserva el original y esto devuelve null). */
    private static EngancheNuevo engancheNuevoDeLaReestructuracion(
            CalendarioPagos.Plan original, VentaReestructurarRequest request, BigDecimal precio) {
        if (original.conPagoInicialPublico() || request.enganche() == null || request.enganche().signum() <= 0) {
            return null;
        }
        String etiqueta = request.engancheLabel() == null ? "Enganche" : request.engancheLabel().trim();
        if (!CalendarioPagos.ETIQUETAS_PAGO_INICIAL.contains(etiqueta)) {
            throw new ValidationException("El primer pago debe ser «Enganche» o «Pago inicial»");
        }
        if (request.enganche().compareTo(precio) >= 0) {
            throw new ValidationException("El enganche no puede ser igual o mayor al precio");
        }
        return new EngancheNuevo(etiqueta, request.enganche().setScale(2, RoundingMode.HALF_UP));
    }

    /** Las aportaciones del plan nuevo por mes, dentro del plazo nuevo y sin repetir mes. */
    private static Map<YearMonth, BigDecimal> aportacionesDelPlanNuevo(
            VentaReestructurarRequest request, YearMonth primero, YearMonth ultimo) {
        Map<YearMonth, BigDecimal> aportaciones = new TreeMap<>();
        if (request.aportaciones() == null) return aportaciones;
        for (var a : request.aportaciones()) {
            YearMonth ym = YearMonth.of(a.anio(), a.mes());
            if (ym.isBefore(primero) || ym.isAfter(ultimo)) {
                throw new ValidationException("La aportación de " + ym + " está fuera del plazo nuevo (" + primero + " a " + ultimo + ")");
            }
            if (aportaciones.put(ym, a.monto()) != null) {
                throw new ValidationException("Hay dos aportaciones en " + ym);
            }
        }
        return aportaciones;
    }

    /** Lo que resta de repartir entre los meses sin aportación, en mensualidades iguales. */
    private static BigDecimal mensualidadDelPlanNuevo(BigDecimal aRepartir, BigDecimal aportado, int regulares) {
        BigDecimal resto = aRepartir.subtract(aportado);
        if (regulares == 0) {
            if (resto.abs().compareTo(UN_CENTAVO) > 0) {
                throw new ValidationException("Todos los meses son aportaciones: deben sumar $" + aRepartir);
            }
            return BigDecimal.ZERO;
        }
        if (resto.compareTo(UN_CENTAVO) <= 0) {
            throw new ValidationException("Las aportaciones cubren todo lo que hay que repartir ($" + aRepartir + "): reduce alguna o el plazo");
        }
        // Hacia abajo: los centavos que sobran los absorbe la última mensualidad (CalendarioPagos), en lugar de
        // recortar el último pago del plan.
        return resto.divide(BigDecimal.valueOf(regulares), 2, RoundingMode.DOWN);
    }

    private String describirPlanVigente(Venta venta) {
        CalendarioPagos.Plan p = CalendarioPagos.planVigente(venta);
        if (!p.conPlazoPublico()) return venta.getFormaPago() + " · contado";
        List<VentaAportacion> propias = ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(venta.getId()).stream()
                .filter(a -> ConsultasVenta.delPlanVigente(venta, a))
                .toList();
        BigDecimal aportado = propias.stream().map(VentaAportacion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return describirPlan(venta.getFormaPago(), p.primerMes(), p.plazoMeses(), p.mensualidad(), propias.size(), aportado);
    }

    /** "Reestructurada · 24 meses: mensualidad $5,000.00 desde 2026-12 (+ 2 aportaciones por $40,000.00)". */
    private static String describirPlan(String formaPago, YearMonth desde, int plazo, BigDecimal mensualidad, int aportaciones, BigDecimal aportado) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
        String texto = formaPago + ": " + plazo + " meses desde " + desde + ", mensualidad " + f.format(mensualidad);
        if (aportaciones > 0) texto += " + " + aportaciones + (aportaciones == 1 ? " aportación" : " aportaciones") + " por " + f.format(aportado);
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }
}
