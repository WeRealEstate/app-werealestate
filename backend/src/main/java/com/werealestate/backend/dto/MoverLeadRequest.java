package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLead;
import jakarta.validation.constraints.NotNull;

public record MoverLeadRequest(@NotNull EstadoLead estado) {
}
