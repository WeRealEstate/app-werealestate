package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Reasignación masiva de leads a un solo asesor (máximo 500 por petición; los ids repetidos se ignoran). */
public record ReasignarVariosLeadsRequest(
        @NotEmpty @Size(max = 500) List<@NotNull Long> leadIds, @NotNull Long nuevoAsesorId) {
}
