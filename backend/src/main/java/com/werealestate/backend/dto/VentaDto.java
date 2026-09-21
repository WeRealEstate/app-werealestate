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
        String notas,
        LocalDateTime fechaCreacion) {

    public static VentaDto from(Venta venta) {
        return new VentaDto(
                venta.getId(),
                LoteDto.from(venta.getLote()),
                venta.getCliente(),
                venta.getAsesor(),
                venta.getPrecioVenta(),
                venta.getFormaPago(),
                venta.getFechaVenta(),
                venta.getNotas(),
                venta.getFechaCreacion());
    }
}
