package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoLoteRequest(@NotNull EstadoLote estado) {
}
