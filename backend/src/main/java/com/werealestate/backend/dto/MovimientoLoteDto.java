package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.MovimientoLote;
import java.time.LocalDateTime;

public record MovimientoLoteDto(
        Long id,
        EstadoLote estadoAnterior,
        EstadoLote estadoNuevo,
        UsuarioResumenDto usuario,
        String nombreAsesor,
        LocalDateTime fecha) {

    public static MovimientoLoteDto from(MovimientoLote movimiento) {
        return new MovimientoLoteDto(
                movimiento.getId(),
                movimiento.getEstadoAnterior(),
                movimiento.getEstadoNuevo(),
                movimiento.getUsuario() != null ? UsuarioResumenDto.from(movimiento.getUsuario()) : null,
                movimiento.getNombreAsesor(),
                movimiento.getFecha());
    }
}
