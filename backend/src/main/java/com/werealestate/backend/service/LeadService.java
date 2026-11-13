package com.werealestate.backend.service;

import com.werealestate.backend.dto.AsignarEtiquetasRequest;
import com.werealestate.backend.dto.LeadCreateRequest;
import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.LeadResumenDto;
import com.werealestate.backend.dto.LeadImportBatchRequest;
import com.werealestate.backend.dto.LeadImportError;
import com.werealestate.backend.dto.LeadImportRequest;
import com.werealestate.backend.dto.LeadImportResultado;
import com.werealestate.backend.dto.LeadUpdateRequest;
import com.werealestate.backend.dto.MoverColumnaRequest;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.ReasignarLeadRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.ColumnaPersonalizada;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Etiqueta;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Pais;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.TipoSeguimiento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ColumnaPersonalizadaRepository;
import com.werealestate.backend.repository.ComisionRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.EtiquetaRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional
public class LeadService {

    private static final List<EstadoLead> ESTADOS_CERRADOS =
            List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);

    /** Base del candado de numeración por asesor (se le suma el id del asesor). */
    private static final long CLAVE_NUMERACION = 7_600_000_000L;

    private final LeadRepository leadRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final ColumnaPersonalizadaRepository columnaRepository;
    private final ComisionRepository comisionRepository;
    private final EtiquetaRepository etiquetaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transaccion;
    private final int diasFrio;
    private final int diasSinContactarNuevo;

    public LeadService(
            LeadRepository leadRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            SeguimientoRepository seguimientoRepository,
            ColumnaPersonalizadaRepository columnaRepository,
            ComisionRepository comisionRepository,
            EtiquetaRepository etiquetaRepository,
            CurrentUserProvider currentUserProvider,
            TransactionTemplate transaccion,
            @Value("${app.lead.dias-frio}") int diasFrio,
            @Value("${app.lead.dias-sin-contactar-nuevo}") int diasSinContactarNuevo) {
        this.leadRepository = leadRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.columnaRepository = columnaRepository;
        this.comisionRepository = comisionRepository;
        this.etiquetaRepository = etiquetaRepository;
        this.currentUserProvider = currentUserProvider;
        this.transaccion = transaccion;
        this.diasFrio = diasFrio;
        this.diasSinContactarNuevo = diasSinContactarNuevo;
    }

    public List<LeadDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Lead> leads = actual.getRol() == Role.ADMIN
                ? leadRepository.findByArchivadoFalseOrderByFechaUltimoContactoDesc()
                : leadRepository.findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoDesc(actual.getId());

        return leads.stream().map(this::toDto).toList();
    }

    /**
     * Versión paginada y con filtros de {@link #listar()} / {@link #listarArchivados()}, para la
     * lista principal de leads: carga por lotes ("Cargar más") en vez de traer todo de un jalón,
     * y la búsqueda/filtros corren en el servidor sobre el total, no solo sobre lo ya cargado.
     */
    public PaginaDto<LeadDto> buscarPaginado(
            String busqueda, String estado, Long asesorIdFiltro, Long etiquetaId, boolean archivados, int pagina, int tamano) {
        Usuario actual = currentUserProvider.getUsuarioActual();

        Specification<Lead> spec = (root, query, cb) -> cb.equal(root.get("archivado"), archivados);

        if (actual.getRol() == Role.ADMIN) {
            if (asesorIdFiltro != null) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("asesor").get("id"), asesorIdFiltro));
            }
        } else {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("asesor").get("id"), actual.getId()));
        }

        if (busqueda != null && !busqueda.isBlank()) {
            spec = spec.and(filtroPorBusqueda(busqueda.trim()));
        }

        if ("FRIOS".equalsIgnoreCase(estado)) {
            LocalDateTime limite = LocalDateTime.now().minusDays(diasFrio);
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.lessThan(root.get("fechaUltimoContacto"), limite),
                    cb.not(root.get("estado").in(ESTADOS_CERRADOS))));
        } else if (estado != null && !estado.isBlank()) {
            EstadoLead estadoEnum = EstadoLead.valueOf(estado);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estadoEnum));
        }

        if (etiquetaId != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("etiquetas").get("id"), etiquetaId);
            });
        }

        Pageable pageable = PageRequest.of(
                Math.max(pagina, 0), Math.max(tamano, 1), Sort.by(Sort.Direction.DESC, "fechaUltimoContacto"));
        Page<Lead> resultado = leadRepository.findAll(spec, pageable);

        List<LeadDto> contenido = resultado.getContent().stream().map(this::toDto).toList();
        return new PaginaDto<>(contenido, resultado.hasNext());
    }

    /** Texto en nombre o teléfono; un número solo ("12" o "#12") también busca el lead con ese número. */
    private static Specification<Lead> filtroPorBusqueda(String busquedaRecortada) {
        String comodin = "%" + busquedaRecortada.toLowerCase() + "%";
        String sinAlmohadilla = busquedaRecortada.startsWith("#") ? busquedaRecortada.substring(1) : busquedaRecortada;
        Long numeroBuscado = sinAlmohadilla.matches("\\d{1,15}") ? Long.valueOf(sinAlmohadilla) : null;
        return (root, query, cb) -> {
            var coincide = cb.or(
                    cb.like(cb.lower(root.get("nombreCliente")), comodin),
                    cb.like(cb.lower(root.get("telefono")), comodin));
            return numeroBuscado == null ? coincide : cb.or(coincide, cb.equal(root.get("numero"), numeroBuscado));
        };
    }

    /**
     * Leads archivados: se conservan como métrica pero no aparecen en la lista activa. Solo
     * accesibles explícitamente (icono dedicado en la lista), con el mismo alcance por rol que
     * {@link #listar()}.
     */
    public List<LeadDto> listarArchivados() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Lead> leads = actual.getRol() == Role.ADMIN
                ? leadRepository.findByArchivadoTrueOrderByFechaUltimoContactoDesc()
                : leadRepository.findByAsesorIdAndArchivadoTrueOrderByFechaUltimoContactoDesc(actual.getId());

        return leads.stream().map(this::toDto).toList();
    }

    public List<LeadDto> listarFrios() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede ver los leads fríos de todo el equipo");
        }
        LocalDateTime limite = LocalDateTime.now().minusDays(diasFrio);
        return leadRepository
                .findByFechaUltimoContactoBeforeAndEstadoNotInAndArchivadoFalseOrderByFechaUltimoContactoAsc(
                        limite, ESTADOS_CERRADOS)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public LeadDto obtener(Long id) {
        Lead lead = buscarLeadPermitido(id);
        return toDto(lead);
    }

    public LeadDto crear(LeadCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Desarrollo desarrollo = desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        Usuario asesor = resolverAsesorDestino(
                actual, request.asesorId(), "Solo un administrador puede asignar el lead a otro asesor");

        Lead lead = new Lead(
                request.nombreCliente(),
                request.telefono(),
                request.email(),
                request.origen(),
                desarrollo,
                asesor,
                request.valorEstimado());
        lead.setEdad(request.edad());
        lead.setPais(request.pais());
        lead.setEstadoRepublica(request.pais() == Pais.EXTRANJERO ? null : request.estadoRepublica());
        lead.setDesarrolloDetalle(normalizarDetalleDesarrollo(desarrollo, request.desarrolloDetalle()));

        return toDto(guardarConNumero(lead));
    }

    /** El asesor dueño de lo que se crea: quien lo pide, o (solo si es admin) el que indicó. */
    private Usuario resolverAsesorDestino(Usuario actual, Long asesorIdPedido, String mensajeSiNoEsAdmin) {
        if (asesorIdPedido == null || asesorIdPedido.equals(actual.getId())) {
            return actual;
        }
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException(mensajeSiNoEsAdmin);
        }
        return usuarioRepository
                .findById(asesorIdPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /** Le da al lead el siguiente número de su asesor y lo guarda, todo en la misma transacción y con un candado por
     * asesor: así dos altas a la vez (o una importación y un alta) no repiten número. */
    private Lead guardarConNumero(Lead lead) {
        return transaccion.execute(estado -> {
            asignarNumero(lead);
            return leadRepository.save(lead);
        });
    }

    private void asignarNumero(Lead lead) {
        lead.setNumero(siguienteNumeroDe(lead.getAsesor().getId()));
    }

    private long siguienteNumeroDe(Long asesorId) {
        leadRepository.bloquearNumeracion(CLAVE_NUMERACION + asesorId);
        return leadRepository.siguienteNumero(asesorId);
    }

    /**
     * Importa un lote de leads desde un archivo (Excel parseado en el frontend). Cada fila se
     * guarda en su propia transacción (ver {@code Propagation.NOT_SUPPORTED} en la anotación del
     * método) para que un error puntual no eche para atrás las filas que sí eran válidas.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LeadImportResultado importar(LeadImportBatchRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Usuario asesor = resolverAsesorDestino(
                actual, request.asesorId(), "Solo un administrador puede importar leads para otro asesor");

        int creados = 0;
        List<LeadImportError> errores = new ArrayList<>();
        int fila = 0;
        for (LeadImportRequest item : request.leads()) {
            fila++;
            try {
                Desarrollo desarrollo = desarrolloRepository
                        .findById(item.desarrolloId())
                        .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

                Lead lead = new Lead(
                        item.nombreCliente(),
                        item.telefono(),
                        item.email(),
                        item.origen(),
                        desarrollo,
                        asesor,
                        null);
                lead = guardarConNumero(lead);

                if (item.notas() != null && !item.notas().isBlank()) {
                    Seguimiento seguimiento = new Seguimiento(
                            lead, asesor, TipoSeguimiento.OTRO, item.notas(), null, null, null);
                    seguimientoRepository.save(seguimiento);
                }
                creados++;
            } catch (Exception e) {
                String motivo = e instanceof ResourceNotFoundException || e instanceof ForbiddenOperationException
                        ? e.getMessage()
                        : "No se pudo crear este lead.";
                errores.add(new LeadImportError(fila, item.nombreCliente(), motivo));
            }
        }
        return new LeadImportResultado(creados, errores);
    }

    public LeadDto actualizar(Long id, LeadUpdateRequest request) {
        Lead lead = buscarLeadPermitido(id);
        Desarrollo desarrollo = desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        lead.setNombreCliente(request.nombreCliente());
        lead.setTelefono(request.telefono());
        lead.setEmail(request.email());
        lead.setOrigen(request.origen());
        lead.setDesarrollo(desarrollo);
        lead.setDesarrolloDetalle(normalizarDetalleDesarrollo(desarrollo, request.desarrolloDetalle()));
        lead.setEstado(request.estado());
        lead.setValorEstimado(request.valorEstimado());
        lead.setEdad(request.edad());
        lead.setPais(request.pais());
        lead.setEstadoRepublica(request.pais() == Pais.EXTRANJERO ? null : request.estadoRepublica());
        Lead guardado = leadRepository.save(lead);

        return toDto(guardado);
    }

    /**
     * El detalle escrito a mano solo tiene sentido cuando el desarrollo elegido es el catálogo
     * genérico "Otro"; en cualquier otro caso se descarta para no dejar texto obsoleto si el lead
     * cambia de desarrollo más adelante.
     */
    private String normalizarDetalleDesarrollo(Desarrollo desarrollo, String detalle) {
        if (!"Otro".equals(desarrollo.getNombre()) || detalle == null || detalle.isBlank()) {
            return null;
        }
        return detalle.trim();
    }

    /**
     * Mueve el lead entre tarjetas (columnas 100% personalizadas del asesor) del tablero, o lo
     * regresa a "Sin asignar" cuando {@code columnaPersonalizadaId} viene nulo. Nunca cambia el
     * estado real del lead; el movimiento igual se confirma con un seguimiento real en la bitácora.
     */
    public LeadDto moverAColumnaPersonalizada(Long id, MoverColumnaRequest request) {
        Lead lead = buscarLeadPermitido(id);
        ColumnaPersonalizada actual = lead.getColumnaPersonalizada();
        Long actualId = actual != null ? actual.getId() : null;
        if (Objects.equals(actualId, request.columnaPersonalizadaId())) {
            return toDto(lead);
        }

        ColumnaPersonalizada columna = null;
        if (request.columnaPersonalizadaId() != null) {
            columna = columnaRepository
                    .findById(request.columnaPersonalizadaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tarjeta no encontrada"));
            if (!columna.getAsesor().getId().equals(lead.getAsesor().getId())) {
                throw new ForbiddenOperationException("Esa tarjeta no pertenece al asesor de este lead");
            }
        }

        Usuario usuarioActual = currentUserProvider.getUsuarioActual();
        lead.setColumnaPersonalizada(columna);
        lead.setFechaUltimoContacto(LocalDateTime.now());
        Lead guardado = leadRepository.save(lead);

        seguimientoRepository.save(new Seguimiento(
                guardado,
                usuarioActual,
                request.tipo(),
                request.nota(),
                request.resultado(),
                proximoSeguimientoOPorDefecto(request.proximoSeguimiento()),
                request.duracionMinutos()));

        return toDto(guardado);
    }

    /** Regla 3 de automatización de seguimiento: si al mover la tarjeta no se agenda el próximo
     * contacto, se agenda uno por defecto en vez de dejar al lead sin ninguna fecha futura. */
    private LocalDateTime proximoSeguimientoOPorDefecto(LocalDateTime proximoSeguimiento) {
        return proximoSeguimiento != null ? proximoSeguimiento : LocalDateTime.now().plusDays(diasSinContactarNuevo);
    }

    public LeadDto reasignar(Long id, ReasignarLeadRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede reasignar leads");
        }

        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado"));
        Usuario nuevoAsesor = usuarioRepository
                .findById(request.nuevoAsesorId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!nuevoAsesor.getId().equals(lead.getAsesor().getId())) {
            // Las etiquetas son privadas de cada asesor: al cambiar de dueño, las del anterior ya no aplican.
            lead.getEtiquetas().clear();
            // Recibe el siguiente número del asesor que lo recibe; el anterior queda como hueco, no se reutiliza.
            // Se calcula antes de cambiar el asesor: la consulta vacía los cambios pendientes y el lead (con el asesor
            // nuevo y su número viejo) podría chocar con otro del mismo número.
            Long numero = siguienteNumeroDe(nuevoAsesor.getId());
            lead.setAsesor(nuevoAsesor);
            lead.setNumero(numero);
        }
        return toDto(leadRepository.save(lead));
    }

    /**
     * Reemplaza el conjunto completo de etiquetas del lead. Solo se pueden usar etiquetas del
     * catálogo del asesor dueño del lead (son privadas, no se comparten entre asesores).
     */
    public LeadDto asignarEtiquetas(Long id, AsignarEtiquetasRequest request) {
        Lead lead = buscarLeadPermitido(id);

        Set<Etiqueta> nuevas = new LinkedHashSet<>();
        for (Long etiquetaId : request.etiquetaIds()) {
            Etiqueta etiqueta = etiquetaRepository
                    .findById(etiquetaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada"));
            if (!etiqueta.getAsesor().getId().equals(lead.getAsesor().getId())) {
                throw new ForbiddenOperationException("Esa etiqueta no pertenece al asesor de este lead");
            }
            nuevas.add(etiqueta);
        }

        lead.getEtiquetas().clear();
        lead.getEtiquetas().addAll(nuevas);
        return toDto(leadRepository.save(lead));
    }

    /** Archiva el lead: deja de aparecer en la lista activa, pero se conserva íntegro como métrica. */
    public LeadDto archivar(Long id) {
        Lead lead = buscarLeadPermitido(id);
        lead.setArchivado(true);
        return toDto(leadRepository.save(lead));
    }

    public LeadDto desarchivar(Long id) {
        Lead lead = buscarLeadPermitido(id);
        lead.setArchivado(false);
        return toDto(leadRepository.save(lead));
    }

    /**
     * Borra el lead definitivamente (a diferencia de archivar, no se puede deshacer). Solo un
     * administrador puede hacerlo. Se rechaza si el lead ya generó una comisión, para no perder
     * ese registro financiero; su bitácora de seguimientos sí se borra junto con él, porque no
     * tiene sentido conservarla por separado.
     */
    public void eliminar(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede borrar leads");
        }

        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado"));

        if (comisionRepository.existsByLeadId(id)) {
            throw new ConflictException("No se puede borrar a " + lead.getNombreCliente()
                    + ": ya generó una comisión y ese registro financiero no se puede perder.");
        }

        seguimientoRepository.deleteByLeadId(id);
        leadRepository.delete(lead);
    }

    /** Carga un lead y valida que el usuario actual pueda verlo/editarlo (dueño o admin). */
    Lead buscarLeadPermitido(Long id) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado"));
        Usuario actual = currentUserProvider.getUsuarioActual();

        boolean esDueno = lead.getAsesor().getId().equals(actual.getId());
        if (!esDueno && actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("No tienes acceso a este lead");
        }
        return lead;
    }

    private LeadDto toDto(Lead lead) {
        long dias = ChronoUnit.DAYS.between(lead.getFechaUltimoContacto(), LocalDateTime.now());
        return LeadDto.from(lead, dias, esFrio(lead, dias));
    }

    /** Frío: abierto (ni ganado ni perdido) y sin contacto en diasFrio días o más. */
    private boolean esFrio(Lead lead, long diasSinContacto) {
        boolean cerrado = lead.getEstado() == EstadoLead.CERRADO_GANADO || lead.getEstado() == EstadoLead.CERRADO_PERDIDO;
        return !cerrado && diasSinContacto >= diasFrio;
    }

    /** Los mismos leads que {@link #listar()}, solo contados (total, fríos y por estado), para el inicio del asesor:
     * resuelto en SQL con GROUP BY y COUNT para no instanciar todas las entidades Lead ni sus colecciones. */
    @Transactional(readOnly = true)
    public LeadResumenDto resumen() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        boolean esAdmin = actual.getRol() == Role.ADMIN;
        List<Object[]> conteos = esAdmin
                ? leadRepository.contarPorEstadoActivos()
                : leadRepository.contarPorEstadoActivosDeAsesor(actual.getId());
        Map<EstadoLead, Long> porEstado = new EnumMap<>(EstadoLead.class);
        long total = 0;
        for (Object[] fila : conteos) {
            EstadoLead estado = (EstadoLead) fila[0];
            long cantidad = ((Number) fila[1]).longValue();
            porEstado.put(estado, cantidad);
            total += cantidad;
        }
        LocalDateTime limiteFrio = LocalDateTime.now().minusDays(diasFrio);
        long frios = esAdmin
                ? leadRepository.contarFriosActivos(limiteFrio)
                : leadRepository.contarFriosActivosDeAsesor(actual.getId(), limiteFrio);
        return new LeadResumenDto(total, frios, porEstado);
    }
}
