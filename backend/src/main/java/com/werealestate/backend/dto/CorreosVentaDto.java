package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoCorreoAutomatico;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registro de correos de una venta para el detalle.
 *
 * @param ventaNueva true si a esta venta le salen solas la bienvenida y la liquidación (registrada desde V94)
 * @param recordatorioExcluido true si se pidió no recordarle sus pagos automáticamente
 * @param pendientes los que están en cola, con su hora de salida
 * @param envios lo enviado (a mano o solo) y lo que no salió, de lo más reciente a lo más antiguo
 */
public record CorreosVentaDto(
        boolean ventaNueva,
        boolean recordatorioExcluido,
        List<Pendiente> pendientes,
        List<Envio> envios) {

    public record Pendiente(TipoCorreoAutomatico tipo, LocalDateTime enviarDesde) {
    }

    public record Envio(
            LocalDateTime fecha,
            TipoCorreoAutomatico tipo,
            boolean enviado,
            boolean automatico,
            String usuario,
            String detalle) {
    }
}
