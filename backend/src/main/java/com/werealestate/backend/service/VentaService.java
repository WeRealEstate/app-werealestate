package com.werealestate.backend.service;

import com.werealestate.backend.dto.AplicarDepositoRequest;
import com.werealestate.backend.dto.ContadoresVentasDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaAportacionItemRequest;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.dto.VentaUpdateRequest;
import com.werealestate.backend.dto.VentaEliminarRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EtiquetaVenta;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final LoteVentaService loteVentaService;
    private final ReciboVentaService reciboVentaService;
    private final NumeracionVentaService numeracion;
    private final ComisionService comisionService;
    private final ClienteService clienteService;
    private final ClienteNumeracionService clienteNumeracionService;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final WeFormRespuestaRepository weFormRespuestaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsesorExternoRepository asesorExternoRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ConsultasVenta consultas;
    private final AutorizacionVentas autorizacion;
    private final VentaDtoArmador dtoArmador;

    public VentaService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            LoteRepository loteRepository,
            LoteVentaService loteVentaService,
            ReciboVentaService reciboVentaService,
            NumeracionVentaService numeracion,
            ComisionService comisionService,
            ClienteService clienteService,
            ClienteNumeracionService clienteNumeracionService,
            VentaCopropietarioRepository copropietarioRepository,
            WeFormRespuestaRepository weFormRespuestaRepository,
            UsuarioRepository usuarioRepository,
            AsesorExternoRepository asesorExternoRepository,
            CurrentUserProvider currentUserProvider,
            ConsultasVenta consultas,
            AutorizacionVentas autorizacion,
            VentaDtoArmador dtoArmador) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.loteRepository = loteRepository;
        this.loteVentaService = loteVentaService;
        this.reciboVentaService = reciboVentaService;
        this.numeracion = numeracion;
        this.comisionService = comisionService;
        this.clienteService = clienteService;
        this.clienteNumeracionService = clienteNumeracionService;
        this.copropietarioRepository = copropietarioRepository;
        this.weFormRespuestaRepository = weFormRespuestaRepository;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.currentUserProvider = currentUserProvider;
        this.consultas = consultas;
        this.autorizacion = autorizacion;
        this.dtoArmador = dtoArmador;
    }

    public VentaDto crear(VentaCreateRequest request) {
        autorizacion.exigirAdminOLider();
        return dtoArmador.armar(crearVenta(request, currentUserProvider.getUsuarioActual(), false));
    }

    /** La venta que nace de un registro de venta de We Forms (ver WeFormPublicoService): sin sesión, a nombre
     * del usuario de sistema; los lotes pasan a "En proceso de firma" (no a Vendido), la fecha queda
     * como provisional y el dinero con el que estuvieran apartados queda pendiente de aplicar. Quien la
     * llama ya validó el PIN del asesor, el cliente y que los lotes se puedan tomar. */
    public Venta crearDesdeRegistroDeVenta(VentaCreateRequest request, Usuario sistema) {
        return crearVenta(request, sistema, true);
    }

    private Venta crearVenta(VentaCreateRequest request, Usuario registradaPor, boolean registroDeVenta) {
        if (request.lotes() == null || request.lotes().isEmpty()) {
            throw new ValidationException("La venta necesita al menos un lote");
        }
        // El desarrollo con el que cuenta la venta: el de su primer lote (en una venta con lotes de dos desarrollos
        // manda el primero). Su número es consecutivo dentro de ese desarrollo.
        Long primerLoteId = request.lotes().get(0).loteId();
        Desarrollo desarrolloDeNumeracion = desarrolloDelLote(primerLoteId);
        // Primero el número (puede recorrer otras ventas y vacía el contexto de persistencia): todavía
        // no se ha cargado nada de esta operación.
        long numeroVenta = numeracion.reservarNumeroGlobal();
        Long numeroEnDesarrollo = ClienteNumeracionService.prefijo(desarrolloDeNumeracion.getNombre()) == null
                ? null
                : numeracion.reservarNumero(request.numero(), desarrolloDeNumeracion.getId(), 0L, -1L);
        // Recorrer números vacía el contexto de persistencia: se vuelve a leer el desarrollo para que siga administrado.
        desarrolloDeNumeracion = desarrolloDelLote(primerLoteId);

        validarLotesLibres(request.lotes());

        AsesorResuelto asesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());
        validarPorcentajeComision(request.comisionPorcentaje(), "La comisión del asesor");
        AsesorResuelto coasesor = resolverCoasesor(request, asesor);
        List<VentaAportacionItemRequest> aportaciones =
                request.aportaciones() == null ? List.of() : request.aportaciones();
        validarAportaciones(aportaciones, request);

        Cliente clienteRef = clienteService.obtenerEntidad(request.clienteId());
        if (!clienteRef.isActivo()) {
            throw new ValidationException("Ese cliente está inactivo: actívalo para registrarle una venta");
        }
        String cliente = clienteRef.nombreCompleto();
        boolean contratoFirmado = Boolean.TRUE.equals(request.contratoFirmado());

        Venta venta = new Venta(
                cliente, asesor.usuario(), asesor.externo(), request.formaPago().trim(), request.fechaVenta(),
                request.mensualidad(), request.plazoMeses(), Textos.opcional(request.engancheLabel()), request.enganche(),
                request.diaPago(), Boolean.TRUE.equals(request.primeraMensualidadMesVenta()),
                Textos.opcional(request.notas()));
        venta.setNumero(numeroVenta);
        venta.numerar(desarrolloDeNumeracion, numeroEnDesarrollo);
        venta.setModalidadPago(request.modalidadPago());
        venta.ligarCliente(clienteRef);
        venta.setRegistradaPor(registradaPor);
        String driveUrl = ContratoVentaService.validarContrato(
                contratoFirmado, request.contratoFechaFirma(), request.contratoMedio(), request.contratoDriveUrl());
        venta.registrarContrato(contratoFirmado, request.contratoFechaFirma(), request.contratoMedio(), driveUrl);
        venta = ventaRepository.save(venta);
        aplicarCopropietarios(venta, request.copropietariosIds());

        for (VentaAportacionItemRequest item : aportaciones) {
            ventaAportacionRepository.save(new VentaAportacion(venta, item.anio(), item.mes(), item.monto()));
        }

        BigDecimal depositoPendiente = guardarLotesDeLaVenta(
                venta, request.lotes(), cliente, asesor, registradaPor, registroDeVenta, contratoFirmado);
        if (registroDeVenta) {
            venta.setFechaProvisional(true);
            venta.setDepositoPendiente(depositoPendiente.signum() > 0 ? depositoPendiente : null);
        }
        // Número de cliente en el desarrollo de sus lotes (S-… / N-…) para el titular y los copropietarios que no tengan.
        clienteNumeracionService.asignarPorVenta(venta);

        // Una venta nueva usa la regla de comisión por tipo de abono (las existentes conservan la suya).
        comisionService.sincronizarVentaNueva(
                venta,
                request.comisionPorcentaje(),
                coasesor != null ? coasesor.usuario() : null,
                coasesor != null ? coasesor.externo() : null,
                request.coasesorComisionPorcentaje());
        return consultas.obtenerEntidad(venta.getId());
    }

    private Desarrollo desarrolloDelLote(Long loteId) {
        return loteRepository.findById(loteId)
                .orElseThrow(() -> new ValidationException("Lote no encontrado"))
                .getDesarrollo();
    }

    /** Un lote no se repite dentro de la venta ni puede estar ya en otra: la venta es la que manda el estado del lote
     * (ver LoteService.cambiarEstado). */
    private void validarLotesLibres(List<VentaLoteItemRequest> lotes) {
        Set<Long> loteIdsUnicos = new HashSet<>();
        for (VentaLoteItemRequest item : lotes) {
            if (!loteIdsUnicos.add(item.loteId())) {
                throw new ValidationException("El mismo lote no puede repetirse dentro de una venta");
            }
            ventaLoteRepository.findFirstByLoteIdOrderByIdDesc(item.loteId()).ifPresent(otra -> {
                throw new ConflictException("El lote Mz " + otra.getLote().getManzana() + " / Lote "
                        + otra.getLote().getNumeroLote() + " ya está en la venta " + otra.getVenta().getEtiqueta());
            });
        }
    }

    /** Copropietario (segundo asesor): opcional; distinto del asesor principal y con su propio %. */
    private AsesorResuelto resolverCoasesor(VentaCreateRequest request, AsesorResuelto principal) {
        if (request.coasesorUsuarioId() == null && request.coasesorExternoId() == null) {
            return null;
        }
        AsesorResuelto coasesor = resolverAsesor(request.coasesorUsuarioId(), request.coasesorExternoId());
        boolean mismoUsuario = coasesor.usuario() != null && principal.usuario() != null
                && coasesor.usuario().getId().equals(principal.usuario().getId());
        boolean mismoExterno = coasesor.externo() != null && principal.externo() != null
                && coasesor.externo().getId().equals(principal.externo().getId());
        if (mismoUsuario || mismoExterno) {
            throw new ValidationException("El copropietario debe ser distinto al asesor de la venta");
        }
        validarPorcentajeComision(request.coasesorComisionPorcentaje(), "La comisión del copropietario");
        return coasesor;
    }

    /** Liga los lotes a la venta y les pone su estado (desde el panel, según el contrato: firmado, Vendido; sin firmar,
     * En proceso de firma; desde un registro de venta, siempre En proceso de firma). Devuelve el dinero con el que
     * estuvieran apartados, que solo importa en un registro de venta. */
    private BigDecimal guardarLotesDeLaVenta(
            Venta venta,
            List<VentaLoteItemRequest> items,
            String cliente,
            AsesorResuelto asesor,
            Usuario registradaPor,
            boolean registroDeVenta,
            boolean contratoFirmado) {
        BigDecimal depositoPendiente = BigDecimal.ZERO;
        for (VentaLoteItemRequest item : items) {
            Lote lote = loteRepository
                    .findById(item.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
            ventaLoteRepository.save(new VentaLote(venta, lote, item.precio()));

            if (registroDeVenta) {
                if (lote.getMontoApartado() != null) depositoPendiente = depositoPendiente.add(lote.getMontoApartado());
                loteVentaService.marcarEnProcesoDeFirma(lote.getId(), cliente, asesor.nombre(), registradaPor);
            } else {
                loteVentaService.marcarPorVenta(lote.getId(), contratoFirmado, cliente, asesor.nombre());
            }
        }
        return depositoPendiente;
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

    public VentaDto obtener(Long id) {
        autorizacion.exigirAdminOLider();
        return dtoArmador.armar(consultas.obtenerEntidad(id));
    }

    /** Para abrir una venta desde su dirección (/panel/ventas/11): el número solo sirve para
     * encontrarla; todo lo que escribe (abonos, modificar) sigue usando el id estable de la venta
     * que devuelve esto, porque un número puede cambiar si se registra otra venta con fecha anterior. */
    public VentaDto obtenerPorNumero(String etiqueta) {
        autorizacion.exigirAdminOLider();
        EtiquetaVenta.Escrita escrita = EtiquetaVenta.leer(etiqueta)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
        return dtoArmador.armar(ventaRepository
                .findByDesarrolloNombreAndNumeroDesarrollo(EtiquetaVenta.nombreDe(escrita.prefijo()), escrita.numero())
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada")));
    }

    /** Modifica los datos capturados de una venta (cliente, fechas, términos de financiamiento);
     * no toca sus lotes ni precios. Temporal: ver Venta.actualizar. */
    public VentaDto actualizar(Long id, VentaUpdateRequest request) {
        autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(id);
        AsesorResuelto asesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());

        String engancheLabel = Textos.opcional(request.engancheLabel());
        String notas = Textos.opcional(request.notas());

        // Una venta registrada desde We Forms con fecha provisional deja de serlo al cambiar la fecha o al confirmarla.
        boolean fechaCambio = !request.fechaVenta().equals(venta.getFechaVenta());
        if (venta.isFechaProvisional() && (fechaCambio || Boolean.TRUE.equals(request.confirmarFecha()))) {
            venta.setFechaProvisional(false);
        }

        LocalDate fechaAnterior = venta.getFechaVenta();
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
        // El plan reestructurado de forma completa arranca en la fecha de venta: si esta cambió, lo sigue.
        if (fechaCambio) venta.seguirFechaDeVenta(fechaAnterior, consultas.pagosVigentes(venta.getId()));
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
        // Un titular o copropietario nuevo recibe su número de cliente en ese desarrollo (quien ya tenía, lo conserva).
        if (request.clienteId() != null || request.copropietariosIds() != null) {
            copropietarioRepository.flush();
            clienteNumeracionService.asignarPorVenta(venta);
        }
        comisionService.sincronizar(venta);
        // Si se pidió otro número, se mueve (recorriendo las consecutivas si estaba ocupado) y se vuelve a
        // leer la venta con el número nuevo. La fecha ya no cambia el número.
        if (request.numero() != null && venta.getDesarrollo() != null && venta.getNumeroDesarrollo() != null
                && !request.numero().equals(venta.getNumeroDesarrollo())) {
            long nuevo = numeracion.reservarNumero(request.numero(), venta.getDesarrollo().getId(), venta.getId(), venta.getNumeroDesarrollo());
            ventaRepository.fijarNumeroDesarrollo(venta.getId(), nuevo);
        }
        return dtoArmador.armar(consultas.obtenerEntidad(id));
    }

    /** Aplica el dinero con el que ya estaba apartado el lote de una venta que nació de un registro de venta
     * (ver Venta.depositoPendiente): SALDO lo registra como abono de tipo apartado (con su folio) y baja lo
     * que falta por pagar; MENSUALIDAD baja la mensualidad (mismo plazo, pago más bajo), igual que al
     * registrar una venta con depósito. Después el depósito ya no está pendiente. */
    public VentaDto aplicarDeposito(Long id, AplicarDepositoRequest request) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(id);
        BigDecimal monto = venta.getDepositoPendiente();
        if (monto == null || monto.signum() <= 0) {
            throw new ValidationException("Esta venta no tiene un depósito pendiente de aplicar");
        }

        if (request.aplicarA() == AplicarDepositoRequest.AplicarDepositoA.SALDO) {
            if (request.folio() == null || request.folio().isBlank()) {
                throw new ValidationException("Ingresa el folio del dinero recibido al apartar el lote");
            }
            reciboVentaService.registrarPago(id, new PagoVentaCreateRequest(
                    LocalDate.now(),
                    request.folio().trim(),
                    "Dinero recibido al apartar el lote",
                    List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, monto, null, null, null))));
        } else {
            if (venta.getPlazoMeses() == null || venta.getMensualidad() == null) {
                throw new ValidationException("Esta venta no tiene mensualidades: aplica el depósito al saldo");
            }
            BigDecimal precio = consultas.precioVenta(id);
            BigDecimal enganche = venta.getEnganche() == null ? BigDecimal.ZERO : venta.getEnganche();
            List<VentaAportacion> aportaciones = ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(id);
            BigDecimal aportado = aportaciones.stream().map(VentaAportacion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            int regulares = venta.getPlazoMeses() - aportaciones.size();
            BigDecimal saldo = precio.subtract(enganche).subtract(monto).subtract(aportado);
            if (regulares <= 0 || saldo.signum() <= 0) {
                throw new ValidationException("El depósito cubre todo el saldo de las mensualidades: aplícalo al saldo");
            }
            BigDecimal nueva = saldo.divide(BigDecimal.valueOf(regulares), 2, RoundingMode.HALF_UP);
            venta.setMensualidad(nueva);
        }

        venta.setDepositoPendiente(null);
        for (VentaLote vl : ventaLoteRepository.findByVentaId(id)) {
            loteVentaService.limpiarMontoApartado(vl.getLote().getId());
        }
        comisionService.sincronizar(venta);
        return dtoArmador.armar(consultas.obtenerEntidad(id));
    }

    /**
     * Elimina una venta por completo. Es destructivo, así que es exclusivo de admin y de
     * administración (líder de área) y exige la contraseña de quien la elimina. Se borran la venta, sus lotes (la relación, no los lotes), sus abonos y sus
     * aportaciones; sus lotes Vendidos o En proceso de firma vuelven a DISPONIBLE; la comisión se queda
     * en el historial como "venta eliminada" (y lo ya ganado sigue pudiéndose entregar); y las
     * ventas con número mayor bajan 1 para no dejar hueco (si eliminas la 4, la 5 pasa a 4, y así).
     */
    public void eliminar(Long id, VentaEliminarRequest request) {
        autorizacion.exigirAdminOLiderConContrasena(
                "Solo un administrador o personal de administración puede eliminar una venta", request.password());
        eliminarYaAutorizada(id);
    }

    /**
     * Elimina la venta sin volver a validar rol ni contraseña: solo para quien ya lo hizo (eliminar() y la eliminación
     * de un cliente con sus ventas, EliminacionClienteService). Las consultas de numeración vacían el contexto de
     * persistencia, así que cada venta se vuelve a leer por su id.
     */
    public void eliminarYaAutorizada(Long id) {
        Venta venta = consultas.obtenerEntidad(id);
        ventaRepository.bloquearNumeracion();
        long numeroEliminado = venta.getNumeroGlobal();
        Long desarrolloEliminado = venta.getDesarrollo() != null ? venta.getDesarrollo().getId() : null;
        Long numeroDesarrolloEliminado = venta.getNumeroDesarrollo();

        comisionService.desvincularVenta(venta);
        // El registro de venta de We Forms que originó la venta se conserva, solo deja de apuntar a ella.
        weFormRespuestaRepository.desvincularVenta(id);

        List<VentaLote> lotes = ventaLoteRepository.findByVentaId(id);
        List<Long> loteIds = lotes.stream().map(vl -> vl.getLote().getId()).toList();
        reciboVentaService.borrarDeVenta(id);
        ventaAportacionRepository.deleteAll(ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(id));
        copropietarioRepository.deleteAll(copropietarioRepository.findByVentaIdOrderByOrdenAsc(id));
        ventaLoteRepository.deleteAll(lotes);
        ventaRepository.flush();
        ventaRepository.delete(venta);
        ventaRepository.flush();

        // Sin venta un lote no puede seguir Vendido ni En proceso de firma: vuelve a Disponible. Si otra venta todavía
        // lo incluye (datos de antes de que se impidiera), se queda como está.
        for (Long loteId : loteIds) {
            if (!ventaLoteRepository.existsByLoteId(loteId)) {
                loteVentaService.liberarPorVentaEliminada(loteId);
            }
        }
        // Las ventas con número mayor bajan 1 (el global y el de su desarrollo): la numeración no queda con hueco.
        numeracion.cerrarHuecoDeVentaEliminada(numeroEliminado, desarrolloEliminado, numeroDesarrolloEliminado);
    }

    /** Para el ícono de "ver información de venta" en /panel/lotes: qué venta vendió este lote, si
     * alguna (un lote puede estar en VENDIDO sin venta asociada si alguien cambió el estado a mano
     * en vez de usar el módulo de ventas). */
    public VentaDto obtenerPorLote(Long loteId) {
        autorizacion.exigirAdminOLider();
        VentaLote ventaLote = ventaLoteRepository
                .findFirstByLoteIdOrderByIdDesc(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Este lote no tiene una venta registrada"));
        return dtoArmador.armar(ventaLote.getVenta());
    }

    /** ascendente = la venta más antigua primero (número 1 arriba); si no, la más reciente primero.
     * Ordena por número de venta (automático = mayor + 1, o el que se escribió a mano).
     * desarrolloId (opcional): solo las ventas que cuentan en ese desarrollo (el de su primer lote, ver
     * EtiquetaVenta); una venta con lotes de dos desarrollos aparece solo en el de su primer lote. */
    public PaginaDto<VentaDto> buscarPaginado(
            String busqueda, boolean ascendente, int pagina, int tamano, Long desarrolloId) {
        autorizacion.exigirAdminOLider();
        Specification<Venta> spec = (root, query, cb) -> cb.conjunction();

        if (desarrolloId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("desarrollo").get("id"), desarrolloId));
        }

        if (busqueda != null && !busqueda.isBlank()) {
            String comodin = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                // Join explícito en vez de root.get("usuarioAsesor").get(...): con una relación
                // nullable, get() genera un INNER JOIN implícito que excluiría del resultado toda
                // venta cuyo asesor sea del otro tipo.
                Join<Venta, Usuario> usuarioAsesor = root.join("usuarioAsesor", JoinType.LEFT);
                Join<Venta, AsesorExterno> asesorExterno = root.join("asesorExterno", JoinType.LEFT);
                List<Predicate> opciones = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("cliente")), comodin),
                        cb.like(cb.lower(usuarioAsesor.get("nombre")), comodin),
                        cb.like(cb.lower(asesorExterno.get("nombre")), comodin)));
                // El número de venta: "S-12" / "n5" (letra y número) o solo "12" (ese número en cualquier desarrollo).
                String texto = busqueda.trim();
                EtiquetaVenta.leer(texto).ifPresent(e -> opciones.add(cb.and(
                        cb.equal(root.get("desarrollo").get("nombre"), EtiquetaVenta.nombreDe(e.prefijo())),
                        cb.equal(root.get("numeroDesarrollo"), e.numero()))));
                String soloNumero = texto.startsWith("#") ? texto.substring(1) : texto;
                if (!soloNumero.isEmpty() && soloNumero.length() <= 8 && soloNumero.chars().allMatch(Character::isDigit)) {
                    opciones.add(cb.equal(root.get("numeroDesarrollo"), Long.parseLong(soloNumero)));
                }
                return cb.or(opciones.toArray(Predicate[]::new));
            });
        }
        spec = spec.and((root, query, cb) -> {
            // Dentro de un desarrollo, por su número; sin filtro de desarrollo, SAMAI antes que Nanuu (por el id del
            // desarrollo) y luego por número.
            query.orderBy(
                    cb.asc(root.get("desarrollo").get("id")),
                    ascendente ? cb.asc(root.get("numeroDesarrollo")) : cb.desc(root.get("numeroDesarrollo")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<Venta> resultado = ventaRepository.findAll(spec, pageable);
        return new PaginaDto<>(dtoArmador.armarPagina(resultado.getContent()), resultado.hasNext());
    }

    /** Para los contadores de /panel/ventas: lotes vendidos (cada lote cuenta una vez) y ventas
     * registradas (una por operación, aunque incluya varios lotes). */
    public ContadoresVentasDto contadores(Long desarrolloId) {
        autorizacion.exigirAdminOLider();
        if (desarrolloId != null) {
            return new ContadoresVentasDto(
                    ventaLoteRepository.countLotesVendidosByDesarrolloId(desarrolloId),
                    ventaLoteRepository.countVentasByDesarrolloId(desarrolloId));
        }
        return new ContadoresVentasDto(ventaLoteRepository.countLotesVendidos(), ventaRepository.count());
    }

    public long contarLotesVendidosSamaiCampestre() {
        autorizacion.exigirAdminOLider();
        return ventaLoteRepository.countLotesVendidosByDesarrolloNombre(DESARROLLO_SAMAI_CAMPESTRE);
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

    /** null = el 5% por defecto; si se indica, entre 0 y el máximo de 10% del valor de la venta. */
    private static void validarPorcentajeComision(BigDecimal porcentaje, String quien) {
        if (porcentaje == null) return;
        if (porcentaje.signum() < 0 || porcentaje.compareTo(ComisionService.PORCENTAJE_MAXIMO) > 0) {
            throw new ValidationException(
                    quien + " debe estar entre 0% y " + ComisionService.PORCENTAJE_MAXIMO.toPlainString() + "% del valor de la venta");
        }
    }

    /** Exactamente uno de usuarioAsesorId/asesorExternoId debe venir, y debe corresponder a un
     * asesor activo — así "solo se puede registrar de entre los asesores registrados" se cumple
     * de verdad en el servidor, no solo porque el <select> del frontend no deje escribir texto. */
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
