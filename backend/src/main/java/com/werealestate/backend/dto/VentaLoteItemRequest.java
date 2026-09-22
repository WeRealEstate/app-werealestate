package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record VentaLoteItemRequest(@NotNull Long loteId, @NotNull @Positive BigDecimal precio) {
}
