package com.werealestate.backend.service;

import com.werealestate.backend.dto.ActualizarPoligonoMapaRequest;
import com.werealestate.backend.dto.ApartarVariosPublicoRequest;
import com.werealestate.backend.dto.ApartarVariosRequest;
import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteCreateRequest;
import com.werealestate.backend.dto.LimpiarMapaRequest;
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
import com.werealestate.backend.dto.ResumenLotesDto;
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
import com.werealestate.backend.model.VentaLote;
import jakarta.persistence.criteria.Subquery;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
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
            EstadoLote.RESERVADO_WE,
            EstadoLote.VENDIDO);

    /** Nota que queda en el historial del lote al pasar a "En proceso de firma" desde We Forms. */
    public static final String NOTA_REGISTRO_VENTA = "Registro de venta We Forms";
    /** Texto que tenía esa nota antes de cambiar el nombre: en el historial ya guardado sigue apareciendo así. */
    private static final Set<String> NOTAS_REGISTRO_VENTA = Set.of(NOTA_REGISTRO_VENTA, "Precontrato We Forms");

    /** Si la nota de un movimiento es la de un registro de venta de We Forms (la actual o la anterior). Acepta null:
     * la mayoría de los movimientos no llevan nota, y Set.of(...).contains(null) lanzaría NullPointerException. */
    public static boolean esNotaDeRegistroDeVenta(String nota) {
        return nota != null && NOTAS_REGISTRO_VENTA.contains(nota);
    }

    private static final Set<EstadoLote> ESTADOS_APARTADO =
            Set.of(EstadoLote.APARTADO, EstadoLote.APARTADO_A_PLAZO, EstadoLote.APARTADO_CON_DINERO);

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
    private final AsesorExternoRepository asesorExternoRepository;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public LoteService(
            LoteRepository loteRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            AsesorExternoRepository asesorExternoRepository,
            MovimientoLoteRepository movimientoLoteRepository,
            VentaLoteRepository ventaLoteRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder) {
        this.loteRepository = loteRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
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
            BigDecimal monto,
            String nota) {
        EstadoLote anterior = lote.getEstado();
        lote.cambiarEstado(nuevoEstado, usuario);
        movimientoLoteRepository.save(
                new MovimientoLote(lote, anterior, nuevoEstado, usuario, nombreAsesor, nombreCliente, monto, nota));
    }

    public PaginaDto<LoteDto> buscarPaginado(
            String manzana,
            String numeroLote,
            Long desarrolloId,
            String estado,
            BigDecimal superficieMin,
            BigDecimal superficieMax,
            Boolean reservadoWe,
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
            boolean veReservados = puedeVerReservadosWe();
            if (!veReservados) {
                // Para quien no ve los reservados por We son simplemente "Vendido": pedir uno u otro es lo mismo y
                // "Vendido" los incluye.
                if (estadoEnum == EstadoLote.RESERVADO_WE) estadoEnum = EstadoLote.VENDIDO;
                final EstadoLote pedido = estadoEnum;
                spec = spec.and((root, query, cb) -> pedido == EstadoLote.VENDIDO
                        ? root.get("estado").in(EstadoLote.VENDIDO, EstadoLote.RESERVADO_WE)
                        : cb.equal(root.get("estado"), pedido));
            } else if (estadoEnum == EstadoLote.RESERVADO_WE) {
                // "Reservado por We" para admin y administración: los marcados con ese estado y, como antes, los
                // vendidos que no tienen ninguna venta registrada.
                spec = spec.and((root, query, cb) -> {
                    Subquery<Long> conVenta = query.subquery(Long.class);
                    var ventaLote = conVenta.from(VentaLote.class);
                    conVenta.select(ventaLote.get("id")).where(cb.equal(ventaLote.get("lote"), root));
                    return cb.or(
                            cb.equal(root.get("estado"), EstadoLote.RESERVADO_WE),
                            cb.and(cb.equal(root.get("estado"), EstadoLote.VENDIDO), cb.not(cb.exists(conVenta))));
                });
            } else {
                final EstadoLote pedido = estadoEnum;
                spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), pedido));
            }
            // Dentro de "Vendido", admin y administración pueden pedir solo los reservados por We (true) o
            // solo los vendidos de verdad (false). Para cualquier otro rol el filtro se ignora.
            if (estadoEnum == EstadoLote.VENDIDO && reservadoWe != null && veReservados) {
                spec = spec.and((root, query, cb) -> {
                    Subquery<Long> conVenta = query.subquery(Long.class);
                    var ventaLote = conVenta.from(VentaLote.class);
                    conVenta.select(ventaLote.get("id")).where(cb.equal(ventaLote.get("lote"), root));
                    return reservadoWe ? cb.not(cb.exists(conVenta)) : cb.exists(conVenta);
                });
            }
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
        List<LoteDto> lotes = resultado.getContent().stream().map(LoteDto::from).toList();
        return new PaginaDto<>(marcarSinVentaRegistrada(lotes), resultado.hasNext());
    }

    /** Marca "Reservado por We" en /panel/lotes y /panel/plano: de los lotes VENDIDO, cuáles no tienen
     * ningún VentaLote asociado. Son lotes que la empresa se guarda para venderlos más adelante y que a
     * propósito se muestran como vendidos: solo admin y administración reciben la marca; para el resto
     * (y en todo lo público) siguen siendo simplemente "Vendido". */
    private List<LoteDto> marcarSinVentaRegistrada(List<LoteDto> lotes) {
        if (!puedeVerReservadosWe()) {
            return lotes;
        }
        List<Long> idsVendidos = lotes.stream()
                .filter(l -> l.estado() == EstadoLote.VENDIDO)
                .map(LoteDto::id)
                .toList();
        if (idsVendidos.isEmpty()) {
            return lotes;
        }
        Set<Long> conVenta = new HashSet<>(ventaLoteRepository.findLoteIdsConVenta(idsVendidos));
        return lotes.stream()
                .map(l -> l.estado() == EstadoLote.VENDIDO && !conVenta.contains(l.id())
                        ? l.conSinVentaRegistrada(true)
                        : l)
                .toList();
    }

    /** Inventario de cada desarrollo contado por estado, para las cifras del inicio del admin. Los
     * desarrollos sin lotes también aparecen (todo en 0) y cada uno trae los 6 estados, en el orden del
     * enum, para que el frontend no tenga que rellenar huecos. Se omite el catálogo genérico "Otro" (V12):
     * solo existe para leads de desarrollos ajenos y nunca tiene lotes. Solo lectura: lo puede pedir cualquiera con
     * acceso a los módulos de lotes (ver ModuloAccesoInterceptor). */
    private static final String DESARROLLO_GENERICO_OTRO = "Otro";

    /** Quién puede saber que un lote "vendido" en realidad lo reservó la empresa: admin y administración. */
    private boolean puedeVerReservadosWe() {
        Role rol = currentUserProvider.getUsuarioActual().getRol();
        return rol == Role.ADMIN || rol == Role.LIDER_AREA;
    }

    @Transactional(readOnly = true)
    public List<ResumenLotesDto> resumenPorDesarrollo() {
        Map<Long, Map<EstadoLote, Long>> conteos = new HashMap<>();
        for (LoteRepository.ConteoPorEstado c : loteRepository.contarPorDesarrolloYEstado()) {
            conteos.computeIfAbsent(c.getDesarrolloId(), id -> new EnumMap<>(EstadoLote.class))
                    .put(c.getEstado(), c.getTotal());
        }
        boolean veReservados = puedeVerReservadosWe();
        Map<Long, Long> reservados = new HashMap<>();
        if (veReservados) {
            for (LoteRepository.ConteoPorDesarrollo c : loteRepository.contarReservadosWePorDesarrollo()) {
                reservados.put(c.getDesarrolloId(), c.getTotal());
            }
        }
        return desarrolloRepository.findAll().stream()
                .filter(d -> !DESARROLLO_GENERICO_OTRO.equals(d.getNombre()))
                .sorted(Comparator.comparing(Desarrollo::getId))
                .map(d -> {
                    Map<EstadoLote, Long> porEstado = new EnumMap<>(EstadoLote.class);
                    Map<EstadoLote, Long> delDesarrollo = conteos.getOrDefault(d.getId(), Map.of());
                    for (EstadoLote estado : EstadoLote.values()) {
                        porEstado.put(estado, delDesarrollo.getOrDefault(estado, 0L));
                    }
                    long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
                    long conEstadoReservado = porEstado.get(EstadoLote.RESERVADO_WE);
                    long sinVenta = reservados.getOrDefault(d.getId(), 0L);
                    if (veReservados) {
                        // Admin y administración: "Reservado por We" va aparte (el estado propio y los vendidos sin
                        // venta registrada); porEstado[RESERVADO_WE] queda en 0 porque ya van en reservadosWe.
                        porEstado.put(EstadoLote.VENDIDO, porEstado.get(EstadoLote.VENDIDO) - sinVenta);
                        porEstado.put(EstadoLote.RESERVADO_WE, 0L);
                        return new ResumenLotesDto(d.getId(), d.getNombre(), total, porEstado, sinVenta + conEstadoReservado);
                    }
                    // El resto: los reservados cuentan como vendidos y el estado ni aparece en el resumen.
                    porEstado.put(EstadoLote.VENDIDO, porEstado.get(EstadoLote.VENDIDO) + conEstadoReservado);
                    porEstado.remove(EstadoLote.RESERVADO_WE);
                    return new ResumenLotesDto(d.getId(), d.getNombre(), total, porEstado, 0);
                })
                .toList();
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

        cambiarEstadoConHistorial(lote, request.estado(), sistema, nombreAsesor, nombreCliente, null, nota);
        return LoteDto.from(lote);
    }

    /** Tope de lotes en un apartado múltiple (el plano interno y el público). */
    public static final int MAX_LOTES_APARTADO_MULTIPLE = 5;

    /** Aparta varios lotes a la vez (estado APARTADO simple) con los mismos datos, desde el plano interno. Cada
     * lote pasa por {@link #cambiarEstado}, así que valen las mismas reglas de rol (nota obligatoria para admin y
     * administración, asesor al que pertenece el apartado) y queda su movimiento en el historial. Todo o nada: se
     * revisa primero que todos estén disponibles y, si algo falla después, la transacción deshace lo demás. */
    public List<LoteDto> apartarVarios(ApartarVariosRequest request) {
        String cliente = request.nombreCliente() == null ? "" : request.nombreCliente().trim();
        if (cliente.isEmpty()) {
            throw new ValidationException("El nombre del cliente es obligatorio para apartar");
        }
        List<Lote> lotes = lotesDisponiblesParaApartar(request.loteIds());
        List<LoteDto> apartados = new ArrayList<>();
        for (Lote lote : lotes) {
            apartados.add(cambiarEstado(
                    lote.getId(),
                    new CambiarEstadoLoteRequest(
                            EstadoLote.APARTADO, null, request.nota(), cliente, null,
                            request.usuarioAsesorId(), request.asesorExternoId())));
        }
        return apartados;
    }

    /** Lo mismo desde el plano público (sin sesión): cada lote pasa por {@link #cambiarEstadoPublico}. */
    public List<LoteDto> apartarVariosPublico(ApartarVariosPublicoRequest request) {
        List<Lote> lotes = lotesDisponiblesParaApartar(request.loteIds());
        List<LoteDto> apartados = new ArrayList<>();
        for (Lote lote : lotes) {
            apartados.add(cambiarEstadoPublico(
                    lote.getId(),
                    new CambiarEstadoLotePublicoRequest(
                            EstadoLote.APARTADO, request.nombreAsesor(), request.nombreCliente(), request.nota())));
        }
        return apartados;
    }

    /** Los lotes pedidos, en el mismo orden, si hay entre 1 y el tope, sin repetir y todos DISPONIBLES. */
    private List<Lote> lotesDisponiblesParaApartar(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("Elige al menos un lote");
        }
        if (ids.size() > MAX_LOTES_APARTADO_MULTIPLE) {
            throw new ValidationException("Máximo " + MAX_LOTES_APARTADO_MULTIPLE + " lotes por apartado");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ValidationException("Hay un lote repetido");
        }
        List<Lote> lotes = new ArrayList<>();
        for (Long id : ids) {
            Lote lote = obtenerEntidad(id);
            if (lote.getEstado() != EstadoLote.DISPONIBLE) {
                throw new ConflictException(
                        "El lote Mz " + lote.getManzana() + " / Lote " + lote.getNumeroLote() + " ya no está disponible");
            }
            lotes.add(lote);
        }
        return lotes;
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

    /** Historial completo de un lote específico (más reciente primero), para el ícono de "ver
     * información" en /panel/lotes: cuándo entró a su estado actual, quién lo cambió y el nombre
     * del cliente/nota que se haya capturado en cada paso. Mismo alcance de acceso que
     * {@link #buscarMovimientos}: cualquier rol autenticado puede consultarlo. */
    public List<MovimientoLoteDto> historialDeLote(Long id) {
        obtenerEntidad(id); // valida que el lote exista antes de listar su historial
        return movimientoLoteRepository.findByLoteIdOrderByFechaDesc(id).stream()
                .map(MovimientoLoteDto::from)
                .toList();
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
        lote.setPrecioM2(request.precioM2());
        return LoteDto.from(loteRepository.save(lote));
    }

    public LoteDto actualizar(Long id, LoteUpdateRequest request) {
        exigirAdmin("editar lotes");
        Lote lote = obtenerEntidad(id);
        validarUnico(lote.getDesarrollo().getId(), request.manzana(), request.numeroLote(), id);

        lote.setManzana(request.manzana().trim());
        lote.setNumeroLote(request.numeroLote().trim());
        lote.setSuperficie(request.superficie());
        lote.setPrecioM2(request.precioM2());
        return LoteDto.from(lote);
    }

    /** Pone (o quita, con null) el precio por m² propio de todos los lotes de una manzana, p. ej. las
     * hectáreas. Devuelve cuántos lotes se actualizaron. */
    public int actualizarPrecioManzana(Long desarrolloId, String manzana, java.math.BigDecimal precioM2) {
        exigirAdmin("cambiar precios");
        java.util.List<Lote> lotes = loteRepository.findAll().stream()
                .filter(l -> l.getDesarrollo().getId().equals(desarrolloId)
                        && l.getManzana().trim().equalsIgnoreCase(manzana.trim()))
                .toList();
        if (lotes.isEmpty()) throw new ResourceNotFoundException("No hay lotes en esa manzana");
        lotes.forEach(l -> l.setPrecioM2(precioM2));
        return lotes.size();
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
                    "Solo un administrador o personal de administración puede cambiar el estado de este lote");
        }

        if (request.estado() == EstadoLote.APARTADO_A_PLAZO) {
            if (request.fechaExpiraApartado() == null || !request.fechaExpiraApartado().isAfter(LocalDateTime.now())) {
                throw new ValidationException(
                        "Para apartar a plazo hay que indicar una fecha de vencimiento futura");
            }
        }
        if (request.estado() == EstadoLote.APARTADO_CON_DINERO) {
            if (request.montoApartado() == null || request.montoApartado().signum() <= 0) {
                throw new ValidationException("Indica con cuánto dinero se apartó este lote");
            }
        }

        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();
        // Un admin o líder de área puede mover un lote a cualquier estado (incluidos los
        // comprometidos con dinero real o en firma), así que se le pide justificar cada cambio; un
        // asesor solo se mueve entre Disponible/Apartado en su operación diaria y no necesita nota.
        if ((actual.getRol() == Role.ADMIN || actual.getRol() == Role.LIDER_AREA) && nota == null) {
            throw new ValidationException("Agrega una nota explicando el motivo del cambio de estado");
        }

        String nombreCliente = request.nombreCliente() == null || request.nombreCliente().isBlank()
                ? null
                : request.nombreCliente().trim();
        BigDecimal montoApartado =
                request.estado() == EstadoLote.APARTADO_CON_DINERO ? request.montoApartado() : null;
        // Un apartado que pone administración debe quedar a nombre del asesor al que pertenece: así
        // ese asesor lo ve (y solo él) al armar un registro de venta en We Forms.
        String nombreAsesor = null;
        if (ESTADOS_APARTADO.contains(request.estado())
                && (actual.getRol() == Role.ADMIN || actual.getRol() == Role.LIDER_AREA)) {
            nombreAsesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());
        }
        cambiarEstadoConHistorial(lote, request.estado(), actual, nombreAsesor, nombreCliente, montoApartado, nota);
        lote.setFechaExpiraApartado(
                request.estado() == EstadoLote.APARTADO_A_PLAZO ? request.fechaExpiraApartado() : null);
        lote.setMontoApartado(montoApartado);
        return LoteDto.from(lote);
    }

    /** Nombre del asesor (interno o externo) elegido al apartar un lote desde el panel; exactamente uno. */
    private String resolverAsesor(Long usuarioAsesorId, Long asesorExternoId) {
        if ((usuarioAsesorId == null) == (asesorExternoId == null)) {
            throw new ValidationException("Elige a qué asesor pertenece este apartado");
        }
        if (usuarioAsesorId != null) {
            return usuarioRepository
                    .findById(usuarioAsesorId)
                    .filter(Usuario::isActivo)
                    .map(Usuario::getNombre)
                    .orElseThrow(() -> new ValidationException("El asesor elegido no existe o está inactivo"));
        }
        return asesorExternoRepository
                .findById(asesorExternoId)
                .filter(com.werealestate.backend.model.AsesorExterno::isActivo)
                .map(com.werealestate.backend.model.AsesorExterno::getNombre)
                .orElseThrow(() -> new ValidationException("El asesor elegido no existe o está inactivo"));
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
        return new PlanoDesarrolloDto(
                desarrollo.getId(), desarrollo.getNombre(), desarrollo.getPlanoUrl(), marcarSinVentaRegistrada(lotes));
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

    /** Borra la delimitación de TODOS los lotes de un desarrollo. Es destructivo (hay que volver a
     * dibujar cada polígono), así que además de ser exclusivo de admin exige su contraseña. Devuelve
     * cuántos lotes tenían delimitación. */
    public int limpiarMapa(LimpiarMapaRequest request) {
        Usuario actual = exigirAdmin("quitar la delimitación de todos los lotes");
        if (!passwordEncoder.matches(request.password(), actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        int limpiados = 0;
        for (Lote lote : loteRepository.findByDesarrolloId(request.desarrolloId())) {
            if (lote.getMapaPoligonoJson() != null) {
                lote.actualizarPoligonoMapa(null);
                limpiados++;
            }
        }
        return limpiados;
    }

    /** Devuelve a DISPONIBLE un lote que quedó VENDIDO por una venta que se eliminó, con su historial
     * de movimientos; si ya no está VENDIDO (alguien lo cambió a mano) se deja como está. */
    public void liberarPorVentaEliminada(Long id) {
        Lote lote = obtenerEntidad(id);
        if (lote.getEstado() != EstadoLote.VENDIDO) return;
        Usuario actual = currentUserProvider.getUsuarioActual();
        cambiarEstadoConHistorial(lote, EstadoLote.DISPONIBLE, actual, null, null, null, "Venta eliminada");
    }

    /** El lote pasa a EN_PROCESO_DE_FIRMA a raíz de un registro de venta de We Forms (ver VentaService.
     * crearDesdeRegistroDeVenta), sin sesión: el cambio queda en el historial a nombre del usuario de sistema.
     * A diferencia de marcarVendido, el dinero con el que estuviera apartado NO se borra: queda pendiente
     * de aplicar en la venta (ver Venta.depositoPendiente) y lo limpia {@link #limpiarMontoApartado}. */
    public void marcarEnProcesoDeFirma(Long id, String cliente, String asesor, Usuario actor) {
        Lote lote = obtenerEntidad(id);
        cambiarEstadoConHistorial(
                lote, EstadoLote.EN_PROCESO_DE_FIRMA, actor, asesor, cliente, null, NOTA_REGISTRO_VENTA);
        lote.setFechaExpiraApartado(null);
    }

    /** El dinero apartado del lote ya se aplicó a su venta: deja de aparecer como depósito pendiente. */
    public void limpiarMontoApartado(Long id) {
        obtenerEntidad(id).setMontoApartado(null);
    }

    /** Lotes que un asesor puede poner en un registro de venta: los disponibles del desarrollo y los que ÉL mismo
     * apartó (quien aparece en el último cambio a su estado actual). Un apartado que alguien más hizo, o
     * uno sin asesor atribuido (por ejemplo, fijado por administración), no se ofrece. */
    @Transactional(readOnly = true)
    public List<LoteDto> listarParaRegistroDeVenta(Long desarrolloId, Long asesorUsuarioId, String asesorNombre) {
        return loteRepository.findByDesarrolloId(desarrolloId).stream()
                .filter(l -> puedeTomarParaRegistroDeVenta(l, asesorUsuarioId, asesorNombre))
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::from)
                .toList();
    }

    /** Regla de {@link #listarParaRegistroDeVenta}, y que no tenga ya una venta. */
    public boolean puedeTomarParaRegistroDeVenta(Lote lote, Long asesorUsuarioId, String asesorNombre) {
        if (ventaLoteRepository.existsByLoteId(lote.getId())) return false;
        EstadoLote estado = lote.getEstado();
        if (estado == EstadoLote.DISPONIBLE) return true;
        if (estado != EstadoLote.APARTADO && estado != EstadoLote.APARTADO_A_PLAZO
                && estado != EstadoLote.APARTADO_CON_DINERO) {
            return false;
        }
        return movimientoLoteRepository.findByLoteIdOrderByFechaDesc(lote.getId()).stream()
                .filter(m -> m.getEstadoNuevo() == estado)
                .findFirst()
                .map(m -> {
                    boolean mismoNombre = m.getNombreAsesor() != null
                            && m.getNombreAsesor().trim().equalsIgnoreCase(asesorNombre == null ? "" : asesorNombre.trim());
                    boolean mismoUsuario = asesorUsuarioId != null
                            && m.getUsuario() != null
                            && asesorUsuarioId.equals(m.getUsuario().getId())
                            && m.getNombreAsesor() == null;
                    return mismoNombre || mismoUsuario;
                })
                .orElse(false);
    }

    /** Marca el lote como VENDIDO a raíz de un registro de {@code Venta} (ver VentaService), con el
     * mismo mecanismo de historial que cualquier otro cambio de estado. No repite el chequeo de rol
     * de {@link #cambiarEstado}: VentaService ya exige admin/líder de área para registrar una venta. */
    public LoteDto marcarVendido(Long id, String cliente, String asesor) {
        Lote lote = obtenerEntidad(id);
        Usuario actual = currentUserProvider.getUsuarioActual();
        cambiarEstadoConHistorial(lote, EstadoLote.VENDIDO, actual, asesor, cliente, null, "Venta registrada");
        lote.setFechaExpiraApartado(null);
        // El formulario de Ventas ya leyó montoApartado (vía LoteDto) antes de llegar aquí, para
        // decidir si ese dinero baja la mensualidad o el saldo; una vez Vendido, ya no representa
        // un depósito pendiente de aplicar.
        lote.setMontoApartado(null);
        return LoteDto.from(lote);
    }

    private Usuario exigirAdmin(String accion) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede " + accion);
        }
        return actual;
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
                lote.setPrecioM2(item.precioM2());
                loteRepository.save(lote);
                creados++;
            } catch (Exception e) {
                errores.add(new LoteImportError(fila, item.manzana(), item.numeroLote(), e.getMessage()));
            }
        }
        return new LoteImportResultado(creados, errores);
    }
}
