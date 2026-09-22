package com.werealestate.backend.dto;

import com.werealestate.backend.model.Venta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VentaDto(
        Long id,
        LoteDto lote,
        String cliente,
        String asesor,
        BigDecimal precioVenta,
        String formaPago,
        LocalDate fechaVenta,
        BigDecimal mensualidad,
        Integer plazoMeses,
        String notas,
        LocalDateTime fechaCreacion,
        // Se calculan a partir de sus pagos (ver VentaService), nunca se guardan directo.
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente) {

    public static VentaDto from(Venta venta, BigDecimal totalAbonado) {
        return new VentaDto(
                venta.getId(),
                LoteDto.from(venta.getLote()),
                venta.getCliente(),
                venta.getAsesor(),
                venta.getPrecioVenta(),
                venta.getFormaPago(),
                venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getNotas(),
                venta.getFechaCreacion(),
                totalAbonado,
                venta.getPrecioVenta().subtract(totalAbonado));
    }
}
