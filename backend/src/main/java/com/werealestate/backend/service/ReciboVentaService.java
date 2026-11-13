package com.werealestate.backend.service;

import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.ReciboAnularRequest;
import com.werealestate.backend.dto.ReciboEditarRequest;
import com.werealestate.backend.dto.ReciboHistorialDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AccionRecibo;
import com.werealestate.backend.model.MesCubierto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.ReciboVenta;
import com.werealestate.backend.model.ReciboVentaHistorial;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.ReciboVentaHistorialRepository;
import com.werealestate.backend.repository.ReciboVentaRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los recibos de una venta (los abonos del cliente): registrarlos con sus líneas por tipo de abono, editarlos, anularlos
 * (no se borran: quedan con su motivo) y llevar su bitácora. Cada cambio vuelve a calcular las comisiones de la venta.
 */
@Service
@Transactional
public class ReciboVentaService {

    private final VentaAportacionRepository ventaAportacionRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final ReciboVentaRepository reciboVentaRepository;
    private final ReciboVentaHistorialRepository reciboHistorialRepository;
    private final ComisionService comisionService;
    private final ConsultasVenta consultas;
    private final AutorizacionVentas autorizacion;

    public ReciboVentaService(
            VentaAportacionRepository ventaAportacionRepository,
            PagoVentaRepository pagoVentaRepository,
            ReciboVentaRepository reciboVentaRepository,
            ReciboVentaHistorialRepository reciboHistorialRepository,
            ComisionService comisionService,
            ConsultasVenta consultas,
            AutorizacionVentas autorizacion) {
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.reciboVentaRepository = reciboVentaRepository;
        this.reciboHistorialRepository = reciboHistorialRepository;
        this.comisionService = comisionService;
        this.consultas = consultas;
        this.autorizacion = autorizacion;
    }

    /** Abonos de una venta, más reciente primero. */
    public List<PagoVentaDto> listarPagos(Long ventaId) {
        autorizacion.exigirAdminOLider();
        consultas.obtenerEntidad(ventaId); // valida que la venta exista antes de listar sus pagos
        return pagoVentaRepository.findByVentaIdOrderByFechaDesc(ventaId).stream().map(PagoVentaDto::from).toList();
    }

    /** Registra un recibo con una o más líneas (cada una de un tipo). Se rechaza si el total excede el
     * saldo pendiente: un abono de más casi siempre es un error de captura (monto o venta
     * equivocada), no un pago real de más. Devuelve las líneas creadas. */
    public List<PagoVentaDto> registrarPago(Long ventaId, PagoVentaCreateRequest request) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);

        BigDecimal totalRecibo = request.lineas().stream()
                .map(PagoVentaCreateRequest.Linea::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoPendiente = consultas.saldoPendiente(ventaId);
        if (totalRecibo.compareTo(saldoPendiente) > 0) {
            throw new ConflictException(
                    "El abono ($" + totalRecibo + ") excede el saldo pendiente ($" + saldoPendiente + ")");
        }

        // El folio es obligatorio pero se puede repetir (decisión del usuario, 2026-11-13; V84 quitó el índice único).
        String folio = request.folio().trim();

        // Se validan todas las líneas antes de guardar nada.
        List<PagoVenta> lineas = new ArrayList<>();
        String notas = Textos.opcional(request.notas());
        ReciboVenta recibo = new ReciboVenta(venta, folio, request.fecha(), notas, actual);
        for (PagoVentaCreateRequest.Linea linea : request.lineas()) {
            lineas.add(construirLinea(venta, recibo, linea));
        }

        reciboVentaRepository.save(recibo);
        reciboHistorialRepository.save(new ReciboVentaHistorial(
                recibo, AccionRecibo.CREADO, actual, "Folio " + folio + " · " + describirLineas(lineas)));
        List<PagoVentaDto> guardadas = pagoVentaRepository.saveAll(lineas).stream().map(PagoVentaDto::from).toList();
        comisionService.sincronizar(venta);
        return guardadas;
    }

    /** Valida lo que cada tipo exige y arma la línea. */
    private PagoVenta construirLinea(Venta venta, ReciboVenta recibo, PagoVentaCreateRequest.Linea linea) {
        boolean extemporaneo = Boolean.TRUE.equals(linea.extemporaneo());
        Referencias ref = validarReferencias(venta, linea.tipo(), linea.meses(), linea.aportacionId(), extemporaneo, recibo.getFecha(), null);
        return new PagoVenta(venta, recibo, linea.monto(), linea.tipo(), ref.aportacion(), ref.meses(), extemporaneo);
    }

    private record Referencias(List<MesCubierto> meses, VentaAportacion aportacion) {
    }

    /** Mensualidad = meses dentro del plazo de la venta (o, si es extemporánea, ningún mes: el cliente ya pasó el plazo
     * sin terminar de pagar y el dinero solo abona al saldo); aportación = una aportación programada de esta misma
     * venta; enganche y apartado, nada más. */
    private Referencias validarReferencias(
            Venta venta,
            TipoAbono tipo,
            List<PagoVentaCreateRequest.Mes> mesesPedidos,
            Long aportacionId,
            boolean extemporaneo,
            LocalDate fechaExtemporaneo,
            Long aportacionActual) {
        boolean traeMeses = mesesPedidos != null && !mesesPedidos.isEmpty();
        // Los abonos vigentes fijan el primer mes posible del plan (CalendarioPagos.aplicandoMesDeVenta).
        List<PagoVenta> pagosVigentes = pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId());

        if (extemporaneo && !tipo.esMensual()) {
            throw new ValidationException("Solo una mensualidad puede ser un pago extemporáneo");
        }

        if (tipo.esMensual()) {
            if (venta.getPlazoMeses() == null || venta.getPlazoMeses() < 1 || venta.getMensualidad() == null) {
                throw new ValidationException("Esta venta no tiene mensualidades");
            }
            if (aportacionId != null) {
                throw new ValidationException("Una mensualidad no se liga a una aportación");
            }
            if (extemporaneo) {
                if (traeMeses) {
                    throw new ValidationException("Un pago extemporáneo no lleva meses: solo abona al saldo");
                }
                // Extemporáneo = después de que terminó el plan (decisión del usuario): dentro del plazo es una
                // mensualidad normal con sus meses.
                YearMonth[] rango = CalendarioPagos.rangoDeMeses(venta, pagosVigentes);
                if (fechaExtemporaneo != null && rango != null && !YearMonth.from(fechaExtemporaneo).isAfter(rango[1])) {
                    throw new ValidationException("El plan termina en " + rango[1]
                            + ": un pago extemporáneo solo se registra después; dentro del plazo elige los meses que cubre");
                }
                return new Referencias(List.of(), null);
            }
            if (!traeMeses) {
                throw new ValidationException("Indica el mes o los meses que cubre la mensualidad");
            }
            return new Referencias(mesesDelPlazo(venta, mesesPedidos, pagosVigentes), null);
        }

        // Un apartado va a la deuda total (sin meses) o, si así se eligió, a la mensualidad de uno o varios meses: solo
        // cambia a qué mes se aplica en la tabla; para la comisión sigue contando como apartado (decisión del usuario).
        if (tipo == TipoAbono.APARTADO && traeMeses) {
            if (venta.getPlazoMeses() == null || venta.getPlazoMeses() < 1 || venta.getMensualidad() == null) {
                throw new ValidationException("Esta venta no tiene mensualidades: el apartado va a la deuda total");
            }
            if (aportacionId != null) {
                throw new ValidationException("Un apartado no se liga a una aportación");
            }
            return new Referencias(mesesDelPlazo(venta, mesesPedidos, pagosVigentes), null);
        }

        if (traeMeses) {
            throw new ValidationException("Solo una mensualidad o un apartado cubren meses");
        }
        if (tipo == TipoAbono.ENGANCHE && (venta.getEnganche() == null || venta.getEnganche().signum() <= 0)) {
            throw new ValidationException("Esta venta no tiene enganche: registra el pago como mensualidad o apartado");
        }
        if (tipo == TipoAbono.APORTACION) {
            if (aportacionId == null) {
                throw new ValidationException("Indica qué aportación programada paga");
            }
            VentaAportacion aportacion = ventaAportacionRepository.findById(aportacionId)
                    .filter(a -> a.getVenta().getId().equals(venta.getId()))
                    .orElseThrow(() -> new ValidationException("Esa aportación no pertenece a esta venta"));
            // Una aportación de un plan reemplazado no se puede volver a cobrar (sí conservarla en un recibo que ya la tenía).
            if (!ConsultasVenta.delPlanVigente(venta, aportacion) && !aportacion.getId().equals(aportacionActual)) {
                throw new ValidationException("Esa aportación es de un plan anterior a la reestructuración: elige una del plan vigente");
            }
            return new Referencias(List.of(), aportacion);
        }
        if (aportacionId != null) {
            throw new ValidationException("Solo una aportación se liga a una aportación programada");
        }
        return new Referencias(List.of(), null);
    }

    /** Valida que los meses estén dentro del plazo (con reestructuraciones, del primer mes del plan original al último
     * del vigente) y no se repitan. */
    private static List<MesCubierto> mesesDelPlazo(
            Venta venta, List<PagoVentaCreateRequest.Mes> mesesPedidos, List<PagoVenta> pagosVigentes) {
        YearMonth[] rango = CalendarioPagos.rangoDeMeses(venta, pagosVigentes);
        YearMonth primero = rango[0];
        YearMonth ultimo = rango[1];
        Set<YearMonth> vistos = new HashSet<>();
        List<MesCubierto> meses = new ArrayList<>();
        for (PagoVentaCreateRequest.Mes m : mesesPedidos) {
            YearMonth ym = YearMonth.of(m.anio(), m.mes());
            if (ym.isBefore(primero) || ym.isAfter(ultimo)) {
                throw new ValidationException("El mes " + ym + " está fuera del plazo de la venta (" + primero + " a " + ultimo + ")");
            }
            if (!vistos.add(ym)) {
                throw new ValidationException("El mes " + ym + " está repetido");
            }
            meses.add(new MesCubierto(m.anio(), m.mes()));
        }
        return meses;
    }

    /** "Enganche $20,000 · Mensualidad $10,000 (2026-11, 2026-12)" para la bitácora. */
    private static String describirLineas(List<PagoVenta> lineas) {
        return lineas.stream().map(ReciboVentaService::describirLinea).collect(Collectors.joining(" · "));
    }

    private static String describirLinea(PagoVenta l) {
        String tipo = l.getTipo() == null ? "Sin tipo" : switch (l.getTipo()) {
            case MENSUALIDAD -> "Mensualidad";
            case A_CUENTA_COMISION -> "A cuenta de comisión";
            case ENGANCHE -> "Enganche";
            case APARTADO -> "Apartado";
            case APORTACION -> "Aportación";
        };
        String meses = l.getMeses().isEmpty()
                ? ""
                : " (" + l.getMeses().stream()
                        .map(m -> YearMonth.of(m.getAnio(), m.getMes()).toString())
                        .sorted()
                        .collect(Collectors.joining(", ")) + ")";
        String aportacion = l.getAportacion() == null
                ? ""
                : " (" + YearMonth.of(l.getAportacion().getAnio(), l.getAportacion().getMes()) + ")";
        return tipo + " $" + l.getMonto().toPlainString() + meses + aportacion + (l.isExtemporaneo() ? " (extemporáneo)" : "");
    }

    // ---------------------------------------------------------------- editar / anular un recibo

    /** Edita un recibo: folio, nota, fecha y, por línea, tipo, meses y aportación. El monto no se toca: si está mal,
     * se anula el recibo y se registra uno nuevo. Al corregir la fecha, Finanzas toma el abono en el mes nuevo y las
     * comisiones se reconstruyen con ella (el sábado de entrega cambia); lo ya entregado no se modifica. Un recibo
     * anulado no se edita. Deja constancia en la bitácora. */
    public List<PagoVentaDto> editarRecibo(Long ventaId, Long reciboId, ReciboEditarRequest request) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);
        ReciboVenta recibo = obtenerRecibo(ventaId, reciboId);
        if (recibo.isAnulado()) {
            throw new ConflictException("Un recibo anulado no se puede editar");
        }

        // El folio se puede repetir entre recibos (ver registrar).
        String folio = request.folio().trim();

        List<PagoVenta> lineas = pagoVentaRepository.findByReciboId(reciboId);
        String antes = describirLineas(lineas);
        String notas = Textos.opcional(request.notas());
        LocalDate fechaNueva = request.fecha() != null ? request.fecha() : recibo.getFecha();
        List<String> cambios = cambiosDeCabecera(recibo, folio, notas, fechaNueva);

        // Se validan todas las líneas antes de guardar nada.
        CorreccionDeLineas correccion = prepararLineas(venta, lineas, request, fechaNueva);
        if (!correccion.montosCambiados().isEmpty()) {
            validarCorreccionDeMonto(actual, request, ventaId, correccion.diferencia());
            cambios.add("Monto " + String.join(", ", correccion.montosCambiados()) + " · Motivo: " + request.motivo().trim());
        }

        recibo.setFolio(folio);
        recibo.setNotas(notas);
        recibo.cambiarFecha(fechaNueva);
        lineas.forEach(l -> l.actualizarFecha(fechaNueva));
        correccion.aplicar().forEach(Runnable::run);
        pagoVentaRepository.saveAll(lineas);
        String despues = describirLineas(lineas);
        if (!antes.equals(despues)) {
            cambios.add("Conceptos: " + antes + " → " + despues);
        }
        reciboVentaRepository.save(recibo);
        reciboHistorialRepository.save(new ReciboVentaHistorial(
                recibo, AccionRecibo.EDITADO, actual, cambios.isEmpty() ? "Sin cambios" : String.join(" | ", cambios)));
        // El tipo de cada línea define cuánto aporta a la comisión (regla por tipo de abono) y la fecha, cuándo toca.
        comisionService.sincronizar(venta);
        return pagoVentaRepository.findByReciboId(reciboId).stream().map(PagoVentaDto::from).toList();
    }

    /** Lo que cambió en folio, nota y fecha del recibo, para la bitácora. */
    private static List<String> cambiosDeCabecera(ReciboVenta recibo, String folio, String notas, LocalDate fechaNueva) {
        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(recibo.getFolio(), folio)) {
            cambios.add("Folio " + (recibo.getFolio() == null ? "(vacío)" : recibo.getFolio()) + " → " + folio);
        }
        if (!Objects.equals(recibo.getNotas(), notas)) {
            cambios.add("Nota actualizada");
        }
        if (!fechaNueva.equals(recibo.getFecha())) {
            DateTimeFormatter dma = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            cambios.add("Fecha " + recibo.getFecha().format(dma) + " → " + fechaNueva.format(dma));
        }
        return cambios;
    }

    /** Los cambios ya validados de las líneas de un recibo, listos para aplicarse: lo que hay que ejecutar, cuánto
     * cambia el total abonado y la descripción de cada monto corregido. */
    private record CorreccionDeLineas(List<Runnable> aplicar, BigDecimal diferencia, List<String> montosCambiados) {
    }

    private CorreccionDeLineas prepararLineas(
            Venta venta, List<PagoVenta> lineas, ReciboEditarRequest request, LocalDate fechaNueva) {
        Map<Long, PagoVenta> porId = new HashMap<>();
        lineas.forEach(l -> porId.put(l.getId(), l));

        List<Runnable> aplicar = new ArrayList<>();
        BigDecimal diferencia = BigDecimal.ZERO;
        List<String> montosCambiados = new ArrayList<>();
        for (ReciboEditarRequest.Linea linea : request.lineas()) {
            PagoVenta pago = porId.get(linea.pagoId());
            if (pago == null) {
                throw new ValidationException("La línea " + linea.pagoId() + " no pertenece a este recibo");
            }
            boolean extemporaneo = Boolean.TRUE.equals(linea.extemporaneo());
            // Solo se revisa la fecha del extemporáneo si se acaba de marcar (los de antes de la regla se pueden seguir editando).
            Referencias ref = validarReferencias(venta, linea.tipo(), linea.meses(), linea.aportacionId(), extemporaneo,
                    extemporaneo && !pago.isExtemporaneo() ? fechaNueva : null,
                    pago.getAportacion() != null ? pago.getAportacion().getId() : null);
            aplicar.add(() -> pago.actualizarConcepto(linea.tipo(), ref.aportacion(), ref.meses(), extemporaneo));
            if (linea.monto() != null && linea.monto().compareTo(pago.getMonto()) != 0) {
                BigDecimal nuevo = linea.monto().setScale(2, RoundingMode.HALF_UP);
                diferencia = diferencia.add(nuevo.subtract(pago.getMonto()));
                montosCambiados.add(describirLinea(pago) + ": $" + pago.getMonto() + " → $" + nuevo);
                aplicar.add(() -> pago.corregirMonto(nuevo));
            }
        }
        return new CorreccionDeLineas(aplicar, diferencia, montosCambiados);
    }

    /** Corregir un monto mueve saldo, comisiones y Finanzas: contraseña y motivo, y nunca más que el precio. */
    private void validarCorreccionDeMonto(Usuario actual, ReciboEditarRequest request, Long ventaId, BigDecimal diferencia) {
        if (request.password() == null) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        autorizacion.exigirContrasena(actual, request.password());
        if (request.motivo() == null || request.motivo().isBlank()) {
            throw new ValidationException("Escribe el motivo del cambio de monto");
        }
        BigDecimal saldo = consultas.saldoPendiente(ventaId);
        if (diferencia.compareTo(saldo) > 0) {
            throw new ValidationException("Con el monto nuevo el abonado pasaría del precio: el saldo pendiente es $" + saldo);
        }
    }

    /** Anula un recibo: no se borra, queda con su motivo, quién y cuándo. Su dinero deja de contar en
     * el saldo de la venta y en las comisiones; en Finanzas se registra un contra-asiento en la fecha
     * de hoy (el mes original no cambia). Si la comisión ya se había entregado, la entrega no se toca:
     * lo que ya no esté respaldado por abonos queda como "por devolver". */
    public List<PagoVentaDto> anularRecibo(Long ventaId, Long reciboId, ReciboAnularRequest request) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);
        ReciboVenta recibo = obtenerRecibo(ventaId, reciboId);
        if (recibo.isAnulado()) {
            throw new ConflictException("Este recibo ya está anulado");
        }

        String motivo = request.motivo().trim();
        recibo.anular(motivo, actual, LocalDate.now());
        reciboVentaRepository.save(recibo);
        List<PagoVenta> lineas = pagoVentaRepository.findByReciboId(reciboId);
        reciboHistorialRepository.save(new ReciboVentaHistorial(
                recibo, AccionRecibo.ANULADO, actual, "Motivo: " + motivo + " · " + describirLineas(lineas)));
        comisionService.sincronizar(venta);
        return lineas.stream().map(PagoVentaDto::from).toList();
    }

    /** Bitácora de todos los recibos de la venta, la más reciente primero. */
    public List<ReciboHistorialDto> historialDeRecibos(Long ventaId) {
        autorizacion.exigirAdminOLider();
        consultas.obtenerEntidad(ventaId);
        return reciboHistorialRepository.findByVentaId(ventaId).stream().map(ReciboHistorialDto::from).toList();
    }

    /** Borra todos los abonos y recibos de una venta, con su bitácora (solo al eliminar la venta). */
    void borrarDeVenta(Long ventaId) {
        pagoVentaRepository.deleteAll(pagoVentaRepository.findByVentaIdOrderByFechaDesc(ventaId));
        pagoVentaRepository.flush();
        List<ReciboVenta> recibosDeVenta = reciboVentaRepository.findByVentaId(ventaId);
        recibosDeVenta.forEach(r -> reciboHistorialRepository.deleteAll(reciboHistorialRepository.findByReciboId(r.getId())));
        reciboHistorialRepository.flush();
        reciboVentaRepository.deleteAll(recibosDeVenta);
    }

    private ReciboVenta obtenerRecibo(Long ventaId, Long reciboId) {
        return reciboVentaRepository.findById(reciboId)
                .filter(r -> r.getVenta().getId().equals(ventaId))
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado"));
    }
}
