package com.werealestate.backend.service;

import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de ventas cerradas. Cliente y asesor son texto libre a propósito (ver Venta): no todo
 * comprador pasó por el CRM como lead y no todo asesor que vende tiene cuenta en el sistema. Exclusivo
 * de admin y líder de área, igual que los estados de lote comprometidos con dinero real.
 */
@Service
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final LoteRepository loteRepository;
    private final LoteService loteService;
    private final CurrentUserProvider currentUserProvider;

    public VentaService(
            VentaRepository ventaRepository,
            LoteRepository loteRepository,
            LoteService loteService,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.loteRepository = loteRepository;
        this.loteService = loteService;
        this.currentUserProvider = currentUserProvider;
    }

    public VentaDto crear(VentaCreateRequest request) {
        exigirAdminOLider();
        Lote lote = loteRepository
                .findById(request.loteId())
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));

        String cliente = request.cliente().trim();
        String asesor = request.asesor().trim();
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();

        Venta venta = new Venta(
                lote, cliente, asesor, request.precioVenta(), request.formaPago().trim(), request.fechaVenta(), notas);
        venta = ventaRepository.save(venta);

        if (request.marcarLoteVendido()) {
            loteService.marcarVendido(lote.getId(), cliente, asesor);
        }

        return VentaDto.from(venta);
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
        return new PaginaDto<>(resultado.getContent().stream().map(VentaDto::from).toList(), resultado.hasNext());
    }

    private void exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o líder de área puede gestionar ventas");
        }
    }
}
