package com.werealestate.backend.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Apartar varios lotes a la vez desde el plano público (sin sesión): mismas reglas que apartar uno (ver
 * LoteService.cambiarEstadoPublico), aplicadas a todos. nombreAsesor y nombreCliente son obligatorios; nota,
 * opcional. Todo o nada. */
public record ApartarVariosPublicoRequest(
        List<Long> loteIds, String nombreAsesor, String nombreCliente, @Size(max = 500) String nota) {
}
