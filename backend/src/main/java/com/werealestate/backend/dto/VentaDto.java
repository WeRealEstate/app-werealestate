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
        String cliente,
        VentaAsesorDto asesor,
        String formaPago,
        LocalDate fechaVenta,
        BigDecimal mensualidad,
        Integer plazoMeses,
        String engancheLabel,
        BigDecimal enganche,
        String notas,
        LocalDateTime fechaCreacion,
        // Se calculan a partir de sus lotes y sus pagos (ver VentaService), nunca se guardan directo.
        BigDecimal precioVenta,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente) {

    public static VentaDto from(Venta venta, Long numero, List<VentaLoteDto> lotes, BigDecimal totalAbonado) {
        BigDecimal precioVenta = lotes.stream().map(VentaLoteDto::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VentaDto(
                venta.getId(),
                numero,
                lotes,
                venta.getCliente(),
                VentaAsesorDto.from(venta),
                venta.getFormaPago(),
                venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getEngancheLabel(),
                venta.getEnganche(),
                venta.getNotas(),
                venta.getFechaCreacion(),
                precioVenta,
                totalAbonado,
                precioVenta.subtract(totalAbonado));
    }
}
