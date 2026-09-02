package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ComisionPagadaRequest(@NotNull Boolean pagada) {
}
