package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Redacción del recordatorio con la plantilla única de We (diseño del usuario, 2026-11-18): usted, número de cliente,
 * mensualidad, monto, fecha límite, recuadro de atraso solo si debe y sin recargos. */
class RecordatorioPagoCorreoTest {

    private static RecordatorioPagoCorreo.Contenido contenido(String desarrollo, RecordatorioPagoCorreo.ProximoPago proximo,
            BigDecimal atraso, int pagos, int mensualidades) {
        return new RecordatorioPagoCorreo.Contenido(
                "Laura Falces", "S-40", "S-1", List.of(desarrollo), List.of("Mz 1 / Lote 1"), proximo, atraso, pagos, mensualidades);
    }

    private static RecordatorioPagoCorreo.ProximoPago mensualidad(String arrastre) {
        return new RecordatorioPagoCorreo.ProximoPago(
                LocalDate.of(2026, 12, 6), new BigDecimal("6945.44"), "Mensualidad 2 de 24", new BigDecimal(arrastre));
    }

    @Test
    void conAtrasoIncluidoLlevaLaTarjetaYElRecuadro() {
        var c = contenido("SAMAI Campestre", mensualidad("2472.72"), new BigDecimal("2472.72"), 1, 1);
        String html = RecordatorioPagoCorreo.html(c);
        assertTrue(html.contains("Estimada(o) Laura Falces"));
        assertTrue(html.contains("Es momento de su pago mensual."));
        assertTrue(html.contains("S-40"), "número de cliente");
        assertTrue(html.contains("2 de 24"), "mensualidad");
        assertTrue(html.contains("$6,945.44 MXN"), "monto");
        assertTrue(html.contains("6 de diciembre de 2026"), "fecha límite");
        assertTrue(html.contains("Incluye $2,472.72 de pagos anteriores"));
        assertTrue(html.contains("Pagos atrasados"));
        assertTrue(html.contains("1 mensualidad vencida por $2,472.72 MXN. El monto a pagar ya incluye este saldo."));
        assertTrue(html.contains("SAMAI Campestre · Mz 1 / Lote 1 · Venta S-1"), "datos de la compra");
        assertTrue(html.contains("cid:" + RecordatorioPagoCorreo.CID_LOGO) && html.contains("cid:" + RecordatorioPagoCorreo.CID_CIRCULO));
        assertFalse(html.contains("Recargo"), "la app no maneja recargos");
        assertFalse(html.contains("&oacute;"), "acentos sin convertir en entidades");
        for (var img : RecordatorioPagoCorreo.IMAGENES) {
            assertNotNull(getClass().getClassLoader().getResource(img.archivo()), img.archivo());
        }
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("Monto a pagar: $6,945.44 MXN"), texto);
        assertTrue(texto.contains("Fecha límite: 6 de diciembre de 2026"), texto);
    }

    @Test
    void sinAtrasoNoLlevaRecuadroYAplicaANanuu() {
        var c = contenido("Aldea Nanuu", mensualidad("0"), BigDecimal.ZERO, 0, 0);
        String html = RecordatorioPagoCorreo.html(c);
        assertFalse(html.contains("Pagos atrasados"));
        assertFalse(html.contains("Incluye"));
        assertTrue(html.contains("Aldea Nanuu · Mz 1 / Lote 1 · Venta S-1"));
        assertTrue(RecordatorioPagoCorreo.asunto(c).equals("Recordatorio de pago – Aldea Nanuu"));
    }

    @Test
    void sinProximoPagoDiceQueTienePagosPendientes() {
        var c = contenido("SAMAI Campestre", null, new BigDecimal("9000"), 2, 2);
        String html = RecordatorioPagoCorreo.html(c);
        assertTrue(html.contains("Tiene pagos pendientes."));
        assertTrue(html.contains("$9,000.00 MXN"));
        assertFalse(html.contains("Fecha límite"));
        assertTrue(html.contains("2 mensualidades vencidas por $9,000.00 MXN. Le solicitamos atentamente regularizarlo a la brevedad."));
    }
}
