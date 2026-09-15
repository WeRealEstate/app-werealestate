package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** nombreAsesor es obligatorio solo para apartar (ver LoteService.cambiarEstadoPublico) — para
 * liberar no hace falta pedirlo de nuevo. nota es siempre opcional. */
public record CambiarEstadoLotePublicoRequest(
        @NotNull EstadoLote estado, String nombreAsesor, @Size(max = 500) String nota) {
}
