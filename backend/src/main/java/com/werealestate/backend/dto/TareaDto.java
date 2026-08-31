package com.werealestate.backend.dto;

import com.werealestate.backend.model.Tarea;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TareaDto(
        Long id,
        String titulo,
        String descripcion,
        UsuarioResumenDto asignadoA,
        UsuarioResumenDto creadoPor,
        LocalDate fechaLimite,
        boolean completada,
        LocalDateTime fechaCreacion) {

    public static TareaDto from(Tarea tarea) {
        return new TareaDto(
                tarea.getId(),
                tarea.getTitulo(),
                tarea.getDescripcion(),
                UsuarioResumenDto.from(tarea.getAsignadoA()),
                UsuarioResumenDto.from(tarea.getCreadoPor()),
                tarea.getFechaLimite(),
                tarea.isCompletada(),
                tarea.getFechaCreacion());
    }
}
