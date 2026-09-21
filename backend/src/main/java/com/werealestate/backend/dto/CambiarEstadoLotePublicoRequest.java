package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** nombreAsesor y nombreCliente son obligatorios solo para apartar (ver
 * LoteService.cambiarEstadoPublico) — liberar un lote ya no está permitido desde aquí. nota es
 * siempre opcional. */
public record CambiarEstadoLotePublicoRequest(
        @NotNull EstadoLote estado, String nombreAsesor, String nombreCliente, @Size(max = 500) String nota) {
}
