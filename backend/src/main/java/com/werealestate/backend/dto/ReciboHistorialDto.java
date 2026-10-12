package com.werealestate.backend.dto;

import com.werealestate.backend.model.AccionRecibo;
import com.werealestate.backend.model.ReciboVentaHistorial;
import java.time.LocalDateTime;

/** Una línea de la bitácora de un recibo (quién, qué, cuándo y el detalle de lo que cambió). */
public record ReciboHistorialDto(
        Long reciboId, String folio, AccionRecibo accion, String usuario, LocalDateTime fechaHora, String detalle) {

    public static ReciboHistorialDto from(ReciboVentaHistorial h) {
        return new ReciboHistorialDto(
                h.getRecibo().getId(),
                h.getRecibo().getFolio(),
                h.getAccion(),
                h.getUsuario().getNombre(),
                h.getFechaHora(),
                h.getDetalle());
    }
}
