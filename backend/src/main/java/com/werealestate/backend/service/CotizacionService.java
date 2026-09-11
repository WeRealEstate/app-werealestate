package com.werealestate.backend.service;

import com.werealestate.backend.dto.CotizacionCreateRequest;
import com.werealestate.backend.dto.CotizacionDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Cotizacion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.CotizacionRepository;
import com.werealestate.backend.repository.UsuarioRepository;
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

    /** Usuario "de sistema" (ver migración V17) al que se atribuyen las cotizaciones generadas
     * desde /cotizador-publico, donde no hay una sesión real detrás. */
    private static final String EMAIL_USUARIO_COTIZADOR_PUBLICO = "cotizador-publico@weinversiones.com";

    private final CotizacionRepository cotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;

    public CotizacionService(
            CotizacionRepository cotizacionRepository,
            UsuarioRepository usuarioRepository,
            CurrentUserProvider currentUserProvider) {
        this.cotizacionRepository = cotizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public CotizacionDto registrar(CotizacionCreateRequest request) {
        return guardar(currentUserProvider.getUsuarioActual(), request);
    }

    /** Igual que {@link #registrar}, pero para /cotizador-publico: sin sesión iniciada, así que
     * se atribuye al usuario de sistema en vez de buscar un autenticado. */
    public CotizacionDto registrarPublica(CotizacionCreateRequest request) {
        Usuario sistema = usuarioRepository
                .findByEmail(EMAIL_USUARIO_COTIZADOR_PUBLICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el usuario de sistema del cotizador público (migración V17)"));

        return guardar(sistema, request);
    }

    private CotizacionDto guardar(Usuario asesor, CotizacionCreateRequest request) {
        Cotizacion cotizacion = new Cotizacion(
                asesor,
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
