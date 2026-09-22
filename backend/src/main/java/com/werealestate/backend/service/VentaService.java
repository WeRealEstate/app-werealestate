package com.werealestate.backend.service;

import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
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
 * Registro de ventas cerradas. Cliente y asesor son texto libre a propósito (ver Venta): no todo
 * comprador pasó por el CRM como lead y no todo asesor que vende tiene cuenta en el sistema. Una
 * venta puede incluir varios lotes (misma operación, una sola mensualidad/plazo/saldo combinado,
 * ver VentaLote). Exclusivo de admin y líder de área, igual que los estados de lote comprometidos
 * con dinero real.
 */
@Service
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final LoteRepository loteRepository;
    private final LoteService loteService;
    private final PagoVentaRepository pagoVentaRepository;
    private final CurrentUserProvider currentUserProvider;

    public VentaService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            LoteRepository loteRepository,
            LoteService loteService,
            PagoVentaRepository pagoVentaRepository,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.loteRepository = loteRepository;
        this.loteService = loteService;
        this.pagoVentaRepository = pagoVentaRepository;
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

        String cliente = request.cliente().trim();
        String asesor = request.asesor().trim();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();

        Venta venta = new Venta(
                cliente, asesor, request.formaPago().trim(), request.fechaVenta(), request.mensualidad(),
                request.plazoMeses(), notas);
        venta = ventaRepository.save(venta);

        for (VentaLoteItemRequest item : request.lotes()) {
            Lote lote = loteRepository
                    .findById(item.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
            ventaLoteRepository.save(new VentaLote(venta, lote, item.precio()));

            if (request.marcarLoteVendido()) {
                loteService.marcarVendido(lote.getId(), cliente, asesor);
            }
        }

        return toDto(venta);
    }

    public VentaDto obtener(Long id) {
        exigirAdminOLider();
        return toDto(obtenerEntidad(id));
    }

    public PaginaDto<VentaDto> buscarPaginado(String busqueda, int pagina, int tamano) {
        exigirAdminOLider();
        Specification<Venta> spec = (root, query, cb) -> cb.conjunction();

        if (busqueda != null && !busqueda.isBlank()) {
            String comodin = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("cliente")), comodin), cb.like(cb.lower(root.get("asesor")), comodin)));
        }
        spec = spec.and((root, query, cb) -> {
            query.orderBy(cb.desc(root.get("fechaVenta")), cb.desc(root.get("id")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<Venta> resultado = ventaRepository.findAll(spec, pageable);
        return new PaginaDto<>(resultado.getContent().stream().map(this::toDto).toList(), resultado.hasNext());
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

        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        PagoVenta pago = new PagoVenta(venta, request.fecha(), request.monto(), notas, actual);
        return PagoVentaDto.from(pagoVentaRepository.save(pago));
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
        return VentaDto.from(venta, lotes, totalAbonado(venta.getId()));
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o líder de área puede gestionar ventas");
        }
        return actual;
    }
}
