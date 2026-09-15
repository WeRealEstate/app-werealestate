package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;

/** nombreAsesor es obligatorio solo para apartar (ver LoteService.cambiarEstadoPublico) — para
 * liberar no hace falta pedirlo de nuevo. */
public record CambiarEstadoLotePublicoRequest(@NotNull EstadoLote estado, String nombreAsesor) {
}
