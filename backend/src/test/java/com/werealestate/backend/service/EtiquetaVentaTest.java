package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EtiquetaVenta;
import org.junit.jupiter.api.Test;

/** "S-12" / "N-5": cómo se escribe y se lee el número de una venta por desarrollo. */
class EtiquetaVentaTest {

    private static Desarrollo desarrollo(String nombre) {
        Desarrollo d = mock(Desarrollo.class);
        when(d.getNombre()).thenReturn(nombre);
        return d;
    }

    @Test
    void cadaDesarrolloLlevaSuLetra() {
        assertThat(EtiquetaVenta.de(desarrollo("SAMAI Campestre"), 12L, 72L)).isEqualTo("S-12");
        assertThat(EtiquetaVenta.de(desarrollo("Aldea Nanuu"), 5L, 65L)).isEqualTo("N-5");
        // Sin número por desarrollo o un desarrollo que no numera: cae al número global.
        assertThat(EtiquetaVenta.de(desarrollo("Otro"), 1L, 9L)).isEqualTo("9");
        assertThat(EtiquetaVenta.de(null, null, 9L)).isEqualTo("9");
    }

    @Test
    void leeLoEscritoEnLaDireccionOElBuscador() {
        assertThat(EtiquetaVenta.leer("S-12")).contains(new EtiquetaVenta.Escrita("S", 12));
        assertThat(EtiquetaVenta.leer(" n5 ")).contains(new EtiquetaVenta.Escrita("N", 5));
        assertThat(EtiquetaVenta.leer("s - 7")).contains(new EtiquetaVenta.Escrita("S", 7));
        assertThat(EtiquetaVenta.leer("72")).isEmpty();
        assertThat(EtiquetaVenta.leer("X-3")).isEmpty();
        assertThat(EtiquetaVenta.leer("S-")).isEmpty();
        assertThat(EtiquetaVenta.leer(null)).isEmpty();
        assertThat(EtiquetaVenta.nombreDe("S")).isEqualTo("SAMAI Campestre");
        assertThat(EtiquetaVenta.nombreDe("N")).isEqualTo("Aldea Nanuu");
    }
}
