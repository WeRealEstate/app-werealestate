package com.werealestate.backend.service;

import com.werealestate.backend.dto.TareaCreateRequest;
import com.werealestate.backend.dto.TareaDto;
import com.werealestate.backend.dto.TareaEstadoRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TareaService {

    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;

    public TareaService(
            TareaRepository tareaRepository, UsuarioRepository usuarioRepository, CurrentUserProvider currentUserProvider) {
        this.tareaRepository = tareaRepository;
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Tareas asignadas al usuario actual, pendientes primero y ordenadas por fecha límite. */
    public List<TareaDto> misTareas() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        return tareaRepository
                .findByAsignadoAIdOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc(actual.getId())
                .stream()
                .map(TareaDto::from)
                .toList();
    }

    /** Tareas que el usuario actual creó/asignó a otros, para darles seguimiento. */
    public List<TareaDto> tareasCreadas() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        return tareaRepository.findByCreadoPorIdOrderByFechaCreacionDesc(actual.getId()).stream()
                .map(TareaDto::from)
                .toList();
    }

    public TareaDto crear(TareaCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.LIDER_AREA && actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un líder de área o administrador puede asignar tareas");
        }

        Usuario asignado = usuarioRepository
                .findById(request.asignadoAId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Tarea tarea = new Tarea(request.titulo(), request.descripcion(), asignado, actual, request.fechaLimite());
        return TareaDto.from(tareaRepository.save(tarea));
    }

    public TareaDto cambiarEstado(Long id, TareaEstadoRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Tarea tarea = tareaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada"));

        boolean esAsignado = tarea.getAsignadoA().getId().equals(actual.getId());
        boolean esCreador = tarea.getCreadoPor().getId().equals(actual.getId());
        if (!esAsignado && !esCreador) {
            throw new ForbiddenOperationException("No tienes acceso a esta tarea");
        }

        tarea.setCompletada(request.completada());
        return TareaDto.from(tareaRepository.save(tarea));
    }
}
