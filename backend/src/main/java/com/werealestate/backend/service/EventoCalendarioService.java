package com.werealestate.backend.service;

import com.werealestate.backend.dto.EventoCalendarioDto;
import com.werealestate.backend.dto.EventoCalendarioRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.EventoCalendario;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agenda personal: cada quien ve y administra solo sus propios eventos, sin importar su rol. */
@Service
@Transactional
public class EventoCalendarioService {

    private final EventoCalendarioRepository eventoCalendarioRepository;
    private final CurrentUserProvider currentUserProvider;

    public EventoCalendarioService(
            EventoCalendarioRepository eventoCalendarioRepository, CurrentUserProvider currentUserProvider) {
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<EventoCalendarioDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        return eventoCalendarioRepository.findByUsuarioIdOrderByFechaAsc(actual.getId()).stream()
                .map(EventoCalendarioDto::from)
                .toList();
    }

    public EventoCalendarioDto crear(EventoCalendarioRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        EventoCalendario evento = new EventoCalendario(request.titulo(), request.descripcion(), request.fecha(), actual);
        return EventoCalendarioDto.from(eventoCalendarioRepository.save(evento));
    }

    public EventoCalendarioDto actualizar(Long id, EventoCalendarioRequest request) {
        EventoCalendario evento = buscarPropio(id);
        evento.setTitulo(request.titulo());
        evento.setDescripcion(request.descripcion());
        evento.setFecha(request.fecha());
        return EventoCalendarioDto.from(eventoCalendarioRepository.save(evento));
    }

    public void eliminar(Long id) {
        EventoCalendario evento = buscarPropio(id);
        eventoCalendarioRepository.delete(evento);
    }

    private EventoCalendario buscarPropio(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        EventoCalendario evento = eventoCalendarioRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        if (!evento.getUsuario().getId().equals(actual.getId())) {
            throw new ForbiddenOperationException("No tienes acceso a este evento");
        }
        return evento;
    }
}
