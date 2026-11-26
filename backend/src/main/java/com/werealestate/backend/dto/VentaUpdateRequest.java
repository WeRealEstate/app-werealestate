package com.werealestate.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Igual que VentaCreateRequest pero sin lotes: no toca qué lotes incluye la
 * venta ni sus precios, solo los datos capturados (cliente, fechas, términos de financiamiento).
 * Temporal: el botón que usa esto en el frontend se va a quitar más adelante. */
public record VentaUpdateRequest(
        // null = sin cambios; con valor, la venta pasa a ese cliente.
        Long clienteId,
        // null = sin cambios; con valor, la venta pasa a ese número (recorriendo las consecutivas si está ocupado).
        @Positive @Max(99999999) Long numero,
        // null = sin cambios; una lista (aunque vacía) reemplaza a los copropietarios. Máximo 4.
        @Size(max = 4) java.util.List<Long> copropietariosIds,
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
        @Size(max = 1000) String notas,
        // true = la fecha de venta ya es la definitiva (quita la marca de "fecha provisional"); cambiarla también la quita.
        Boolean confirmarFecha,
        // Efectivo o transferencia; null = sin definir (quita la que tuviera).
        com.werealestate.backend.model.ModalidadPago modalidadPago,
        // true = aplicar promocionId (null = quitar la promoción); null o false = la promoción no cambia.
        // Cambiarla mueve el precio de los lotes (el bono se resta de ellos), por eso pide la contraseña.
        Boolean cambiarPromocion,
        Long promocionId,
        String password,
        // Con cambiarPromocion a una de mensualidad fija: las aportaciones del plan original (reemplazan a las que
        // tuviera). null = las aportaciones no cambian.
        @jakarta.validation.Valid java.util.List<VentaAportacionItemRequest> aportaciones) {

    /** Sin tocar la promoción (como antes de poder cambiarla al modificar). */
    public VentaUpdateRequest(Long clienteId, Long numero, java.util.List<Long> copropietariosIds, Long usuarioAsesorId,
            Long asesorExternoId, String formaPago, LocalDate fechaVenta, BigDecimal mensualidad, Integer plazoMeses,
            String engancheLabel, BigDecimal enganche, Integer diaPago, Boolean primeraMensualidadMesVenta, String notas,
            Boolean confirmarFecha, com.werealestate.backend.model.ModalidadPago modalidadPago) {
        this(clienteId, numero, copropietariosIds, usuarioAsesorId, asesorExternoId, formaPago, fechaVenta, mensualidad,
                plazoMeses, engancheLabel, enganche, diaPago, primeraMensualidadMesVenta, notas, confirmarFecha,
                modalidadPago, null, null, null, null);
    }
}
