package com.werealestate.backend.service;

import com.werealestate.backend.dto.AplicarDepositoRequest;
import com.werealestate.backend.dto.ContadoresVentasDto;
import com.werealestate.backend.dto.NumeracionVentaDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.ReciboAnularRequest;
import com.werealestate.backend.dto.ReciboEditarRequest;
import com.werealestate.backend.dto.ReciboHistorialDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.ClienteResumenDto;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaAportacionDto;
import com.werealestate.backend.dto.VentaAportacionItemRequest;
import com.werealestate.backend.dto.VentaLoteDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.dto.VentaUpdateRequest;
import com.werealestate.backend.dto.VentaEliminarRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.AccionRecibo;
import com.werealestate.backend.model.MedioFirma;
import com.werealestate.backend.model.MesCubierto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.ReciboVenta;
import com.werealestate.backend.model.ReciboVentaHistorial;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.ReciboVentaHistorialRepository;
import com.werealestate.backend.repository.ReciboVentaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de ventas cerradas. Cliente es texto libre a propósito (ver Venta): no todo comprador
 * pasó por el CRM como lead. El asesor sí es una relación real, a un usuario interno o a un
 * AsesorExterno registrado (ver resolverAsesor). Una venta puede incluir varios lotes (misma
 * operación, una sola mensualidad/plazo/saldo combinado, ver VentaLote). Exclusivo de admin y
 * líder de área, igual que los estados de lote comprometidos con dinero real.
 */
@Service
@Transactional
public class VentaService {

    private static final String DESARROLLO_SAMAI_CAMPESTRE = "SAMAI Campestre";

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final LoteRepository loteRepository;
    private final LoteService loteService;
    private final PagoVentaRepository pagoVentaRepository;
    private final ReciboVentaRepository reciboVentaRepository;
    private final ReciboVentaHistorialRepository reciboHistorialRepository;
    private final ComisionService comisionService;
    private final ClienteService clienteService;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final com.werealestate.backend.repository.WeFormRespuestaRepository weFormRespuestaRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final AsesorExternoRepository asesorExternoRepository;
    private final CurrentUserProvider currentUserProvider;

    public VentaService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            LoteRepository loteRepository,
            LoteService loteService,
            PagoVentaRepository pagoVentaRepository,
            ReciboVentaRepository reciboVentaRepository,
            ReciboVentaHistorialRepository reciboHistorialRepository,
            ComisionService comisionService,
            ClienteService clienteService,
            VentaCopropietarioRepository copropietarioRepository,
            com.werealestate.backend.repository.WeFormRespuestaRepository weFormRespuestaRepository,
            PasswordEncoder passwordEncoder,
            UsuarioRepository usuarioRepository,
            AsesorExternoRepository asesorExternoRepository,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.loteRepository = loteRepository;
        this.loteService = loteService;
        this.pagoVentaRepository = pagoVentaRepository;
        this.reciboVentaRepository = reciboVentaRepository;
        this.reciboHistorialRepository = reciboHistorialRepository;
        this.comisionService = comisionService;
        this.clienteService = clienteService;
        this.copropietarioRepository = copropietarioRepository;
        this.weFormRespuestaRepository = weFormRespuestaRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public VentaDto crear(VentaCreateRequest request) {
        exigirAdminOLider();
        return toDto(crearVenta(request, currentUserProvider.getUsuarioActual(), false));
    }

    /** La venta que nace de un registro de venta de We Forms (ver WeFormPublicoService): sin sesión, a nombre
     * del usuario de sistema; los lotes pasan a "En proceso de firma" (no a Vendido), la fecha queda
     * como provisional y el dinero con el que estuvieran apartados queda pendiente de aplicar. Quien la
     * llama ya validó el PIN del asesor, el cliente y que los lotes se puedan tomar. */
    public Venta crearDesdeRegistroDeVenta(VentaCreateRequest request, Usuario sistema) {
        return crearVenta(request, sistema, true);
    }

    private Venta crearVenta(VentaCreateRequest request, Usuario registradaPor, boolean registroDeVenta) {
        // Primero el número (puede recorrer otras ventas y vacía el contexto de persistencia): todavía
        // no se ha cargado nada de esta operación.
        long numeroVenta = reservarNumero(request.numero(), 0L);

        Set<Long> loteIdsUnicos = new HashSet<>();
        for (VentaLoteItemRequest item : request.lotes()) {
            if (!loteIdsUnicos.add(item.loteId())) {
                throw new ValidationException("El mismo lote no puede repetirse dentro de una venta");
            }
        }

        AsesorResuelto asesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());
        validarPorcentajeComision(request.comisionPorcentaje(), "La comisión del asesor");
        // Copropietario (segundo asesor): opcional; distinto del asesor principal y con su propio %.
        AsesorResuelto coasesor = null;
        if (request.coasesorUsuarioId() != null || request.coasesorExternoId() != null) {
            coasesor = resolverAsesor(request.coasesorUsuarioId(), request.coasesorExternoId());
            boolean mismoUsuario = coasesor.usuario() != null && asesor.usuario() != null
                    && coasesor.usuario().getId().equals(asesor.usuario().getId());
            boolean mismoExterno = coasesor.externo() != null && asesor.externo() != null
                    && coasesor.externo().getId().equals(asesor.externo().getId());
            if (mismoUsuario || mismoExterno) {
                throw new ValidationException("El copropietario debe ser distinto al asesor de la venta");
            }
            validarPorcentajeComision(request.coasesorComisionPorcentaje(), "La comisión del copropietario");
        }
        List<VentaAportacionItemRequest> aportaciones =
                request.aportaciones() == null ? List.of() : request.aportaciones();
        validarAportaciones(aportaciones, request);

        Cliente clienteRef = clienteService.obtenerEntidad(request.clienteId());
        if (!clienteRef.isActivo()) {
            throw new ValidationException("Ese cliente está inactivo: actívalo para registrarle una venta");
        }
        String cliente = clienteRef.nombreCompleto();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();

        String engancheLabel = request.engancheLabel() == null || request.engancheLabel().isBlank()
                ? null
                : request.engancheLabel().trim();

        Venta venta = new Venta(
                cliente, asesor.usuario(), asesor.externo(), request.formaPago().trim(), request.fechaVenta(),
                request.mensualidad(), request.plazoMeses(), engancheLabel, request.enganche(),
                request.diaPago(), Boolean.TRUE.equals(request.primeraMensualidadMesVenta()), notas);
        venta.setNumero(numeroVenta);
        venta.setModalidadPago(request.modalidadPago());
        venta.ligarCliente(clienteRef);
        venta.setRegistradaPor(registradaPor);
        boolean contratoFirmado = Boolean.TRUE.equals(request.contratoFirmado());
        String driveUrl = validarContrato(
                contratoFirmado, request.contratoFechaFirma(), request.contratoMedio(), request.contratoDriveUrl());
        venta.registrarContrato(contratoFirmado, request.contratoFechaFirma(), request.contratoMedio(), driveUrl);
        venta = ventaRepository.save(venta);
        aplicarCopropietarios(venta, request.copropietariosIds());

        for (VentaAportacionItemRequest item : aportaciones) {
            ventaAportacionRepository.save(new VentaAportacion(venta, item.anio(), item.mes(), item.monto()));
        }

        BigDecimal depositoPendiente = BigDecimal.ZERO;
        for (VentaLoteItemRequest item : request.lotes()) {
            Lote lote = loteRepository
                    .findById(item.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
            ventaLoteRepository.save(new VentaLote(venta, lote, item.precio()));

            if (registroDeVenta) {
                if (lote.getMontoApartado() != null) depositoPendiente = depositoPendiente.add(lote.getMontoApartado());
                loteService.marcarEnProcesoDeFirma(lote.getId(), cliente, asesor.nombre(), registradaPor);
            } else if (request.marcarLoteVendido()) {
                loteService.marcarVendido(lote.getId(), cliente, asesor.nombre());
            }
        }
        if (registroDeVenta) {
            venta.setFechaProvisional(true);
            venta.setDepositoPendiente(depositoPendiente.signum() > 0 ? depositoPendiente : null);
        }

        // Una venta nueva usa la regla de comisión por tipo de abono (las existentes conservan la suya).
        comisionService.sincronizarVentaNueva(
                venta,
                request.comisionPorcentaje(),
                coasesor != null ? coasesor.usuario() : null,
                coasesor != null ? coasesor.externo() : null,
                request.coasesorComisionPorcentaje());
        return obtenerEntidad(venta.getId());
    }

    /** Reglas de las aportaciones (el frontend ya las aplica, aquí se hacen valer en el servidor):
     * necesitan plazo; cada una cae dentro de los meses del plan (desde el mes de la venta) y no en el
     * último año calendario del plazo (queda libre para mensualidades regulares); no se repite el mes;
     * y juntas dejan saldo por pagar en mensualidades. */
    private void validarAportaciones(List<VentaAportacionItemRequest> aportaciones, VentaCreateRequest request) {
        if (aportaciones.isEmpty()) {
            return;
        }
        if (request.plazoMeses() == null) {
            throw new ValidationException("Indica el plazo para registrar aportaciones");
        }

        YearMonth inicio = YearMonth.from(request.fechaVenta());
        YearMonth fin = inicio.plusMonths(request.plazoMeses() - 1L);
        Set<YearMonth> usados = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (VentaAportacionItemRequest a : aportaciones) {
            YearMonth mes = YearMonth.of(a.anio(), a.mes());
            if (mes.isBefore(inicio) || mes.isAfter(fin)) {
                throw new ValidationException(
                        "La aportación de " + a.mes() + "/" + a.anio() + " queda fuera del plazo de la venta");
            }
            if (mes.getYear() == fin.getYear()) {
                throw new ValidationException(
                        "No puede haber aportaciones en el último año del plazo (" + fin.getYear() + ")");
            }
            if (!usados.add(mes)) {
                throw new ValidationException("Hay dos aportaciones en " + a.mes() + "/" + a.anio());
            }
            total = total.add(a.monto());
        }

        // Con enganche o pago inicial (se pueden combinar con aportaciones o anualidades), entre los dos
        // tampoco pueden cubrir todo el precio: debe quedar saldo para las mensualidades regulares.
        BigDecimal precio =
                request.lotes().stream().map(VentaLoteItemRequest::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal enganche = request.enganche() == null ? BigDecimal.ZERO : request.enganche();
        if (total.add(enganche).compareTo(precio) >= 0) {
            throw new ValidationException(enganche.signum() > 0
                    ? "El enganche y las aportaciones no pueden sumar todo el precio de la venta"
                    : "Las aportaciones no pueden sumar todo el precio de la venta");
        }
    }

    /** Registra o corrige si el contrato de la venta ya está firmado y desde cuándo. Sin contrato
     * firmado, la comisión de una venta nueva se acumula pero no se puede entregar; al firmar queda
     * disponible el sábado siguiente a la firma. */
    public VentaDto registrarContrato(Long ventaId, com.werealestate.backend.dto.ContratoRequest request) {
        exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);
        String driveUrl = validarContrato(request.firmado(), request.fechaFirma(), request.medio(), request.driveUrl());
        venta.registrarContrato(request.firmado(), request.fechaFirma(), request.medio(), driveUrl);
        ventaRepository.save(venta);
        comisionService.sincronizar(venta);
        return toDto(venta);
    }

    /** Con el contrato firmado: fecha (no futura) y medio (físico o DocuSign) obligatorios. El enlace a
     * la carpeta de Drive es opcional (siempre, firmado o no) y debe ser http(s). Devuelve el enlace
     * limpio (o null). */
    private static String validarContrato(
            boolean firmado, java.time.LocalDate fechaFirma, MedioFirma medio, String driveUrl) {
        String url = driveUrl == null || driveUrl.isBlank() ? null : driveUrl.trim();
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new ValidationException("El enlace de Drive debe empezar con https://");
        }
        if (!firmado) return url;
        if (fechaFirma == null) {
            throw new ValidationException("Indica la fecha de firma del contrato");
        }
        if (fechaFirma.isAfter(java.time.LocalDate.now())) {
            throw new ValidationException("La fecha de firma no puede ser futura");
        }
        if (medio == null) {
            throw new ValidationException("Indica si el contrato se firmó en físico o en DocuSign");
        }
        return url;
    }

    /** Pone, cambia o quita (vacío) el número de contrato de la venta. Es texto libre pero no puede repetirse en
     * otra venta (sin importar mayúsculas). Lo pueden usar admin y administración, igual que el resto de Ventas. */
    public VentaDto registrarNumeroContrato(Long ventaId, com.werealestate.backend.dto.NumeroContratoRequest request) {
        exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);
        String numero = request.numeroContrato() == null || request.numeroContrato().isBlank()
                ? null
                : request.numeroContrato().trim();
        if (numero != null) {
            ventaRepository.findFirstByNumeroContratoIgnoreCase(numero)
                    .filter(otra -> !otra.getId().equals(ventaId))
                    .ifPresent(otra -> {
                        throw new ConflictException(
                                "El número de contrato " + numero + " ya lo tiene la venta #" + otra.getNumero());
                    });
        }
        venta.setNumeroContrato(numero);
        try {
            ventaRepository.saveAndFlush(venta);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Dos personas guardando el mismo número a la vez: lo frena el índice único.
            throw new ConflictException("Ese número de contrato ya lo tiene otra venta");
        }
        return toDto(venta);
    }

    public VentaDto obtener(Long id) {
        exigirAdminOLider();
        return toDto(obtenerEntidad(id));
    }

    /** Para abrir una venta desde su dirección (/panel/ventas/11): el número solo sirve para
     * encontrarla; todo lo que escribe (abonos, modificar) sigue usando el id estable de la venta
     * que devuelve esto, porque un número puede cambiar si se registra otra venta con fecha anterior. */
    public VentaDto obtenerPorNumero(Long numero) {
        exigirAdminOLider();
        return toDto(
                ventaRepository.findByNumero(numero).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada")));
    }

    /** Modifica los datos capturados de una venta (cliente, fechas, términos de financiamiento);
     * no toca sus lotes ni precios. Temporal: ver Venta.actualizar. */
    public VentaDto actualizar(Long id, VentaUpdateRequest request) {
        exigirAdminOLider();
        Venta venta = obtenerEntidad(id);
        AsesorResuelto asesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());

        String engancheLabel = request.engancheLabel() == null || request.engancheLabel().isBlank()
                ? null
                : request.engancheLabel().trim();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();

        // Una venta registrada desde We Forms con fecha provisional deja de serlo al cambiar la fecha o al confirmarla.
        boolean fechaCambio = !request.fechaVenta().equals(venta.getFechaVenta());
        if (venta.isFechaProvisional() && (fechaCambio || Boolean.TRUE.equals(request.confirmarFecha()))) {
            venta.setFechaProvisional(false);
        }

        venta.setModalidadPago(request.modalidadPago());
        venta.actualizar(
                venta.getCliente(),
                asesor.usuario(),
                asesor.externo(),
                request.formaPago().trim(),
                request.fechaVenta(),
                request.mensualidad(),
                request.plazoMeses(),
                engancheLabel,
                request.enganche(),
                request.diaPago() != null ? request.diaPago() : venta.getDiaPago(),
                request.primeraMensualidadMesVenta() != null
                        ? request.primeraMensualidadMesVenta()
                        : venta.isPrimeraMensualidadMesVenta(),
                notas);
        if (request.clienteId() != null) {
            Cliente nuevo = clienteService.obtenerEntidad(request.clienteId());
            venta.ligarCliente(nuevo);
        }
        if (request.copropietariosIds() != null) {
            aplicarCopropietarios(venta, request.copropietariosIds());
        } else if (request.clienteId() != null) {
            // El principal cambió: si estaba como copropietario, deja de serlo.
            copropietarioRepository.deleteAll(copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                    .filter(cp -> cp.getCliente().getId().equals(venta.getClienteRef().getId()))
                    .toList());
        }
        comisionService.sincronizar(venta);
        // Si se pidió otro número, se mueve (recorriendo las consecutivas si estaba ocupado) y se vuelve a
        // leer la venta con el número nuevo. La fecha ya no cambia el número.
        if (request.numero() != null && !request.numero().equals(venta.getNumero())) {
            long nuevo = reservarNumero(request.numero(), venta.getId(), venta.getNumero());
            ventaRepository.fijarNumero(venta.getId(), nuevo);
        }
        return toDto(obtenerEntidad(id));
    }

    /** Aplica el dinero con el que ya estaba apartado el lote de una venta que nació de un registro de venta
     * (ver Venta.depositoPendiente): SALDO lo registra como abono de tipo apartado (con su folio) y baja lo
     * que falta por pagar; MENSUALIDAD baja la mensualidad (mismo plazo, pago más bajo), igual que al
     * registrar una venta con depósito. Después el depósito ya no está pendiente. */
    public VentaDto aplicarDeposito(Long id, AplicarDepositoRequest request) {
        Usuario actual = exigirAdminOLider();
        Venta venta = obtenerEntidad(id);
        BigDecimal monto = venta.getDepositoPendiente();
        if (monto == null || monto.signum() <= 0) {
            throw new ValidationException("Esta venta no tiene un depósito pendiente de aplicar");
        }

        if (request.aplicarA() == AplicarDepositoRequest.AplicarDepositoA.SALDO) {
            if (request.folio() == null || request.folio().isBlank()) {
                throw new ValidationException("Ingresa el folio del dinero recibido al apartar el lote");
            }
            registrarPago(id, new PagoVentaCreateRequest(
                    java.time.LocalDate.now(),
                    request.folio().trim(),
                    "Dinero recibido al apartar el lote",
                    List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, monto, null, null))));
        } else {
            if (venta.getPlazoMeses() == null || venta.getMensualidad() == null) {
                throw new ValidationException("Esta venta no tiene mensualidades: aplica el depósito al saldo");
            }
            BigDecimal precio = precioVenta(id);
            BigDecimal enganche = venta.getEnganche() == null ? BigDecimal.ZERO : venta.getEnganche();
            List<VentaAportacion> aportaciones = ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(id);
            BigDecimal aportado = aportaciones.stream().map(VentaAportacion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            int regulares = venta.getPlazoMeses() - aportaciones.size();
            BigDecimal saldo = precio.subtract(enganche).subtract(monto).subtract(aportado);
            if (regulares <= 0 || saldo.signum() <= 0) {
                throw new ValidationException("El depósito cubre todo el saldo de las mensualidades: aplícalo al saldo");
            }
            BigDecimal nueva = saldo.divide(BigDecimal.valueOf(regulares), 2, java.math.RoundingMode.HALF_UP);
            venta.setMensualidad(nueva);
        }

        venta.setDepositoPendiente(null);
        for (VentaLote vl : ventaLoteRepository.findByVentaId(id)) {
            loteService.limpiarMontoApartado(vl.getLote().getId());
        }
        comisionService.sincronizar(venta);
        return toDto(obtenerEntidad(id));
    }

    /**
     * Elimina una venta por completo. Es destructivo, así que es exclusivo de admin y de
     * administración (líder de área) y exige la contraseña de quien la elimina. Se borran la venta, sus lotes (la relación, no los lotes), sus abonos y sus
     * aportaciones; los lotes que sigan VENDIDO vuelven a DISPONIBLE si se pide; la comisión se queda
     * en el historial como "venta eliminada" (y lo ya ganado sigue pudiéndose entregar); y se
     * ventas con número mayor bajan 1 para no dejar hueco (si eliminas la 4, la 5 pasa a 4, y así).
     */
    public void eliminar(Long id, VentaEliminarRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede eliminar una venta");
        }
        if (!passwordEncoder.matches(request.password(), actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        Venta venta = obtenerEntidad(id);
        ventaRepository.bloquearNumeracion();
        long numeroEliminado = venta.getNumero();

        comisionService.desvincularVenta(venta);
        // El registro de venta de We Forms que originó la venta se conserva, solo deja de apuntar a ella.
        weFormRespuestaRepository.desvincularVenta(id);

        List<VentaLote> lotes = ventaLoteRepository.findByVentaId(id);
        List<Long> loteIds = lotes.stream().map(vl -> vl.getLote().getId()).toList();
        pagoVentaRepository.deleteAll(pagoVentaRepository.findByVentaIdOrderByFechaDesc(id));
        pagoVentaRepository.flush();
        List<ReciboVenta> recibosDeVenta = reciboVentaRepository.findByVentaId(id);
        recibosDeVenta.forEach(r -> reciboHistorialRepository.deleteAll(reciboHistorialRepository.findByReciboId(r.getId())));
        reciboHistorialRepository.flush();
        reciboVentaRepository.deleteAll(recibosDeVenta);
        ventaAportacionRepository.deleteAll(ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(id));
        copropietarioRepository.deleteAll(copropietarioRepository.findByVentaIdOrderByOrdenAsc(id));
        ventaLoteRepository.deleteAll(lotes);
        ventaRepository.flush();
        ventaRepository.delete(venta);
        ventaRepository.flush();

        if (request.liberarLotes()) {
            for (Long loteId : loteIds) {
                // Si otra venta todavía incluye ese lote, sigue vendido.
                if (!ventaLoteRepository.existsByLoteId(loteId)) {
                    loteService.liberarPorVentaEliminada(loteId);
                }
            }
        }
        // Las ventas con número mayor bajan 1: la numeración no queda con hueco.
        ventaRepository.bajarDesde(numeroEliminado);
    }

    /** Para el ícono de "ver información de venta" en /panel/lotes: qué venta vendió este lote, si
     * alguna (un lote puede estar en VENDIDO sin venta asociada si alguien cambió el estado a mano
     * en vez de usar el módulo de ventas). */
    public VentaDto obtenerPorLote(Long loteId) {
        exigirAdminOLider();
        VentaLote ventaLote = ventaLoteRepository
                .findFirstByLoteIdOrderByIdDesc(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Este lote no tiene una venta registrada"));
        return toDto(ventaLote.getVenta());
    }

    /** ascendente = la venta más antigua primero (número 1 arriba); si no, la más reciente primero.
     * Ordena por número de venta (automático = mayor + 1, o el que se escribió a mano).
     * desarrolloId (opcional): solo las ventas con al menos un lote de ese desarrollo; una venta mixta
     * aparece en cada desarrollo que toca. */
    public PaginaDto<VentaDto> buscarPaginado(
            String busqueda, boolean ascendente, int pagina, int tamano, Long desarrolloId) {
        exigirAdminOLider();
        Specification<Venta> spec = (root, query, cb) -> cb.conjunction();

        if (desarrolloId != null) {
            spec = spec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<VentaLote> vl = sub.from(VentaLote.class);
                sub.select(vl.<Long>get("id"))
                        .where(
                                cb.equal(vl.get("venta"), root),
                                cb.equal(vl.get("lote").get("desarrollo").get("id"), desarrolloId));
                return cb.exists(sub);
            });
        }

        if (busqueda != null && !busqueda.isBlank()) {
            String comodin = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                // Join explícito en vez de root.get("usuarioAsesor").get(...): con una relación
                // nullable, get() genera un INNER JOIN implícito que excluiría del resultado toda
                // venta cuyo asesor sea del otro tipo.
                Join<Venta, Usuario> usuarioAsesor = root.join("usuarioAsesor", JoinType.LEFT);
                Join<Venta, AsesorExterno> asesorExterno = root.join("asesorExterno", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("cliente")), comodin),
                        cb.like(cb.lower(usuarioAsesor.get("nombre")), comodin),
                        cb.like(cb.lower(asesorExterno.get("nombre")), comodin));
            });
        }
        spec = spec.and((root, query, cb) -> {
            query.orderBy(ascendente ? cb.asc(root.get("numero")) : cb.desc(root.get("numero")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<Venta> resultado = ventaRepository.findAll(spec, pageable);
        return new PaginaDto<>(resultado.getContent().stream().map(this::toDto).toList(), resultado.hasNext());
    }

    /** Para los contadores de /panel/ventas: lotes vendidos (cada lote cuenta una vez) y ventas
     * registradas (una por operación, aunque incluya varios lotes). */
    public ContadoresVentasDto contadores(Long desarrolloId) {
        exigirAdminOLider();
        if (desarrolloId != null) {
            return new ContadoresVentasDto(
                    ventaLoteRepository.countLotesVendidosByDesarrolloId(desarrolloId),
                    ventaLoteRepository.countVentasByDesarrolloId(desarrolloId));
        }
        return new ContadoresVentasDto(ventaLoteRepository.countLotesVendidos(), ventaRepository.count());
    }

    public long contarLotesVendidosSamaiCampestre() {
        exigirAdminOLider();
        return ventaLoteRepository.countLotesVendidosByDesarrolloNombre(DESARROLLO_SAMAI_CAMPESTRE);
    }

    /** Abonos de una venta, más reciente primero. */
    public List<PagoVentaDto> listarPagos(Long ventaId) {
        exigirAdminOLider();
        obtenerEntidad(ventaId); // valida que la venta exista antes de listar sus pagos
        return pagoVentaRepository.findByVentaIdOrderByFechaDesc(ventaId).stream().map(PagoVentaDto::from).toList();
    }

    /** Registra un recibo con una o más líneas (cada una de un tipo). Se rechaza si el total excede el
     * saldo pendiente: un abono de más casi siempre es un error de captura (monto o venta
     * equivocada), no un pago real de más. Devuelve las líneas creadas. */
    public List<PagoVentaDto> registrarPago(Long ventaId, PagoVentaCreateRequest request) {
        Usuario actual = exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);

        BigDecimal totalRecibo = request.lineas().stream()
                .map(PagoVentaCreateRequest.Linea::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoPendiente = precioVenta(ventaId).subtract(totalAbonado(ventaId));
        if (totalRecibo.compareTo(saldoPendiente) > 0) {
            throw new ConflictException(
                    "El abono ($" + totalRecibo + ") excede el saldo pendiente ($" + saldoPendiente + ")");
        }

        String folio = request.folio().trim();
        if (reciboVentaRepository.existsByFolioIgnoreCaseAndAnuladoFalse(folio)) {
            throw new ConflictException("Ya existe un abono con el folio " + folio);
        }

        // Se validan todas las líneas antes de guardar nada.
        List<PagoVenta> lineas = new ArrayList<>();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
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
        Referencias ref = validarReferencias(venta, linea.tipo(), linea.meses(), linea.aportacionId());
        return new PagoVenta(venta, recibo, linea.monto(), linea.tipo(), ref.aportacion(), ref.meses());
    }

    private record Referencias(List<MesCubierto> meses, VentaAportacion aportacion) {
    }

    /** Mensualidad = meses dentro del plazo de la venta; aportación = una aportación programada de
     * esta misma venta; enganche y apartado, nada más. */
    private Referencias validarReferencias(
            Venta venta, TipoAbono tipo, List<PagoVentaCreateRequest.Mes> mesesPedidos, Long aportacionId) {
        boolean traeMeses = mesesPedidos != null && !mesesPedidos.isEmpty();

        if (tipo == TipoAbono.MENSUALIDAD) {
            if (venta.getPlazoMeses() == null || venta.getPlazoMeses() < 1 || venta.getMensualidad() == null) {
                throw new ValidationException("Esta venta no tiene mensualidades");
            }
            if (!traeMeses) {
                throw new ValidationException("Indica el mes o los meses que cubre la mensualidad");
            }
            if (aportacionId != null) {
                throw new ValidationException("Una mensualidad no se liga a una aportación");
            }
            YearMonth primero = YearMonth.from(venta.getFechaVenta()).plusMonths(venta.isPrimeraMensualidadMesVenta() ? 0 : 1);
            YearMonth ultimo = primero.plusMonths(venta.getPlazoMeses() - 1L);
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
            return new Referencias(meses, null);
        }

        if (traeMeses) {
            throw new ValidationException("Solo una mensualidad cubre meses");
        }
        if (tipo == TipoAbono.APORTACION) {
            if (aportacionId == null) {
                throw new ValidationException("Indica qué aportación programada paga");
            }
            VentaAportacion aportacion = ventaAportacionRepository.findById(aportacionId)
                    .filter(a -> a.getVenta().getId().equals(venta.getId()))
                    .orElseThrow(() -> new ValidationException("Esa aportación no pertenece a esta venta"));
            return new Referencias(List.of(), aportacion);
        }
        if (aportacionId != null) {
            throw new ValidationException("Solo una aportación se liga a una aportación programada");
        }
        return new Referencias(List.of(), null);
    }

    /** "Enganche $20,000 · Mensualidad $10,000 (2026-11, 2026-12)" para la bitácora. */
    private static String describirLineas(List<PagoVenta> lineas) {
        return lineas.stream().map(VentaService::describirLinea).collect(java.util.stream.Collectors.joining(" · "));
    }

    private static String describirLinea(PagoVenta l) {
        String tipo = l.getTipo() == null ? "Sin tipo" : switch (l.getTipo()) {
            case MENSUALIDAD -> "Mensualidad";
            case ENGANCHE -> "Enganche";
            case APARTADO -> "Apartado";
            case APORTACION -> "Aportación";
        };
        String meses = l.getMeses().isEmpty()
                ? ""
                : " (" + l.getMeses().stream()
                        .map(m -> YearMonth.of(m.getAnio(), m.getMes()).toString())
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(", ")) + ")";
        String aportacion = l.getAportacion() == null
                ? ""
                : " (" + YearMonth.of(l.getAportacion().getAnio(), l.getAportacion().getMes()) + ")";
        return tipo + " $" + l.getMonto().toPlainString() + meses + aportacion;
    }

    // ---------------------------------------------------------------- editar / anular un recibo

    /** Edita solo lo NO financiero de un recibo: folio, nota y, por línea, tipo, meses y aportación.
     * El monto y la fecha no se tocan (los libros no se reescriben): si están mal, se anula el recibo
     * y se registra uno nuevo. Un recibo anulado no se edita. Deja constancia en la bitácora. */
    public List<PagoVentaDto> editarRecibo(Long ventaId, Long reciboId, ReciboEditarRequest request) {
        Usuario actual = exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);
        ReciboVenta recibo = obtenerRecibo(ventaId, reciboId);
        if (recibo.isAnulado()) {
            throw new ConflictException("Un recibo anulado no se puede editar");
        }

        String folio = request.folio().trim();
        reciboVentaRepository.findByFolioIgnoreCaseAndAnuladoFalse(folio)
                .filter(otro -> !otro.getId().equals(recibo.getId()))
                .ifPresent(otro -> {
                    throw new ConflictException("Ya existe un abono con el folio " + folio);
                });

        List<PagoVenta> lineas = pagoVentaRepository.findByReciboId(reciboId);
        java.util.Map<Long, PagoVenta> porId = new java.util.HashMap<>();
        lineas.forEach(l -> porId.put(l.getId(), l));

        String antes = describirLineas(lineas);
        List<String> cambios = new ArrayList<>();
        if (!java.util.Objects.equals(recibo.getFolio(), folio)) {
            cambios.add("Folio " + (recibo.getFolio() == null ? "(vacío)" : recibo.getFolio()) + " → " + folio);
        }
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        if (!java.util.Objects.equals(recibo.getNotas(), notas)) {
            cambios.add("Nota actualizada");
        }

        // Se validan todas las líneas antes de guardar nada.
        List<Runnable> aplicar = new ArrayList<>();
        for (ReciboEditarRequest.Linea linea : request.lineas()) {
            PagoVenta pago = porId.get(linea.pagoId());
            if (pago == null) {
                throw new ValidationException("La línea " + linea.pagoId() + " no pertenece a este recibo");
            }
            Referencias ref = validarReferencias(venta, linea.tipo(), linea.meses(), linea.aportacionId());
            aplicar.add(() -> pago.actualizarConcepto(linea.tipo(), ref.aportacion(), ref.meses()));
        }

        recibo.setFolio(folio);
        recibo.setNotas(notas);
        aplicar.forEach(Runnable::run);
        pagoVentaRepository.saveAll(lineas);
        String despues = describirLineas(lineas);
        if (!antes.equals(despues)) {
            cambios.add("Conceptos: " + antes + " → " + despues);
        }
        reciboVentaRepository.save(recibo);
        reciboHistorialRepository.save(new ReciboVentaHistorial(
                recibo, AccionRecibo.EDITADO, actual, cambios.isEmpty() ? "Sin cambios" : String.join(" | ", cambios)));
        // El tipo de cada línea define cuánto aporta a la comisión (regla por tipo de abono).
        comisionService.sincronizar(venta);
        return pagoVentaRepository.findByReciboId(reciboId).stream().map(PagoVentaDto::from).toList();
    }

    /** Anula un recibo: no se borra, queda con su motivo, quién y cuándo. Su dinero deja de contar en
     * el saldo de la venta y en las comisiones; en Finanzas se registra un contra-asiento en la fecha
     * de hoy (el mes original no cambia). Si la comisión ya se había entregado, la entrega no se toca:
     * lo que ya no esté respaldado por abonos queda como "por devolver". */
    public List<PagoVentaDto> anularRecibo(Long ventaId, Long reciboId, ReciboAnularRequest request) {
        Usuario actual = exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);
        ReciboVenta recibo = obtenerRecibo(ventaId, reciboId);
        if (recibo.isAnulado()) {
            throw new ConflictException("Este recibo ya está anulado");
        }

        String motivo = request.motivo().trim();
        recibo.anular(motivo, actual, java.time.LocalDate.now());
        reciboVentaRepository.save(recibo);
        List<PagoVenta> lineas = pagoVentaRepository.findByReciboId(reciboId);
        reciboHistorialRepository.save(new ReciboVentaHistorial(
                recibo, AccionRecibo.ANULADO, actual, "Motivo: " + motivo + " · " + describirLineas(lineas)));
        comisionService.sincronizar(venta);
        return lineas.stream().map(PagoVentaDto::from).toList();
    }

    /** Bitácora de todos los recibos de la venta, la más reciente primero. */
    public List<ReciboHistorialDto> historialDeRecibos(Long ventaId) {
        exigirAdminOLider();
        obtenerEntidad(ventaId);
        return reciboHistorialRepository.findByVentaId(ventaId).stream().map(ReciboHistorialDto::from).toList();
    }

    private ReciboVenta obtenerRecibo(Long ventaId, Long reciboId) {
        return reciboVentaRepository.findById(reciboId)
                .filter(r -> r.getVenta().getId().equals(ventaId))
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado"));
    }

    private Venta obtenerEntidad(Long id) {
        return ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
    }

    /** Lo abonado de verdad: los recibos anulados ya no cuentan. */
    private BigDecimal totalAbonado(Long ventaId) {
        return pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(ventaId).stream()
                .map(PagoVenta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal precioVenta(Long ventaId) {
        return ventaLoteRepository.findByVentaId(ventaId).stream()
                .map(VentaLote::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private VentaDto toDto(Venta venta) {
        List<VentaLoteDto> lotes =
                ventaLoteRepository.findByVentaId(venta.getId()).stream().map(VentaLoteDto::from).toList();
        List<VentaAportacionDto> aportaciones = ventaAportacionRepository
                .findByVentaIdOrderByAnioAscMesAsc(venta.getId())
                .stream()
                .map(VentaAportacionDto::from)
                .toList();
        List<ClienteResumenDto> copropietarios = copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                .map(cp -> ClienteResumenDto.from(cp.getCliente()))
                .toList();
        return VentaDto.from(venta, venta.getNumero(), lotes, aportaciones, copropietarios, totalAbonado(venta.getId()));
    }

    /** Reemplaza los copropietarios de la venta: hasta 4 clientes distintos, activos y distintos del principal. */
    private void aplicarCopropietarios(Venta venta, List<Long> ids) {
        copropietarioRepository.deleteAll(copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()));
        copropietarioRepository.flush();
        if (ids == null || ids.isEmpty()) return;
        if (ids.size() > 4) throw new ValidationException("Una venta admite hasta 5 clientes (el principal y 4 copropietarios)");
        Set<Long> vistos = new HashSet<>();
        Long principalId = venta.getClienteRef() != null ? venta.getClienteRef().getId() : null;
        int orden = 1;
        for (Long id : ids) {
            if (id == null || !vistos.add(id)) throw new ValidationException("Un cliente no puede repetirse en la misma venta");
            if (id.equals(principalId)) throw new ValidationException("El cliente principal no puede ser también copropietario");
            Cliente c = clienteService.obtenerEntidad(id);
            if (!c.isActivo()) {
                throw new ValidationException("El cliente " + c.nombreCompleto() + " está inactivo: actívalo para registrarlo");
            }
            copropietarioRepository.save(new VentaCopropietario(venta, c, orden++));
        }
    }

    /** Deja libre el número que se quiere para una venta y devuelve el que le toca: sin número pedido, el
     * siguiente automático (mayor + 1); con número pedido, ese mismo — y si otra venta ya lo tiene, se
     * recorren +1 esa y las que le siguen de forma consecutiva (se detiene en el primer hueco). Va con
     * candado para que dos registros simultáneos no se pisen. exceptoId = la venta que se está moviendo
     * (0 si es una nueva) y numeroActual = el que tenía (-1 si es nueva): al irse, deja libre su lugar. */
    private long reservarNumero(Long pedido, long exceptoId) {
        return reservarNumero(pedido, exceptoId, -1L);
    }

    private long reservarNumero(Long pedido, long exceptoId, long numeroActual) {
        ventaRepository.bloquearNumeracion();
        if (pedido == null) {
            return ventaRepository.maxNumero() + 1;
        }
        long hasta = finDeLaRacha(pedido, numeroActual);
        if (hasta >= pedido) {
            ventaRepository.recorrer(pedido, hasta, exceptoId);
        }
        return pedido;
    }

    /** Último número de la racha de consecutivos que empieza en "desde" (sin contar el que se está
     * moviendo, que se va de su lugar); desde - 1 si "desde" está libre. */
    private long finDeLaRacha(long desde, long numeroIgnorado) {
        java.util.Set<Long> enUso = new java.util.HashSet<>(ventaRepository.numerosDesde(desde));
        enUso.remove(numeroIgnorado);
        long fin = desde - 1;
        while (enUso.contains(fin + 1)) fin++;
        return fin;
    }

    /** Para el formulario: el siguiente número automático y, si el número consultado ya está ocupado
     * (por una venta distinta de excluirId), cuál es y cuántas ventas se recorrerían. */
    @Transactional(readOnly = true)
    public NumeracionVentaDto consultarNumeracion(Long numero, Long excluirId) {
        exigirAdminOLider();
        long siguiente = ventaRepository.maxNumero() + 1;
        if (numero == null || numero < 1) {
            return new NumeracionVentaDto(siguiente, null, null, null, 0);
        }
        Venta ocupante = ventaRepository.findByNumero(numero).orElse(null);
        if (ocupante == null || ocupante.getId().equals(excluirId)) {
            return new NumeracionVentaDto(siguiente, null, null, null, 0);
        }
        Long ignorado = excluirId == null ? null : ventaRepository.findById(excluirId).map(Venta::getNumero).orElse(null);
        long fin = finDeLaRacha(numero, ignorado == null ? -1L : ignorado);
        return new NumeracionVentaDto(
                siguiente, ocupante.getId(), ocupante.getCliente(), ocupante.getFechaVenta(), (int) (fin - numero + 1));
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar ventas");
        }
        return actual;
    }

    /** Exactamente uno de usuarioAsesorId/asesorExternoId debe venir, y debe corresponder a un
     * asesor activo — así "solo se puede registrar de entre los asesores registrados" se cumple
     * de verdad en el servidor, no solo porque el <select> del frontend no deje escribir texto. */
    /** null = el 5% por defecto; si se indica, entre 0 y el máximo de 10% del valor de la venta. */
    private static void validarPorcentajeComision(BigDecimal porcentaje, String quien) {
        if (porcentaje == null) return;
        if (porcentaje.signum() < 0 || porcentaje.compareTo(ComisionService.PORCENTAJE_MAXIMO) > 0) {
            throw new ValidationException(
                    quien + " debe estar entre 0% y " + ComisionService.PORCENTAJE_MAXIMO.toPlainString() + "% del valor de la venta");
        }
    }

    private AsesorResuelto resolverAsesor(Long usuarioAsesorId, Long asesorExternoId) {
        boolean tieneInterno = usuarioAsesorId != null;
        boolean tieneExterno = asesorExternoId != null;
        if (tieneInterno == tieneExterno) {
            throw new ValidationException("Selecciona un asesor interno o externo (uno de los dos, no ambos)");
        }

        if (tieneInterno) {
            Usuario usuario = usuarioRepository.findById(usuarioAsesorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Asesor no encontrado"));
            if (!usuario.isActivo()) {
                throw new ValidationException("Ese asesor está inactivo");
            }
            return new AsesorResuelto(usuario, null);
        }

        AsesorExterno externo = asesorExternoRepository.findById(asesorExternoId)
                .orElseThrow(() -> new ResourceNotFoundException("Asesor externo no encontrado"));
        if (!externo.isActivo()) {
            throw new ValidationException("Ese asesor externo está inactivo");
        }
        return new AsesorResuelto(null, externo);
    }

    private record AsesorResuelto(Usuario usuario, AsesorExterno externo) {
        String nombre() {
            return usuario != null ? usuario.getNombre() : externo.getNombre();
        }
    }
}
