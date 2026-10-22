package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.werealestate.backend.service.ComisionService.Porcion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El desglose por abono del ticket: cada entrega cubre lo ganado en orden, después de lo ya entregado. */
class ComisionRepartoEntregaTest {

    private static final List<Porcion> GANADO = List.of(
            new Porcion(LocalDate.of(2026, 10, 6), "Enganche", new BigDecimal("1600.00")),
            new Porcion(LocalDate.of(2026, 11, 6), "Mensualidad", new BigDecimal("500.00")),
            new Porcion(LocalDate.of(2026, 12, 6), "Mensualidad", new BigDecimal("500.00")));

    @Test
    void primeraEntregaCubreElEnganche() {
        List<Porcion> partes = ComisionService.repartirEntrega(GANADO, BigDecimal.ZERO, new BigDecimal("1600.00"));
        assertEquals(1, partes.size());
        assertEquals("Enganche", partes.get(0).concepto());
        assertEquals(0, new BigDecimal("1600.00").compareTo(partes.get(0).monto()));
    }

    @Test
    void entregaQueCruzaVariosAbonos() {
        // Ya se entregaron $1,000 del enganche: esta entrega de $1,200 toma los $600 que quedan del enganche,
        // la primera mensualidad completa y $100 de la segunda.
        List<Porcion> partes = ComisionService.repartirEntrega(GANADO, new BigDecimal("1000.00"), new BigDecimal("1200.00"));
        assertEquals(3, partes.size());
        assertEquals(0, new BigDecimal("600.00").compareTo(partes.get(0).monto()));
        assertEquals(0, new BigDecimal("500.00").compareTo(partes.get(1).monto()));
        assertEquals(0, new BigDecimal("100.00").compareTo(partes.get(2).monto()));
        assertEquals(LocalDate.of(2026, 12, 6), partes.get(2).fecha());
    }

    @Test
    void lasPartesSumanLaEntrega() {
        BigDecimal monto = new BigDecimal("777.77");
        BigDecimal suma = ComisionService.repartirEntrega(GANADO, new BigDecimal("1500.00"), monto).stream()
                .map(Porcion::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, monto.compareTo(suma));
    }
}
