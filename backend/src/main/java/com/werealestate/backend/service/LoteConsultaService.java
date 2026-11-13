package com.werealestate.backend.service;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PlanoDesarrolloDto;
import com.werealestate.backend.dto.ResumenLotesDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura de lotes: búsqueda paginada, resúmenes, listas para cotizar y planos públicos, y el plano interno. Lo que
 * cada rol ve de un lote (reservados por We, venta en la que está) se decide aquí y en model.VisibilidadLote. */
@Service
@Transactional
public class LoteConsultaService {

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

    static final Comparator<Lote> ORDEN_NATURAL_LOTES = Comparator.comparing(Lote::getManzana, ORDEN_NATURAL)
            .thenComparing(Lote::getNumeroLote, ORDEN_NATURAL);


    /** Nombre exacto de Desarrollo (ver catálogo sembrado) para cada valor de "proyecto" que usa
     * el público (planos públicos y cotizador) — el mismo mapeo que ya usa CotizadorComponent. */
    private static final Map<String, String> DESARROLLO_POR_PROYECTO =
            Map.of("samai", "SAMAI Campestre", "nanuu", "Aldea Nanuu");

    /** Catálogo genérico "Otro" (V12): solo existe para leads de desarrollos ajenos y nunca tiene lotes. */
    private static final String DESARROLLO_GENERICO_OTRO = "Otro";

    private final LoteRepository loteRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final AutorizacionLotes autorizacion;

    public LoteConsultaService(
            LoteRepository loteRepository,
            DesarrolloRepository desarrolloRepository,
            VentaLoteRepository ventaLoteRepository,
            AutorizacionLotes autorizacion) {
        this.loteRepository = loteRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.autorizacion = autorizacion;
    }

    public Lote obtenerEntidad(Long id) {
        return loteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
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
            String comodin = Textos.comodinMinusculas(manzana);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("manzana")), comodin));
        }
        if (numeroLote != null && !numeroLote.isBlank()) {
            String comodin = Textos.comodinMinusculas(numeroLote);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("numeroLote")), comodin));
        }
        if (desarrolloId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("desarrollo").get("id"), desarrolloId));
        }
        if (estado != null && !estado.isBlank()) {
            spec = spec.and(filtroPorEstado(EstadoLote.valueOf(estado), reservadoWe));
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
        List<LoteDto> lotes = resultado.getContent().stream().map(LoteDto::sinMapa).toList();
        return new PaginaDto<>(marcarVentas(lotes), resultado.hasNext());
    }

    /** Filtro por estado de {@link #buscarPaginado}. Quién ve los reservados por We decide qué significa pedir
     * "Vendido" o "Reservado por We" (ver model.VisibilidadLote). */
    private Specification<Lote> filtroPorEstado(EstadoLote pedido, Boolean reservadoWe) {
        boolean veReservados = autorizacion.puedeVerReservadosWe();
        if (!veReservados) {
            // Para quien no ve los reservados por We son simplemente "Vendido": pedir uno u otro es lo mismo y
            // "Vendido" los incluye.
            EstadoLote efectivo = pedido == EstadoLote.RESERVADO_WE ? EstadoLote.VENDIDO : pedido;
            return (root, query, cb) -> efectivo == EstadoLote.VENDIDO
                    ? root.get("estado").in(EstadoLote.VENDIDO, EstadoLote.RESERVADO_WE)
                    : cb.equal(root.get("estado"), efectivo);
        }
        if (pedido == EstadoLote.RESERVADO_WE) {
            // "Reservado por We" para admin y administración: los marcados con ese estado y, como antes, los
            // vendidos que no tienen ninguna venta registrada.
            return (root, query, cb) -> cb.or(
                    cb.equal(root.get("estado"), EstadoLote.RESERVADO_WE),
                    cb.and(cb.equal(root.get("estado"), EstadoLote.VENDIDO), cb.not(cb.exists(subconsultaConVenta(root, query, cb)))));
        }
        Specification<Lote> porEstado = (root, query, cb) -> cb.equal(root.get("estado"), pedido);
        // Dentro de "Vendido", admin y administración pueden pedir solo los reservados por We (true) o
        // solo los vendidos de verdad (false).
        if (pedido == EstadoLote.VENDIDO && reservadoWe != null) {
            return porEstado.and((root, query, cb) -> {
                var conVenta = cb.exists(subconsultaConVenta(root, query, cb));
                return reservadoWe ? cb.not(conVenta) : conVenta;
            });
        }
        return porEstado;
    }

    /** Subconsulta "este lote está en alguna venta". */
    private static Subquery<Long> subconsultaConVenta(
            jakarta.persistence.criteria.Root<Lote> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Subquery<Long> conVenta = query.subquery(Long.class);
        var ventaLote = conVenta.from(VentaLote.class);
        conVenta.select(ventaLote.get("id")).where(cb.equal(ventaLote.get("lote"), root));
        return conVenta;
    }

    /** Para /panel/lotes, /panel/plano y el formulario de Ventas, solo admin y administración (los que ven Ventas):
     * en qué venta está cada lote (ventaNumero: con venta su estado lo manda la venta, ver {@link #cambiarEstado}) y
     * cuáles VENDIDO no tienen ninguna ("Reservado por We": lotes que la empresa se guarda y que a propósito se
     * muestran como vendidos). Para el resto, y en todo lo público, los lotes van tal cual: "Vendido" y ya. */
    List<LoteDto> marcarVentas(List<LoteDto> lotes) {
        if (lotes.isEmpty() || !autorizacion.puedeVerReservadosWe()) {
            return lotes;
        }
        Map<Long, String> numeroPorLote = new HashMap<>();
        for (Object[] fila : ventaLoteRepository.numeroDeVentaPorLote(lotes.stream().map(LoteDto::id).toList())) {
            numeroPorLote.put((Long) fila[0], ((Venta) fila[1]).getEtiqueta());
        }
        return lotes.stream()
                .map(l -> {
                    String numero = numeroPorLote.get(l.id());
                    boolean sinVenta = l.estado() == EstadoLote.VENDIDO && numero == null;
                    return numero == null && !sinVenta ? l : l.conVenta(sinVenta, numero);
                })
                .toList();
    }

    /** {@link #marcarVentas} de un solo lote, para lo que se devuelve después de cambiarlo. */
    LoteDto conVentas(Lote lote) {
        return marcarVentas(List.of(LoteDto.from(lote))).get(0);
    }

    /** Inventario de cada desarrollo contado por estado, para las cifras del inicio del admin. Los
     * desarrollos sin lotes también aparecen (todo en 0) y cada uno trae los 6 estados, en el orden del
     * enum, para que el frontend no tenga que rellenar huecos. Se omite el catálogo genérico "Otro".
     * Solo lectura: lo puede pedir cualquiera con acceso a los módulos de lotes (ver ModuloAccesoInterceptor). */
    @Transactional(readOnly = true)
    public List<ResumenLotesDto> resumenPorDesarrollo() {
        Map<Long, Map<EstadoLote, Long>> conteos = new HashMap<>();
        for (LoteRepository.ConteoPorEstado c : loteRepository.contarPorDesarrolloYEstado()) {
            conteos.computeIfAbsent(c.getDesarrolloId(), id -> new EnumMap<>(EstadoLote.class))
                    .put(c.getEstado(), c.getTotal());
        }
        boolean veReservados = autorizacion.puedeVerReservadosWe();
        Map<Long, Long> reservados = new HashMap<>();
        if (veReservados) {
            for (LoteRepository.ConteoPorDesarrollo c : loteRepository.contarReservadosWePorDesarrollo()) {
                reservados.put(c.getDesarrolloId(), c.getTotal());
            }
        }
        return desarrolloRepository.findAll().stream()
                .filter(d -> !DESARROLLO_GENERICO_OTRO.equals(d.getNombre()))
                .sorted(Comparator.comparing(Desarrollo::getId))
                .map(d -> resumenDe(d, conteos.getOrDefault(d.getId(), Map.of()), veReservados, reservados.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    private ResumenLotesDto resumenDe(
            Desarrollo d, Map<EstadoLote, Long> delDesarrollo, boolean veReservados, long sinVenta) {
        Map<EstadoLote, Long> porEstado = new EnumMap<>(EstadoLote.class);
        for (EstadoLote estado : EstadoLote.values()) {
            porEstado.put(estado, delDesarrollo.getOrDefault(estado, 0L));
        }
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long conEstadoReservado = porEstado.get(EstadoLote.RESERVADO_WE);
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
    }

    /** Lotes disponibles de un desarrollo, para elegir uno al cotizar. Se reordenan en Java con
     * ORDEN_NATURAL_LOTES (no es una lista paginada, así que no hace falta resolverlo en SQL). */
    public List<LoteDto> listarDisponibles(Long desarrolloId) {
        return loteRepository
                .findByDesarrolloIdAndEstadoOrderByManzanaAscNumeroLoteAsc(desarrolloId, EstadoLote.DISPONIBLE)
                .stream()
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::sinMapa)
                .toList();
    }

    /** Todos los lotes de un desarrollo (cualquier estado) para los planos públicos y el cotizador: a
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

        return loteRepository.findConDesarrolloByDesarrolloId(desarrollo.getId()).stream()
                .sorted(ORDEN_NATURAL_LOTES)
                .map(LoteDto::paraPublico)
                .toList();
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
                desarrollo.getId(), desarrollo.getNombre(), desarrollo.getPlanoUrl(), marcarVentas(lotes));
    }
}
