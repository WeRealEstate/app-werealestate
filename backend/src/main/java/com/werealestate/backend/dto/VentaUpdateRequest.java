package com.werealestate.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Igual que VentaCreateRequest pero sin lotes ni marcarLoteVendido: no toca qué lotes incluye la
 * venta ni sus precios, solo los datos capturados (cliente, fechas, términos de financiamiento).
 * Temporal: el botón que usa esto en el frontend se va a quitar más adelante. */
public record VentaUpdateRequest(
        // null = sin cambios; con valor, la venta pasa a ese cliente.
        Long clienteId,
        // Exactamente uno de los dos (ver VentaCreateRequest / VentaService.resolverAsesor).
        Long usuarioAsesorId,
        Long asesorExternoId,
        @NotBlank String formaPago,
        @NotNull LocalDate fechaVenta,
        @Positive BigDecimal mensualidad,
        @Positive Integer plazoMeses,
        String engancheLabel,
        @Positive BigDecimal enganche,
        // null = sin cambios (clientes viejos que no los mandan).
        @Min(1) @Max(31) Integer diaPago,
        Boolean primeraMensualidadMesVenta,
        @Size(max = 1000) String notas) {
}
