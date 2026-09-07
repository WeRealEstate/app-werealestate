package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Reemplaza el conjunto completo de etiquetas de un lead por esta lista de ids. */
public record AsignarEtiquetasRequest(@NotNull List<Long> etiquetaIds) {
}
