package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.VisibilidadLote;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param viaPublico true si lo hizo alguien sin sesión desde una página pública (plano o disponibilidad):
 *     ahí {@code nombreAsesor} es el nombre que escribió el asesor y {@code usuario} el de sistema. En
 *     un cambio hecho en el panel, {@code nombreAsesor} es el asesor al que se le apartó el lote.
 */
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
        boolean viaPublico,
        String nombreCliente,
        BigDecimal monto,
        String nota,
        LocalDateTime fecha) {

    /** Usuario de sistema al que se atribuyen los cambios sin sesión (V17, ver LoteService.cambiarEstadoPublico). */
    private static final String EMAIL_USUARIO_PUBLICO = "cotizador-publico@weinversiones.com";

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
                !disfrazado && movimiento.getUsuario() != null
                        && EMAIL_USUARIO_PUBLICO.equalsIgnoreCase(movimiento.getUsuario().getEmail()),
                disfrazado ? null : movimiento.getNombreCliente(),
                disfrazado ? null : movimiento.getMonto(),
                disfrazado ? null : movimiento.getNota(),
                movimiento.getFecha());
    }
}
