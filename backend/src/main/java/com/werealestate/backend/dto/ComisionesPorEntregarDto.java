package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que toca entregar al sábado indicado (o antes): por comisión, lo acumulado hasta esa fecha
 * que todavía no se entrega. */
public record ComisionesPorEntregarDto(LocalDate sabado, BigDecimal total, List<Item> items) {

    public record Item(ComisionDto comision, BigDecimal montoAlSabado) {
    }
}
