package com.werealestate.backend.dto;

import com.werealestate.backend.model.MedioFirma;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Registra o corrige si el contrato de la venta ya está firmado, desde cuándo y cómo (físico o
 * DocuSign, obligatorio al firmar), más el enlace opcional a su carpeta de Drive. */
public record ContratoRequest(
        @NotNull Boolean firmado, LocalDate fechaFirma, MedioFirma medio, @Size(max = 500) String driveUrl) {
}
