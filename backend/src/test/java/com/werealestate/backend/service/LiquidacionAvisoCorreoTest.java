package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Redacción del aviso de liquidación a finanzas: escapado de datos, textos fijos y enlace. */
class LiquidacionAvisoCorreoTest {

    private static LiquidacionAvisoCorreo.ContenidoFinanzas finanzas(String nombre) {
        return new LiquidacionAvisoCorreo.ContenidoFinanzas(
                "S-12", "SAMAI Campestre", List.of("Mz 3 / Lote 7", "Mz 3 / Lote 8"), "C-045", "Asesor <i>X</i>",
                new LiquidacionAvisoCorreo.Persona(nombre, "951 111 2233", "ana@ejemplo.com"),
                List.of(new LiquidacionAvisoCorreo.Persona("Luis Pérez", null, "luis@ejemplo.com")),
                new BigDecimal("350000"), new BigDecimal("350000.00"), LocalDate.of(2026, 11, 5),
                "https://app.weinversiones.com/panel/ventas/S-12");
    }

    @Test
    void finanzasLlevaTodosLosDatosElEnlaceYEscapaLoQueVieneDeDatos() {
        var c = finanzas("Ana <script>alert(1)</script>");
        String html = LiquidacionAvisoCorreo.htmlFinanzas(c);
        assertFalse(html.contains("<script>"), "escapa el nombre");
        assertFalse(html.contains("<i>X</i>"), "escapa el asesor");
        assertTrue(html.contains("¡Es hora de realizar una carta finiquito!"));
        assertTrue(html.contains("951 111 2233"));
        assertTrue(html.contains("ana@ejemplo.com"));
        assertTrue(html.contains("Luis Pérez"));
        assertTrue(html.contains("luis@ejemplo.com"));
        assertTrue(html.contains("S-12"));
        assertTrue(html.contains("C-045"));
        assertTrue(html.contains("$350,000.00"));
        assertTrue(html.contains("05/11/2026"));
        assertTrue(html.contains("Mz 3 / Lote 7<br>Mz 3 / Lote 8"));
        assertTrue(html.contains("<a href=\"https://app.weinversiones.com/panel/ventas/S-12\""));
        String texto = LiquidacionAvisoCorreo.textoFinanzas(c);
        assertTrue(texto.contains("Último pago: 05/11/2026"));
        assertTrue(texto.contains("Ver la venta: https://app.weinversiones.com/panel/ventas/S-12"));
        assertTrue(texto.contains("Copropietario: Luis Pérez · Tel. — · luis@ejemplo.com"));
    }

    @Test
    void elAsuntoDeFinanzasLlevaLaEtiquetaYSinContratoNiCopropietariosNoLosMuestra() {
        assertEquals("¡Es hora de realizar una carta finiquito! – Venta S-12", LiquidacionAvisoCorreo.asuntoFinanzas("S-12"));
        var c = new LiquidacionAvisoCorreo.ContenidoFinanzas("S-1", "SAMAI Campestre", List.of("Mz 1 / Lote 1"), null, "Ana",
                new LiquidacionAvisoCorreo.Persona("Beto", null, null), List.of(), BigDecimal.TEN, BigDecimal.TEN, null,
                "https://x/panel/ventas/S-1");
        String texto = LiquidacionAvisoCorreo.textoFinanzas(c);
        assertFalse(texto.contains("Copropietario"));
        assertTrue(texto.contains("Contrato: —"));
    }
}
