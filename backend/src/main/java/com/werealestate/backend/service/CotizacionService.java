package com.werealestate.backend.service;

import com.werealestate.backend.dto.CotizacionCreateRequest;
import com.werealestate.backend.dto.CotizacionDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Cotizacion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.CotizacionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora de cotizaciones generadas desde el Cotizador. Cualquier asesor o admin que genera o
 * comparte un PDF registra aquí una fila; solo el admin puede consultar el historial completo.
 */
@Service
@Transactional
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final CurrentUserProvider currentUserProvider;

    public CotizacionService(CotizacionRepository cotizacionRepository, CurrentUserProvider currentUserProvider) {
        this.cotizacionRepository = cotizacionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public CotizacionDto registrar(CotizacionCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();

        Cotizacion cotizacion = new Cotizacion(
                actual,
                request.proyecto(),
                request.nombreCliente(),
                request.manzana(),
                request.lote(),
                request.superficie(),
                request.precioM2(),
                request.precioTotal(),
                request.formaPago(),
                request.engancheLabel(),
                request.enganche(),
                request.montoFinanciado(),
                request.meses(),
                request.mensualidad(),
                request.interesPorcentaje(),
                request.interesMonto(),
                request.totalInversion());

        return CotizacionDto.from(cotizacionRepository.save(cotizacion));
    }

    public List<CotizacionDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede ver el historial de cotizaciones");
        }

        return cotizacionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(CotizacionDto::from)
                .toList();
    }

    /** Versión paginada y con filtros de {@link #listar()}: carga por lotes ("Cargar más") y la
     * búsqueda/filtros corren en el servidor sobre el historial completo. */
    public PaginaDto<CotizacionDto> buscarPaginado(String busqueda, Long asesorId, String proyecto, int pagina, int tamano) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede ver el historial de cotizaciones");
        }

        Specification<Cotizacion> spec = (root, query, cb) -> cb.conjunction();

        if (busqueda != null && !busqueda.isBlank()) {
            String comodin = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nombreCliente")), comodin));
        }
        if (asesorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("asesor").get("id"), asesorId));
        }
        if (proyecto != null && !proyecto.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("proyecto"), proyecto));
        }

        Pageable pageable = PageRequest.of(
                Math.max(pagina, 0), Math.max(tamano, 1), Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        Page<Cotizacion> resultado = cotizacionRepository.findAll(spec, pageable);

        List<CotizacionDto> contenido = resultado.getContent().stream().map(CotizacionDto::from).toList();
        return new PaginaDto<>(contenido, resultado.hasNext());
    }
}
