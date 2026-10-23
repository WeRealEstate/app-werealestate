package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.VisibilidadLote;
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
        // Un movimiento que entra o sale de "Reservado por We" se ve como un cambio a/de Vendido, sin la nota, el
        // asesor, el cliente ni el monto: así nadie que no sea admin o administración nota que se reservó.
        boolean disfrazado = VisibilidadLote.estaDisfrazado(movimiento.getEstadoAnterior())
                || VisibilidadLote.estaDisfrazado(movimiento.getEstadoNuevo());
        return new MovimientoLoteDto(
                movimiento.getId(),
                lote.getId(),
                lote.getManzana(),
                lote.getNumeroLote(),
                lote.getDesarrollo().getNombre(),
                VisibilidadLote.visible(movimiento.getEstadoAnterior()),
                VisibilidadLote.visible(movimiento.getEstadoNuevo()),
                !disfrazado && movimiento.getUsuario() != null ? UsuarioResumenDto.from(movimiento.getUsuario()) : null,
                disfrazado ? null : movimiento.getNombreAsesor(),
                disfrazado ? null : movimiento.getNombreCliente(),
                disfrazado ? null : movimiento.getMonto(),
                disfrazado ? null : movimiento.getNota(),
                movimiento.getFecha());
    }
}
