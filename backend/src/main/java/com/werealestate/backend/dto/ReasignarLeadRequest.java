package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ReasignarLeadRequest(@NotNull Long nuevoAsesorId) {
}
