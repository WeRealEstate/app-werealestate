package com.werealestate.backend.service;

import com.werealestate.backend.dto.AsignarEtiquetasRequest;
import com.werealestate.backend.dto.LeadCreateRequest;
import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.LeadImportBatchRequest;
import com.werealestate.backend.dto.LeadImportError;
import com.werealestate.backend.dto.LeadImportRequest;
import com.werealestate.backend.dto.LeadImportResultado;
import com.werealestate.backend.dto.LeadUpdateRequest;
import com.werealestate.backend.dto.MoverColumnaRequest;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final ColumnaPersonalizadaRepository columnaRepository;
    private final ComisionRepository comisionRepository;
    private final EtiquetaRepository etiquetaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int diasFrio;

    public LeadService(
            LeadRepository leadRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            SeguimientoRepository seguimientoRepository,
            ColumnaPersonalizadaRepository columnaRepository,
            ComisionRepository comisionRepository,
            EtiquetaRepository etiquetaRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-frio}") int diasFrio) {
        this.leadRepository = leadRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.columnaRepository = columnaRepository;
        this.comisionRepository = comisionRepository;
        this.etiquetaRepository = etiquetaRepository;
        this.currentUserProvider = currentUserProvider;
        this.diasFrio = diasFrio;
    }

    public List<LeadDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Lead> leads = actual.getRol() == Role.ADMIN
                ? leadRepository.findByArchivadoFalseOrderByFechaUltimoContactoDesc()
                : leadRepository.findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoDesc(actual.getId());

        return leads.stream().map(this::toDto).toList();
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
        List<EstadoLead> cerrados = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);
        return leadRepository
                .findByFechaUltimoContactoBeforeAndEstadoNotInAndArchivadoFalseOrderByFechaUltimoContactoAsc(
                        limite, cerrados)
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

        Usuario asesor = actual;
        if (request.asesorId() != null && !request.asesorId().equals(actual.getId())) {
            if (actual.getRol() != Role.ADMIN) {
                throw new ForbiddenOperationException("Solo un administrador puede asignar el lead a otro asesor");
            }
            asesor = usuarioRepository
                    .findById(request.asesorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        }

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

        return toDto(leadRepository.save(lead));
    }

    /**
     * Importa un lote de leads desde un archivo (Excel parseado en el frontend). Cada fila se
     * guarda en su propia transacción (ver {@code Propagation.NOT_SUPPORTED} en la anotación del
     * método) para que un error puntual no eche para atrás las filas que sí eran válidas.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LeadImportResultado importar(LeadImportBatchRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Usuario asesor = actual;
        if (request.asesorId() != null && !request.asesorId().equals(actual.getId())) {
            if (actual.getRol() != Role.ADMIN) {
                throw new ForbiddenOperationException("Solo un administrador puede importar leads para otro asesor");
            }
            asesor = usuarioRepository
                    .findById(request.asesorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        }

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
                lead = leadRepository.save(lead);

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
                request.proximoSeguimiento(),
                request.duracionMinutos()));

        return toDto(guardado);
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
        }
        lead.setAsesor(nuevoAsesor);
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
        boolean cerrado = lead.getEstado() == EstadoLead.CERRADO_GANADO || lead.getEstado() == EstadoLead.CERRADO_PERDIDO;
        boolean frio = !cerrado && dias >= diasFrio;
        return LeadDto.from(lead, dias, frio);
    }
}
