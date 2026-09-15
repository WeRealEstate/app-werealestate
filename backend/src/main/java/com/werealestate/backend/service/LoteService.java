package com.werealestate.backend.service;

import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteCreateRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.LoteImportBatchRequest;
import com.werealestate.backend.dto.LoteImportError;
import com.werealestate.backend.dto.LoteImportRequest;
import com.werealestate.backend.dto.LoteImportResultado;
import com.werealestate.backend.dto.LoteUpdateRequest;
import com.werealestate.backend.dto.MovimientoLoteDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los estados APARTADO_CON_DINERO y EN_PROCESO_DE_FIRMA solo los puede establecer un admin;
 * DISPONIBLE y APARTADO los puede mover cualquiera (es el uso diario de un asesor). Dar de
 * alta/editar/importar lotes es exclusivo de admin. */
@Service
@Transactional
public class LoteService {

    private static final Set<EstadoLote> ESTADOS_SOLO_ADMIN =
            Set.of(EstadoLote.APARTADO_CON_DINERO, EstadoLote.EN_PROCESO_DE_FIRMA);

    /** Compara manzana/número de lote como números cuando se puede (así "2" queda antes que "10"
     * en vez del orden alfabético de VARCHAR, donde "10" queda antes que "2"); si alguno no es
     * numérico, cae a orden alfabético normal. */
    private static final Comparator<String> ORDEN_NATURAL = (a, b) -> {
        try {
            return Integer.compare(Integer.parseInt(a.trim()), Integer.parseInt(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    };

    private static final Comparator<Lote> ORDEN_NATURAL_LOTES = Comparator.comparing(Lote::getManzana, ORDEN_NATURAL)
            .thenComparing(Lote::getNumeroLote, ORDEN_NATURAL);

    /** Mismo usuario "de sistema" (ver migración V17) que atribuye las cotizaciones generadas desde
     * /cotizador-publico: aquí se reutiliza para atribuir los cambios de estado que haga un
     * visitante público en /cotizador-publico/lotes, donde tampoco hay una sesión real detrás. */
    private static final String EMAIL_USUARIO_PUBLICO = "cotizador-publico@weinversiones.com";

    /** Nombre exacto de Desarrollo (ver catálogo sembrado) para cada valor de "proyecto" que usa
     * el público en /cotizador-publico/lotes — el mismo mapeo que ya usa CotizadorComponent. */
    private static final Map<String, String> DESARROLLO_POR_PROYECTO =
            Map.of("samai", "SAMAI Campestre", "nanuu", "Aldea Nanuu");

    private final LoteRepository loteRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final CurrentUserProvider currentUserProvider;

    public LoteService(
            LoteRepository loteRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            MovimientoLoteRepository movimientoLoteRepository,
            CurrentUserProvider currentUserProvider) {
        this.loteRepository = loteRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Único punto que cambia el estado de un lote Y dejar registro en la bitácora: así el
     * historial siempre queda consistente con el estado real del lote, sin importar desde dónde se
     * haya originado el cambio (asesor/admin autenticado, visitante público o el revertido
     * automático por vencimiento). */
    private void cambiarEstadoConHistorial(Lote lote, EstadoLote nuevoEstado, Usuario usuario, String nombreAsesor) {
        EstadoLote anterior = lote.getEstado();
        lote.cambiarEstado(nuevoEstado, usuario);
        movimientoLoteRepository.save(new MovimientoLote(lote, anterior, nuevoEstado, usuario, nombreAsesor));
    }

    public PaginaDto<LoteDto> buscarPaginado(
            String manzana,
            String numeroLote,
            Long desarrolloId,
            String estado,
            BigDecimal superficieMin,
            BigDecimal superficieMax,
            int pagina,
            int tamano) {
        Specification<Lote> spec = (root, query, cb) -> cb.conjunction();

        if (manzana != null && !manzana.isBlank()) {
            String comodin = "%" + manzana.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("manzana")), comodin));
        }
        if (numeroLote != null && !numeroLote.isBlank()) {
            String comodin = "%" + numeroLote.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("numeroLote")), comodin));
        }
        if (desarrolloId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("desarrollo").get("id"), desarrolloId));
        }
        if (estado != null && !estado.isBlank()) {
            EstadoLote estadoEnum = EstadoLote.valueOf(estado);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estadoEnum));
        }
        if (superficieMin != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("superficie"), superficieMin));
        }
        if (superficieMax != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("superficie"), superficieMax));
        }

        // Manzana y número de lote son VARCHAR, así que un ORDER BY normal los trata como texto
        // ("10" antes que "2"). Ordenar primero por longitud y luego alfabéticamente da el orden
        // numérico esperado para los valores puramente numéricos que se capturan en la práctica.
        spec = spec.and((root, query, cb) -> {
            query.orderBy(
                    cb.asc(cb.length(root.get("manzana"))),
                    cb.asc(root.get("manzana")),
                    cb.asc(cb.length(root.get("numeroLote"))),
                    cb.asc(root.get("numeroLote")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<Lote> resultado = loteRepository.findAll(spec, pageable);
        return new PaginaDto<>(resultado.getContent().stream().map(LoteDto::from).toList(), resultado.hasNext());
    }

    /** Lotes disponibles de un desarrollo, para elegir uno al cotizar. Se reordenan en Java con
     * ORDEN_NATURAL_LOTES (no es una lista paginada, así que no hace falta resolverlo en SQL). */
    public List<LoteDto> listarDisponibles(Long desarrolloId) {
        return loteRepository
                .findByDesarrolloIdAndEstadoOrderByManzanaAscNumeroLoteAsc(desarrolloId, EstadoLote.DISPONIBLE)
                .stream()
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::from)
                .toList();
    }

    /** Todos los lotes de un desarrollo (cualquier estado) para /cotizador-publico/lotes: a
     * diferencia de {@link #listarDisponibles}, aquí también se muestran los apartados para que el
     * visitante vea la disponibilidad real, aunque solo pueda actuar sobre los disponibles. */
    public List<LoteDto> listarPublicoPorProyecto(String proyecto) {
        String nombreDesarrollo = DESARROLLO_POR_PROYECTO.get(proyecto == null ? "" : proyecto.trim().toLowerCase());
        if (nombreDesarrollo == null) {
            throw new ResourceNotFoundException("Proyecto no válido");
        }
        Desarrollo desarrollo = desarrolloRepository
                .findByNombre(nombreDesarrollo)
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        return loteRepository.findByDesarrolloId(desarrollo.getId()).stream()
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::from)
                .toList();
    }

    /** Igual que {@link #cambiarEstado}, pero para /cotizador-publico/lotes: sin sesión iniciada,
     * así que un visitante nunca puede tocar un lote exclusivo de admin (ni para entrar ni para
     * salir de esos estados), el cambio se atribuye al usuario de sistema, y apartar exige el
     * nombre del asesor que atendió al visitante (para liberar no hace falta). */
    public LoteDto cambiarEstadoPublico(Long id, CambiarEstadoLotePublicoRequest request) {
        Lote lote = obtenerEntidad(id);

        boolean tocaEstadoDeAdmin =
                ESTADOS_SOLO_ADMIN.contains(request.estado()) || ESTADOS_SOLO_ADMIN.contains(lote.getEstado());
        if (tocaEstadoDeAdmin) {
            throw new ForbiddenOperationException("Este lote no se puede modificar desde la disponibilidad pública");
        }

        String nombreAsesor = request.nombreAsesor() == null ? null : request.nombreAsesor().trim();
        if (request.estado() == EstadoLote.APARTADO && (nombreAsesor == null || nombreAsesor.isBlank())) {
            throw new ValidationException("El nombre del asesor es obligatorio para apartar un lote");
        }

        Usuario sistema = usuarioRepository
                .findByEmail(EMAIL_USUARIO_PUBLICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el usuario de sistema del cotizador público (migración V17)"));

        cambiarEstadoConHistorial(lote, request.estado(), sistema, nombreAsesor);
        return LoteDto.from(lote);
    }

    /** Historial de movimientos de un lote (quién cambió qué y cuándo), para /panel/lotes. */
    public List<MovimientoLoteDto> listarMovimientos(Long loteId) {
        obtenerEntidad(loteId);
        return movimientoLoteRepository.findByLoteIdOrderByFechaDesc(loteId).stream()
                .map(MovimientoLoteDto::from)
                .toList();
    }

    public LoteDto obtener(Long id) {
        return LoteDto.from(obtenerEntidad(id));
    }

    private Lote obtenerEntidad(Long id) {
        return loteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
    }

    public LoteDto crear(LoteCreateRequest request) {
        exigirAdmin("crear lotes");
        Desarrollo desarrollo = desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        validarUnico(request.desarrolloId(), request.manzana(), request.numeroLote(), null);

        Lote lote = new Lote(desarrollo, request.manzana().trim(), request.numeroLote().trim(), request.superficie());
        return LoteDto.from(loteRepository.save(lote));
    }

    public LoteDto actualizar(Long id, LoteUpdateRequest request) {
        exigirAdmin("editar lotes");
        Lote lote = obtenerEntidad(id);
        validarUnico(lote.getDesarrollo().getId(), request.manzana(), request.numeroLote(), id);

        lote.setManzana(request.manzana().trim());
        lote.setNumeroLote(request.numeroLote().trim());
        lote.setSuperficie(request.superficie());
        return LoteDto.from(lote);
    }

    public void eliminar(Long id) {
        exigirAdmin("eliminar lotes");
        loteRepository.delete(obtenerEntidad(id));
    }

    public LoteDto cambiarEstado(Long id, CambiarEstadoLoteRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Lote lote = obtenerEntidad(id);

        // No solo se restringe ENTRAR a un estado exclusivo de admin: una vez que un lote ya está
        // ahí (comprometido con dinero real o en firma), solo un admin puede moverlo a cualquier
        // otro estado. Si no, cualquier asesor podría "liberar" un lote que un admin apartó en
        // firme con solo marcarlo de vuelta a Disponible.
        boolean requiereAdmin =
                ESTADOS_SOLO_ADMIN.contains(request.estado()) || ESTADOS_SOLO_ADMIN.contains(lote.getEstado());
        if (requiereAdmin && actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede cambiar el estado de este lote");
        }

        cambiarEstadoConHistorial(lote, request.estado(), actual, null);
        return LoteDto.from(lote);
    }

    private void validarUnico(Long desarrolloId, String manzana, String numeroLote, Long idAExcluir) {
        boolean existe = loteRepository.existsByDesarrolloIdAndManzanaIgnoreCaseAndNumeroLoteIgnoreCase(
                desarrolloId, manzana.trim(), numeroLote.trim());
        if (existe) {
            // Si es edición y el duplicado encontrado es el mismo registro, no es un conflicto real.
            if (idAExcluir != null) {
                Lote actual = obtenerEntidad(idAExcluir);
                boolean esElMismo = actual.getDesarrollo().getId().equals(desarrolloId)
                        && actual.getManzana().equalsIgnoreCase(manzana.trim())
                        && actual.getNumeroLote().equalsIgnoreCase(numeroLote.trim());
                if (esElMismo) {
                    return;
                }
            }
            throw new ConflictException("Ya existe un lote con esa manzana y número en este desarrollo");
        }
    }

    private void exigirAdmin(String accion) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede " + accion);
        }
    }

    public LoteImportResultado importar(LoteImportBatchRequest request) {
        exigirAdmin("importar lotes");

        int creados = 0;
        List<LoteImportError> errores = new ArrayList<>();
        // Detecta duplicados DENTRO del mismo archivo sin depender de que Hibernate haga flush de
        // los inserts anteriores antes de la siguiente validación (evitaría además dejar la
        // transacción entera inutilizable si esa validación llegara a fallar a mitad del lote).
        Set<String> vistosEnEsteLote = new HashSet<>();
        int fila = 0;
        for (LoteImportRequest item : request.lotes()) {
            fila++;
            try {
                String clave = (item.desarrolloId() + ":" + item.manzana() + ":" + item.numeroLote()).toLowerCase();
                if (!vistosEnEsteLote.add(clave)) {
                    throw new ConflictException("Manzana y número de lote repetidos en el archivo");
                }

                Desarrollo desarrollo = desarrolloRepository
                        .findById(item.desarrolloId())
                        .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
                validarUnico(item.desarrolloId(), item.manzana(), item.numeroLote(), null);

                Lote lote = new Lote(desarrollo, item.manzana().trim(), item.numeroLote().trim(), item.superficie());
                loteRepository.save(lote);
                creados++;
            } catch (Exception e) {
                errores.add(new LoteImportError(fila, item.manzana(), item.numeroLote(), e.getMessage()));
            }
        }
        return new LoteImportResultado(creados, errores);
    }
}
