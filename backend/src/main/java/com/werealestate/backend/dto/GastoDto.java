package com.werealestate.backend.dto;

import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.OrigenGasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GastoDto(
        Long id,
        String concepto,
        OrigenGasto origen,
        LocalDate fecha,
        BigDecimal monto,
        boolean tieneTicket,
        UsuarioResumenDto registradoPor,
        LocalDateTime fechaCreacion) {

    public static GastoDto from(Gasto gasto) {
        return new GastoDto(
                gasto.getId(),
                gasto.getConcepto(),
                gasto.getOrigen(),
                gasto.getFecha(),
                gasto.getMonto(),
                gasto.getTicketExtension() != null,
                UsuarioResumenDto.from(gasto.getRegistradoPor()),
                gasto.getFechaCreacion());
    }
}
