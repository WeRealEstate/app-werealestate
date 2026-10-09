package com.werealestate.backend.service;

import com.werealestate.backend.dto.ContadoresVentasDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
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
import com.werealestate.backend.model.PagoVenta;
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
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.YearMonth;
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
    private final ComisionService comisionService;
    private final ClienteService clienteService;
    private final VentaCopropietarioRepository copropietarioRepository;
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
            ComisionService comisionService,
            ClienteService clienteService,
            VentaCopropietarioRepository copropietarioRepository,
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
        this.comisionService = comisionService;
        this.clienteService = clienteService;
        this.copropietarioRepository = copropietarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public VentaDto crear(VentaCreateRequest request) {
        exigirAdminOLider();

        Set<Long> loteIdsUnicos = new HashSet<>();
        for (VentaLoteItemRequest item : request.lotes()) {
            if (!loteIdsUnicos.add(item.loteId())) {
                throw new ValidationException("El mismo lote no puede repetirse dentro de una venta");
            }
        }

        AsesorResuelto asesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());
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
        venta.ligarCliente(clienteRef);
        venta.setRegistradaPor(currentUserProvider.getUsuarioActual());
        venta = ventaRepository.save(venta);
        aplicarCopropietarios(venta, request.copropietariosIds());

        for (VentaAportacionItemRequest item : aportaciones) {
            ventaAportacionRepository.save(new VentaAportacion(venta, item.anio(), item.mes(), item.monto()));
        }

        for (VentaLoteItemRequest item : request.lotes()) {
            Lote lote = loteRepository
                    .findById(item.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
            ventaLoteRepository.save(new VentaLote(venta, lote, item.precio()));

            if (request.marcarLoteVendido()) {
                loteService.marcarVendido(lote.getId(), cliente, asesor.nombre());
            }
        }

        comisionService.sincronizar(venta);
        return toDto(renumerar(venta.getId()));
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

        BigDecimal precio =
                request.lotes().stream().map(VentaLoteItemRequest::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(precio) >= 0) {
            throw new ValidationException("Las aportaciones no pueden sumar todo el precio de la venta");
        }
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
        // La fecha pudo cambiar: se reacomodan los números y se vuelve a leer con el nuevo.
        return toDto(renumerar(venta.getId()));
    }

    /**
     * Elimina una venta por completo. Es destructivo, así que es exclusivo de admin y exige su
     * contraseña. Se borran la venta, sus lotes (la relación, no los lotes), sus abonos y sus
     * aportaciones; los lotes que sigan VENDIDO vuelven a DISPONIBLE si se pide; la comisión se queda
     * en el historial como "venta eliminada" (y lo ya ganado sigue pudiéndose entregar); y se
     * reacomodan los números de las demás ventas.
     */
    public void eliminar(Long id, VentaEliminarRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede eliminar una venta");
        }
        if (!passwordEncoder.matches(request.password(), actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        Venta venta = obtenerEntidad(id);
        ventaRepository.bloquearNumeracion();

        comisionService.desvincularVenta(venta);

        List<VentaLote> lotes = ventaLoteRepository.findByVentaId(id);
        List<Long> loteIds = lotes.stream().map(vl -> vl.getLote().getId()).toList();
        pagoVentaRepository.deleteAll(pagoVentaRepository.findByVentaIdOrderByFechaDesc(id));
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
        ventaRepository.renumerar();
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
     * Ordena por número de venta, que ya sigue la fecha de venta (ver VentaRepository.renumerar). */
    public PaginaDto<VentaDto> buscarPaginado(String busqueda, boolean ascendente, int pagina, int tamano) {
        exigirAdminOLider();
        Specification<Venta> spec = (root, query, cb) -> cb.conjunction();

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
    public ContadoresVentasDto contadores() {
        exigirAdminOLider();
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

    /** Registra un abono. Se rechaza si excede el saldo pendiente: un abono de más casi siempre es
     * un error de captura (monto o venta equivocada), no un pago real de más. */
    public PagoVentaDto registrarPago(Long ventaId, PagoVentaCreateRequest request) {
        Usuario actual = exigirAdminOLider();
        Venta venta = obtenerEntidad(ventaId);

        BigDecimal saldoPendiente = precioVenta(ventaId).subtract(totalAbonado(ventaId));
        if (request.monto().compareTo(saldoPendiente) > 0) {
            throw new ConflictException(
                    "El abono ($" + request.monto() + ") excede el saldo pendiente ($" + saldoPendiente + ")");
        }

        String folio = request.folio().trim();
        if (pagoVentaRepository.existsByFolioIgnoreCase(folio)) {
            throw new ConflictException("Ya existe un abono con el folio " + folio);
        }

        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        PagoVenta pago = new PagoVenta(venta, request.fecha(), request.monto(), folio, notas, actual);
        PagoVentaDto guardado = PagoVentaDto.from(pagoVentaRepository.save(pago));
        comisionService.sincronizar(venta);
        return guardado;
    }

    private Venta obtenerEntidad(Long id) {
        return ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
    }

    private BigDecimal totalAbonado(Long ventaId) {
        return pagoVentaRepository.findByVentaIdOrderByFechaDesc(ventaId).stream()
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

    /** Reacomoda el número de todas las ventas por fecha y devuelve la venta pedida ya leída de nuevo
     * (con su número definitivo). Va con candado para que dos registros simultáneos no se pisen. */
    private Venta renumerar(Long ventaId) {
        ventaRepository.bloquearNumeracion();
        ventaRepository.renumerar();
        return obtenerEntidad(ventaId);
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
