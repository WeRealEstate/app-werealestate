package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;

public record TareaReasignarRequest(@NotNull Long nuevoAsignadoAId) {
}
