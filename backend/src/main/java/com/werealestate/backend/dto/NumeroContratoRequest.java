package com.werealestate.backend.dto;

import jakarta.validation.constraints.Size;

/** El número de contrato de una venta (texto libre, hasta 50). Vacío o null = quitarlo. */
public record NumeroContratoRequest(@Size(max = 50) String numeroContrato) {
}
