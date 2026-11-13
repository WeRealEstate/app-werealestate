package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoteServiceNotasTest {

    @Test
    void unaNotaNulaNoEsDeRegistroDeVenta() {
        // Antes lanzaba NullPointerException y el panel de notificaciones respondía 401 a admin y administración.
        assertFalse(LoteVentaService.esNotaDeRegistroDeVenta(null));
    }

    @Test
    void reconoceLaNotaActualYLaAnterior() {
        assertTrue(LoteVentaService.esNotaDeRegistroDeVenta("Registro de venta We Forms"));
        assertTrue(LoteVentaService.esNotaDeRegistroDeVenta("Precontrato We Forms"));
    }

    @Test
    void otrasNotasNoCuentan() {
        assertFalse(LoteVentaService.esNotaDeRegistroDeVenta("Cliente se retracta"));
        assertFalse(LoteVentaService.esNotaDeRegistroDeVenta(""));
    }
}
