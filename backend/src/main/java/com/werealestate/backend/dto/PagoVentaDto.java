package com.werealestate.backend.dto;

import com.werealestate.backend.model.PagoVenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PagoVentaDto(
        Long id,
        LocalDate fecha,
        BigDecimal monto,
        String notas,
        UsuarioResumenDto registradoPor,
        LocalDateTime fechaCreacion) {

    public static PagoVentaDto from(PagoVenta pago) {
        return new PagoVentaDto(
                pago.getId(),
                pago.getFecha(),
                pago.getMonto(),
                pago.getNotas(),
                UsuarioResumenDto.from(pago.getRegistradoPor()),
                pago.getFechaCreacion());
    }
}
