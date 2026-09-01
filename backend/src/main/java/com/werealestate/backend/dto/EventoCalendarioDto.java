package com.werealestate.backend.dto;

import com.werealestate.backend.model.EventoCalendario;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EventoCalendarioDto(
        Long id, String titulo, String descripcion, LocalDate fecha, LocalDateTime fechaCreacion) {

    public static EventoCalendarioDto from(EventoCalendario evento) {
        return new EventoCalendarioDto(
                evento.getId(), evento.getTitulo(), evento.getDescripcion(), evento.getFecha(), evento.getFechaCreacion());
    }
}
