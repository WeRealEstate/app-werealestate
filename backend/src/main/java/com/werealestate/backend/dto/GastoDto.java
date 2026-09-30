package com.werealestate.backend.dto;

import com.werealestate.backend.model.Gasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GastoDto(
        Long id,
        TipoGastoDto tipoGasto,
        LocalDate fecha,
        BigDecimal monto,
        boolean tieneTicket,
        UsuarioResumenDto registradoPor,
        LocalDateTime fechaCreacion) {

    public static GastoDto from(Gasto gasto) {
        return new GastoDto(
                gasto.getId(),
                TipoGastoDto.from(gasto.getTipoGasto()),
                gasto.getFecha(),
                gasto.getMonto(),
                gasto.getTicketExtension() != null,
                UsuarioResumenDto.from(gasto.getRegistradoPor()),
                gasto.getFechaCreacion());
    }
}
