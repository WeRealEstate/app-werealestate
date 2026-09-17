package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** fechaExpiraApartado es obligatoria (y debe ser futura) solo cuando estado ==
 * APARTADO_A_PLAZO; para cualquier otro estado se ignora (ver LoteService). */
public record CambiarEstadoLoteRequest(@NotNull EstadoLote estado, LocalDateTime fechaExpiraApartado) {
}
