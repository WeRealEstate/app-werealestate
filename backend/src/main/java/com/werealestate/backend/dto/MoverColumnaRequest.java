package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;

public record MoverColumnaRequest(@NotNull Long columnaPersonalizadaId) {
}
