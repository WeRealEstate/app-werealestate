package com.werealestate.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EstadosMexicoTest {

    @Test
    void tieneLosTreintaYDosEstados() {
        assertEquals(32, EstadosMexico.todos().size());
        assertEquals(32, EstadosMexico.todos().stream().distinct().count());
    }

    @Test
    void reconoceUnEstadoSinImportarMayusculasNiAcentos() {
        assertEquals("Oaxaca", EstadosMexico.canonico("oaxaca"));
        assertEquals("Nuevo León", EstadosMexico.canonico("NUEVO LEON"));
        assertEquals("Ciudad de México", EstadosMexico.canonico(" ciudad de mexico "));
    }

    @Test
    void rechazaLoQueNoEsUnEstadoDeLaLista() {
        assertNull(EstadosMexico.canonico("CDMX"));
        assertNull(EstadosMexico.canonico("Antioquia"));
        assertNull(EstadosMexico.canonico(null));
    }

    @Test
    void mexicoEsUnPaisDeLaLista() {
        assertEquals(Paises.MEXICO, Paises.canonico("mexico"));
    }
}
