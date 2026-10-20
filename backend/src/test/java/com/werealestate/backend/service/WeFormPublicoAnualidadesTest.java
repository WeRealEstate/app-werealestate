package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.VentaAportacionItemRequest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeFormPublicoAnualidadesTest {

    private static final BigDecimal MONTO = new BigDecimal("10000");

    @Test
    void unaPorAnioEnElMesElegidoHastaLaCantidad() {
        List<VentaAportacionItemRequest> lista =
                WeFormPublicoService.anualidadesComoAportaciones(YearMonth.of(2026, 10), 60, 12, MONTO, 3);
        assertEquals(List.of("12/2026", "12/2027", "12/2028"), lista.stream().map(a -> a.mes() + "/" + a.anio()).toList());
        assertTrue(lista.stream().allMatch(a -> a.monto().equals(MONTO)));
    }

    @Test
    void nuncaMasDeLasQuePermiteElPlazo() {
        // 60 meses: máximo 4 (un último año de mensualidades regulares)
        assertEquals(4, WeFormPublicoService.anualidadesComoAportaciones(YearMonth.of(2026, 10), 60, 12, MONTO, 10).size());
        // 12 meses: ninguna
        assertEquals(0, WeFormPublicoService.anualidadesComoAportaciones(YearMonth.of(2026, 10), 12, 12, MONTO, 1).size());
    }

    @Test
    void nuncaEnElUltimoAnioCalendarioDelPlazo() {
        // De enero 2027 a diciembre 2028 (24 meses): el máximo es 1 y no puede caer en 2028.
        List<VentaAportacionItemRequest> lista =
                WeFormPublicoService.anualidadesComoAportaciones(YearMonth.of(2027, 1), 24, 12, MONTO, 1);
        assertEquals(List.of("12/2027"), lista.stream().map(a -> a.mes() + "/" + a.anio()).toList());
    }
}
