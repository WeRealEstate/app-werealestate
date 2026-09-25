package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VentaCreateRequest(
        // Uno o más lotes: un cliente puede comprar varios en la misma operación, con una sola
        // mensualidad/plazo/saldo combinado (ver VentaLote y VentaService).
        @NotEmpty @Valid List<VentaLoteItemRequest> lotes,
        @NotBlank String cliente,
        // Exactamente uno de los dos: el asesor interno (usuario real del sistema) o externo (ver
        // VentaService.resolverAsesor, que valida esto — no se puede expresar con anotaciones).
        Long usuarioAsesorId,
        Long asesorExternoId,
        @NotBlank String formaPago,
        @NotNull LocalDate fechaVenta,
        // Términos de financiamiento; opcionales (una venta de contado no los necesita).
        @Positive BigDecimal mensualidad,
        @Positive Integer plazoMeses,
        // "Enganche" / "Pago inicial" / "Aportación anual" (mismo concepto que ya usa Cotización);
        // ambos null cuando no aplica (Sin enganche / Contado).
        String engancheLabel,
        @Positive BigDecimal enganche,
        @Size(max = 1000) String notas,
        // Si es true, además de registrar la venta se marca cada lote como VENDIDO (con su
        // historial normal, ver LoteService.marcarVendido). Ver VentaService.
        boolean marcarLoteVendido) {
}
