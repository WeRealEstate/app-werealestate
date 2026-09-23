package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoLoteDto(
        Long id,
        Long loteId,
        String manzana,
        String numeroLote,
        String desarrolloNombre,
        EstadoLote estadoAnterior,
        EstadoLote estadoNuevo,
        UsuarioResumenDto usuario,
        String nombreAsesor,
        String nombreCliente,
        BigDecimal monto,
        String nota,
        LocalDateTime fecha) {

    public static MovimientoLoteDto from(MovimientoLote movimiento) {
        Lote lote = movimiento.getLote();
        return new MovimientoLoteDto(
                movimiento.getId(),
                lote.getId(),
                lote.getManzana(),
                lote.getNumeroLote(),
                lote.getDesarrollo().getNombre(),
                movimiento.getEstadoAnterior(),
                movimiento.getEstadoNuevo(),
                movimiento.getUsuario() != null ? UsuarioResumenDto.from(movimiento.getUsuario()) : null,
                movimiento.getNombreAsesor(),
                movimiento.getNombreCliente(),
                movimiento.getMonto(),
                movimiento.getNota(),
                movimiento.getFecha());
    }
}
