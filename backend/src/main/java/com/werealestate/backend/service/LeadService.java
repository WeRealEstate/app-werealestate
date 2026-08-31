package com.werealestate.backend.service;

import com.werealestate.backend.dto.LeadCreateRequest;
import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.LeadUpdateRequest;
import com.werealestate.backend.dto.ReasignarLeadRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int diasFrio;

    public LeadService(
            LeadRepository leadRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-frio}") int diasFrio) {
        this.leadRepository = leadRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
        this.diasFrio = diasFrio;
    }

    public List<LeadDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Lead> leads = actual.getRol() == Role.ADMIN
                ? leadRepository.findAllByOrderByFechaUltimoContactoAsc()
                : leadRepository.findByAsesorIdOrderByFechaUltimoContactoAsc(actual.getId());

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
                .findByFechaUltimoContactoBeforeAndEstadoNotInOrderByFechaUltimoContactoAsc(limite, cerrados)
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

        return toDto(leadRepository.save(lead));
    }

    public LeadDto actualizar(Long id, LeadUpdateRequest request) {
        Lead lead = buscarLeadPermitido(id);
        lead.setNombreCliente(request.nombreCliente());
        lead.setTelefono(request.telefono());
        lead.setEmail(request.email());
        lead.setOrigen(request.origen());
        lead.setEstado(request.estado());
        lead.setValorEstimado(request.valorEstimado());
        return toDto(leadRepository.save(lead));
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
