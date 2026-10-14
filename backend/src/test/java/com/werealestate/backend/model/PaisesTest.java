package com.werealestate.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaisesTest {

    @Test
    void reconoceUnPaisSinImportarMayusculasNiAcentos() {
        assertEquals("México", Paises.canonico("mexico"));
        assertEquals("México", Paises.canonico("  MÉXICO "));
        assertEquals("Estados Unidos", Paises.canonico("estados unidos"));
    }

    @Test
    void rechazaTextoQueNoEsUnPais() {
        assertNull(Paises.canonico("Narnia"));
        assertNull(Paises.canonico(""));
        assertNull(Paises.canonico(null));
    }

    @Test
    void laListaCargaCompletaYSinRepetidos() {
        assertTrue(Paises.todos().size() >= 190);
        assertEquals(Paises.todos().size(), Paises.todos().stream().distinct().count());
    }
}
