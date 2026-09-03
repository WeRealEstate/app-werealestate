package com.werealestate.backend.service;

import com.werealestate.backend.dto.LeadCreateRequest;
import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.LeadUpdateRequest;
import com.werealestate.backend.dto.MoverColumnaRequest;
import com.werealestate.backend.dto.ReasignarLeadRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.ColumnaPersonalizada;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Pais;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ColumnaPersonalizadaRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final ColumnaPersonalizadaRepository columnaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ComisionService comisionService;
    private final int diasFrio;

    public LeadService(
            LeadRepository leadRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            SeguimientoRepository seguimientoRepository,
            ColumnaPersonalizadaRepository columnaRepository,
            CurrentUserProvider currentUserProvider,
            ComisionService comisionService,
            @Value("${app.lead.dias-frio}") int diasFrio) {
        this.leadRepository = leadRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.columnaRepository = columnaRepository;
        this.currentUserProvider = currentUserProvider;
        this.comisionService = comisionService;
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

        return toDto(leadRepository.save(lead));
    }

    public LeadDto actualizar(Long id, LeadUpdateRequest request) {
        Lead lead = buscarLeadPermitido(id);
        boolean eraGanado = lead.getEstado() == EstadoLead.CERRADO_GANADO;

        lead.setNombreCliente(request.nombreCliente());
        lead.setTelefono(request.telefono());
        lead.setEmail(request.email());
        lead.setOrigen(request.origen());
        lead.setEstado(request.estado());
        lead.setValorEstimado(request.valorEstimado());
        lead.setEdad(request.edad());
        lead.setPais(request.pais());
        lead.setEstadoRepublica(request.pais() == Pais.EXTRANJERO ? null : request.estadoRepublica());
        Lead guardado = leadRepository.save(lead);

        comisionService.generarSiCorresponde(guardado, eraGanado, request.estado() == EstadoLead.CERRADO_GANADO);

        return toDto(guardado);
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
                guardado, usuarioActual, request.tipo(), request.nota(), request.resultado(), request.proximoSeguimiento()));

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

        lead.setAsesor(nuevoAsesor);
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
