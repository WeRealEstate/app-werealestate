package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anula un recibo; el motivo es obligatorio y queda en la bitácora. */
public record ReciboAnularRequest(@NotBlank @Size(max = 500) String motivo) {
}
