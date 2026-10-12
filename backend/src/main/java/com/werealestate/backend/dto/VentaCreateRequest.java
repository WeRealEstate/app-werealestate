package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        // El cliente ya registrado (ver Cliente / ClienteService): se elige al crear la venta.
        @NotNull Long clienteId,
        // Opcional: número de venta escrito a mano; null = el siguiente (mayor + 1). Si ya está ocupado se
        // recorren +1 ese número y los consecutivos que le siguen (ver VentaService.reservarNumero).
        @Positive @Max(99999999) Long numero,
        // Copropietarios (opcional, máximo 4 además del principal: 5 clientes en total).
        @Size(max = 4) List<Long> copropietariosIds,
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
        // Día del mes (1-31) en que paga la mensualidad; null = el día de la fecha de venta. Y si el
        // mes de la venta cuenta como primera mensualidad (true) o esta cae el mes siguiente
        // (false/null). Ver Venta.diaPago.
        @Min(1) @Max(31) Integer diaPago,
        Boolean primeraMensualidadMesVenta,
        @Size(max = 1000) String notas,
        // Si es true, además de registrar la venta se marca cada lote como VENDIDO (con su
        // historial normal, ver LoteService.marcarVendido). Ver VentaService.
        boolean marcarLoteVendido,
        // Esquema "Con aportaciones": varias por año, cada una con su mes y monto. Opcional (null o
        // vacío = sin aportaciones); ver VentaService.validarAportaciones.
        @Valid List<VentaAportacionItemRequest> aportaciones,
        // Contrato firmado (opcional; null = sin firmar). Con firmado = true, la fecha es obligatoria.
        Boolean contratoFirmado,
        LocalDate contratoFechaFirma,
        // Cómo se firmó (obligatorio con firmado = true) y enlace opcional a la carpeta de Drive.
        com.werealestate.backend.model.MedioFirma contratoMedio,
        @Size(max = 500) String contratoDriveUrl,
        // Comisión del asesor principal, en % del valor de la venta (null = 5%, máximo 10%).
        BigDecimal comisionPorcentaje,
        // Copropietario de la venta (un segundo asesor, interno o externo; ninguno = sin copropietario),
        // con su propia comisión en % (null = 5%, máximo 10%).
        Long coasesorUsuarioId,
        Long coasesorExternoId,
        BigDecimal coasesorComisionPorcentaje) {
}
