package com.werealestate.backend.service;

import com.werealestate.backend.dto.ActualizarPoligonoMapaRequest;
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
import com.werealestate.backend.dto.PlanoDesarrolloDto;
import com.werealestate.backend.dto.PoligonoMapaJson;
import com.werealestate.backend.dto.PuntoMapaDto;
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
import java.time.LocalDateTime;
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

/** Los estados APARTADO_A_PLAZO, APARTADO_CON_DINERO, EN_PROCESO_DE_FIRMA y VENDIDO (comprometidos
 * con dinero real o en firma) solo los puede establecer un admin o un líder de área; DISPONIBLE y
 * APARTADO los puede mover cualquiera (es el uso diario de un asesor). Dar de alta/editar/importar
 * lotes es exclusivo de admin. */
@Service
@Transactional
public class LoteService {

    private static final Set<EstadoLote> ESTADOS_ADMIN_O_LIDER = Set.of(
            EstadoLote.APARTADO_A_PLAZO,
            EstadoLote.APARTADO_CON_DINERO,
            EstadoLote.EN_PROCESO_DE_FIRMA,
            EstadoLote.VENDIDO);

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
    private void cambiarEstadoConHistorial(
            Lote lote,
            EstadoLote nuevoEstado,
            Usuario usuario,
            String nombreAsesor,
            String nombreCliente,
            String nota) {
        EstadoLote anterior = lote.getEstado();
        lote.cambiarEstado(nuevoEstado, usuario);
        movimientoLoteRepository.save(
                new MovimientoLote(lote, anterior, nuevoEstado, usuario, nombreAsesor, nombreCliente, nota));
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
     * salir de esos estados), el cambio se atribuye al usuario de sistema, apartar exige el
     * nombre del asesor que atendió al visitante y el del cliente que apartó, y liberar un lote ya
     * apartado no está permitido desde aquí (solo desde /panel/lotes o /panel/plano) — evita que
     * cualquiera con el link pueda dejar disponible un lote que un asesor ya comprometió. */
    public LoteDto cambiarEstadoPublico(Long id, CambiarEstadoLotePublicoRequest request) {
        Lote lote = obtenerEntidad(id);

        boolean tocaEstadoRestringido =
                ESTADOS_ADMIN_O_LIDER.contains(request.estado()) || ESTADOS_ADMIN_O_LIDER.contains(lote.getEstado());
        if (tocaEstadoRestringido) {
            throw new ForbiddenOperationException("Este lote no se puede modificar desde la disponibilidad pública");
        }
        if (request.estado() == EstadoLote.DISPONIBLE && lote.getEstado() == EstadoLote.APARTADO) {
            throw new ForbiddenOperationException("Un lote apartado no se puede liberar desde la disponibilidad pública");
        }

        String nombreAsesor = request.nombreAsesor() == null ? null : request.nombreAsesor().trim();
        String nombreCliente = request.nombreCliente() == null ? null : request.nombreCliente().trim();
        if (request.estado() == EstadoLote.APARTADO) {
            if (nombreAsesor == null || nombreAsesor.isBlank()) {
                throw new ValidationException("El nombre del asesor es obligatorio para apartar un lote");
            }
            if (nombreCliente == null || nombreCliente.isBlank()) {
                throw new ValidationException("El nombre del cliente es obligatorio para apartar un lote");
            }
        }
        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();

        Usuario sistema = usuarioRepository
                .findByEmail(EMAIL_USUARIO_PUBLICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el usuario de sistema del cotizador público (migración V17)"));

        cambiarEstadoConHistorial(lote, request.estado(), sistema, nombreAsesor, nombreCliente, nota);
        return LoteDto.from(lote);
    }

    /** Historial de movimientos de todos los lotes (quién cambió qué y cuándo), para /panel/lotes.
     * Filtrable por manzana/lote/desarrollo igual que {@link #buscarPaginado}. */
    public PaginaDto<MovimientoLoteDto> buscarMovimientos(
            String manzana, String numeroLote, Long desarrolloId, int pagina, int tamano) {
        Specification<MovimientoLote> spec = (root, query, cb) -> cb.conjunction();

        if (manzana != null && !manzana.isBlank()) {
            String comodin = "%" + manzana.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("lote").get("manzana")), comodin));
        }
        if (numeroLote != null && !numeroLote.isBlank()) {
            String comodin = "%" + numeroLote.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("lote").get("numeroLote")), comodin));
        }
        if (desarrolloId != null) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(root.get("lote").get("desarrollo").get("id"), desarrolloId));
        }
        spec = spec.and((root, query, cb) -> {
            query.orderBy(cb.desc(root.get("fecha")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<MovimientoLote> resultado = movimientoLoteRepository.findAll(spec, pageable);
        return new PaginaDto<>(resultado.getContent().stream().map(MovimientoLoteDto::from).toList(), resultado.hasNext());
    }

    /** Borrar un registro del historial de movimientos es exclusivo de admin: es la bitácora de
     * auditoría de todo lo que pasa con los lotes. */
    public void eliminarMovimiento(Long id) {
        exigirAdmin("eliminar movimientos del historial");
        MovimientoLote movimiento = movimientoLoteRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado"));
        movimientoLoteRepository.delete(movimiento);
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

        // No solo se restringe ENTRAR a un estado comprometido con dinero real o en firma: una vez
        // que un lote ya está ahí, solo un admin o un líder de área puede moverlo a cualquier otro
        // estado. Si no, cualquier asesor podría "liberar" un lote que ya fue apartado en firme con
        // solo marcarlo de vuelta a Disponible.
        boolean requiereAdminOLider =
                ESTADOS_ADMIN_O_LIDER.contains(request.estado()) || ESTADOS_ADMIN_O_LIDER.contains(lote.getEstado());
        if (requiereAdminOLider && actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException(
                    "Solo un administrador o un líder de área puede cambiar el estado de este lote");
        }

        if (request.estado() == EstadoLote.APARTADO_A_PLAZO) {
            if (request.fechaExpiraApartado() == null || !request.fechaExpiraApartado().isAfter(LocalDateTime.now())) {
                throw new ValidationException(
                        "Para apartar a plazo hay que indicar una fecha de vencimiento futura");
            }
        }

        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();
        // Un admin o líder de área puede mover un lote a cualquier estado (incluidos los
        // comprometidos con dinero real o en firma), así que se le pide justificar cada cambio; un
        // asesor solo se mueve entre Disponible/Apartado en su operación diaria y no necesita nota.
        if ((actual.getRol() == Role.ADMIN || actual.getRol() == Role.LIDER_AREA) && nota == null) {
            throw new ValidationException("Agrega una nota explicando el motivo del cambio de estado");
        }

        cambiarEstadoConHistorial(lote, request.estado(), actual, null, null, nota);
        lote.setFechaExpiraApartado(
                request.estado() == EstadoLote.APARTADO_A_PLAZO ? request.fechaExpiraApartado() : null);
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

    /** Plano interactivo de un desarrollo (imagen + pin de cada lote) para /panel/plano,
     * tanto para verlo (cualquier rol) como para editarlo (solo admin, ver
     * {@link #actualizarPosicionMapa}). */
    public PlanoDesarrolloDto obtenerMapa(Long desarrolloId) {
        Desarrollo desarrollo = desarrolloRepository
                .findById(desarrolloId)
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        List<LoteDto> lotes = loteRepository.findByDesarrolloId(desarrolloId).stream()
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::from)
                .toList();
        return new PlanoDesarrolloDto(desarrollo.getId(), desarrollo.getNombre(), desarrollo.getPlanoUrl(), lotes);
    }

    /** Delimita (o borra, con una lista vacía o nula) el polígono de un lote sobre el plano de su
     * desarrollo; exclusivo de admin, igual que dar de alta/editar/importar lotes. */
    public LoteDto actualizarPoligonoMapa(Long id, ActualizarPoligonoMapaRequest request) {
        exigirAdmin("delimitar lotes en el plano");
        List<PuntoMapaDto> puntos = request.puntos();
        if (puntos != null && !puntos.isEmpty() && puntos.size() < 3) {
            throw new ValidationException("Un polígono necesita al menos 3 puntos");
        }
        Lote lote = obtenerEntidad(id);
        lote.actualizarPoligonoMapa(PoligonoMapaJson.serializar(puntos));
        return LoteDto.from(lote);
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
