package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** fechaExpiraApartado es obligatoria (y debe ser futura) solo cuando estado ==
 * APARTADO_A_PLAZO; para cualquier otro estado se ignora (ver LoteService). nota es obligatoria
 * cuando quien cambia el estado es admin o líder de área (ver LoteService.cambiarEstado).
 * nombreCliente es opcional a nivel de validación de este DTO, pero la UI lo exige al mover un
 * lote a un estado comprometido (apartado en cualquiera de sus variantes, en firma o vendido) —
 * así queda un dato estructurado y buscable, no solo enterrado en la nota libre. */
public record CambiarEstadoLoteRequest(
        @NotNull EstadoLote estado,
        LocalDateTime fechaExpiraApartado,
        @Size(max = 500) String nota,
        @Size(max = 150) String nombreCliente) {
}
