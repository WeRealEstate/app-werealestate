package com.werealestate.backend.dto;

import com.werealestate.backend.model.FrecuenciaGasto;
import com.werealestate.backend.model.GastoRecurrente;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GastoRecurrenteDto(
        Long id,
        String nombre,
        BigDecimal montoEstimado,
        FrecuenciaGasto frecuencia,
        Integer dia,
        Integer dia2,
        LocalDate primerVencimiento,
        boolean activo,
        // Fecha del vencimiento más próximo aún sin pagar (puede estar en el pasado si está vencido).
        LocalDate proximoVencimiento,
        // Cuántos vencimientos siguen sin pagar ni omitir.
        int sinPagar) {

    public static GastoRecurrenteDto from(GastoRecurrente g, LocalDate proximoVencimiento, int sinPagar) {
        return new GastoRecurrenteDto(
                g.getId(),
                g.getNombre(),
                g.getMontoEstimado(),
                g.getFrecuencia(),
                g.getDia(),
                g.getDia2(),
                g.getPrimerVencimiento(),
                g.isActivo(),
                proximoVencimiento,
                sinPagar);
    }
}
