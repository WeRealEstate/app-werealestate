package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Registra o corrige si el contrato de la venta ya está firmado y desde cuándo. */
public record ContratoRequest(@NotNull Boolean firmado, LocalDate fechaFirma) {
}
