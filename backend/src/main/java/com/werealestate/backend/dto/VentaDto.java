package com.werealestate.backend.dto;

import com.werealestate.backend.model.Venta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VentaDto(
        Long id,
        // Posición de la venta ordenada por fecha de venta (la más antigua es la 1), sin importar en qué
        // orden se registraron; se calcula al consultar (ver VentaRepository.numerosDe), no se guarda.
        Long numero,
        List<VentaLoteDto> lotes,
        // Aportaciones programadas de la venta (ver VentaAportacion); vacía si no las tiene.
        List<VentaAportacionDto> aportaciones,
        String cliente,
        // El cliente ligado (null solo en una venta anterior que aún no se migra).
        Long clienteId,
        VentaAsesorDto asesor,
        String formaPago,
        LocalDate fechaVenta,
        BigDecimal mensualidad,
        Integer plazoMeses,
        String engancheLabel,
        BigDecimal enganche,
        int diaPago,
        boolean primeraMensualidadMesVenta,
        String notas,
        LocalDateTime fechaCreacion,
        // Se calculan a partir de sus lotes y sus pagos (ver VentaService), nunca se guardan directo.
        BigDecimal precioVenta,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente) {

    public static VentaDto from(
            Venta venta,
            Long numero,
            List<VentaLoteDto> lotes,
            List<VentaAportacionDto> aportaciones,
            BigDecimal totalAbonado) {
        BigDecimal precioVenta = lotes.stream().map(VentaLoteDto::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VentaDto(
                venta.getId(),
                numero,
                lotes,
                aportaciones,
                venta.getCliente(),
                venta.getClienteRef() != null ? venta.getClienteRef().getId() : null,
                VentaAsesorDto.from(venta),
                venta.getFormaPago(),
                venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getEngancheLabel(),
                venta.getEnganche(),
                venta.getDiaPago(),
                venta.isPrimeraMensualidadMesVenta(),
                venta.getNotas(),
                venta.getFechaCreacion(),
                precioVenta,
                totalAbonado,
                precioVenta.subtract(totalAbonado));
    }
}
