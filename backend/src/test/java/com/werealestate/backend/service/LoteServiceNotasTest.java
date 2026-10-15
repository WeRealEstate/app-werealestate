package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoteServiceNotasTest {

    @Test
    void unaNotaNulaNoEsDeRegistroDeVenta() {
        // Antes lanzaba NullPointerException y el panel de notificaciones respondía 401 a admin y administración.
        assertFalse(LoteService.esNotaDeRegistroDeVenta(null));
    }

    @Test
    void reconoceLaNotaActualYLaAnterior() {
        assertTrue(LoteService.esNotaDeRegistroDeVenta("Registro de venta We Forms"));
        assertTrue(LoteService.esNotaDeRegistroDeVenta("Precontrato We Forms"));
    }

    @Test
    void otrasNotasNoCuentan() {
        assertFalse(LoteService.esNotaDeRegistroDeVenta("Cliente se retracta"));
        assertFalse(LoteService.esNotaDeRegistroDeVenta(""));
    }
}
