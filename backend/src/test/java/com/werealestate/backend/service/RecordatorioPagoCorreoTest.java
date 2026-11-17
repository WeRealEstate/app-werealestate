package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Redacción del recordatorio: la plantilla propia de SAMAI (diseño del usuario) y la genérica de los demás
 * desarrollos. Cuando el próximo pago ya trae lo que faltó de un mes anterior, el correo lo desglosa y aclara que el
 * saldo vencido ya va incluido, para que el cliente no sume las dos cantidades. */
class RecordatorioPagoCorreoTest {

    private static RecordatorioPagoCorreo.Contenido contenido(String desarrollo, BigDecimal arrastre, BigDecimal atraso) {
        var proximo = new RecordatorioPagoCorreo.ProximoPago(
                LocalDate.of(2026, 12, 6), new BigDecimal("6945.44"), "Mensualidad 2 de 24", arrastre);
        return new RecordatorioPagoCorreo.Contenido(
                "Laura Falces", "S-1", List.of(desarrollo), List.of("Mz 1 / Lote 1"), proximo, atraso, 0, 0);
    }

    @Test
    void samaiConPendienteIncluidoDesglosaElMontoYMuestraElAtraso() {
        var c = contenido("SAMAI Campestre", new BigDecimal("2472.72"), new BigDecimal("2472.72"));
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("le recordamos que su día de pago es el próximo 6 de diciembre de 2026 por un monto de "
                + "$6,945.44 (mensualidad $4,472.72 + $2,472.72 pendientes de pagos anteriores), correspondiente a: "
                + "Mensualidad 2 de 24."), texto);
        assertTrue(texto.contains("ya está incluido en el monto de su próximo pago"), texto);
        assertTrue(texto.contains("Número de venta: S-1"), texto);
        String html = RecordatorioPagoCorreo.html(c);
        assertTrue(html.contains("cid:" + RecordatorioPagoCorreo.CID_BANNER), "lleva el banner incrustado");
        assertTrue(html.contains("#FDECEA"), "recuadro rojo del atraso");
        // El HTML escapa los acentos (pr&oacute;ximo); se ven bien en cualquier lector de correo.
        assertTrue(html.contains("Su pr&oacute;ximo pago es el 6 de diciembre de 2026 por $6,945.44."), "vista previa en la bandeja");
        assertEquals("correo/banner-samai.jpg", RecordatorioPagoCorreo.banner(c));
        assertNotNull(RecordatorioPagoCorreoTest.class.getClassLoader().getResource("correo/banner-samai.jpg"));
    }

    @Test
    void samaiSinAtrasoNoLlevaRecuadroRojo() {
        var c = contenido("SAMAI Campestre", BigDecimal.ZERO, BigDecimal.ZERO);
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("por un monto de $6,945.44, correspondiente a: Mensualidad 2 de 24."), texto);
        assertFalse(texto.contains("saldo vencido"), texto);
        assertFalse(RecordatorioPagoCorreo.html(c).contains("#FDECEA"));
    }

    @Test
    void otroDesarrolloUsaLaPlantillaGenericaSinBanner() {
        var c = contenido("Aldea Nanuu", new BigDecimal("2472.72"), new BigDecimal("2472.72"));
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("Monto: $6,945.44 (mensualidad $4,472.72 + $2,472.72 pendientes de pagos anteriores)"), texto);
        assertNull(RecordatorioPagoCorreo.banner(c));
        assertFalse(RecordatorioPagoCorreo.html(c).contains("cid:"));
    }
}
