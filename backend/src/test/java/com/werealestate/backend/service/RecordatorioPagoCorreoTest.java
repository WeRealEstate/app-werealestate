package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Redacción del recordatorio cuando el próximo pago ya trae lo que faltó de un mes anterior (la tabla pasa el faltante
 * a la mensualidad siguiente): el correo lo desglosa y aclara que el saldo vencido ya va incluido, para que el cliente
 * no sume las dos cantidades. */
class RecordatorioPagoCorreoTest {

    private static RecordatorioPagoCorreo.Contenido contenido(BigDecimal arrastre, BigDecimal atraso) {
        var proximo = new RecordatorioPagoCorreo.ProximoPago(
                LocalDate.of(2026, 12, 6), new BigDecimal("6945.44"), "Mensualidad 2 de 24", arrastre);
        return new RecordatorioPagoCorreo.Contenido(
                "Laura Falces", "S-1", List.of("SAMAI Campestre"), List.of("Mz 1 / Lote 1"), proximo, atraso, 0, 0);
    }

    @Test
    void conPendienteIncluidoDesglosaElMontoYAclaraQueYaVaIncluido() {
        var c = contenido(new BigDecimal("2472.72"), new BigDecimal("2472.72"));
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("Monto: $6,945.44 (mensualidad $4,472.72 + $2,472.72 pendientes de pagos anteriores)"), texto);
        assertTrue(texto.contains("ya está incluido en el monto de su próximo pago"), texto);
        assertTrue(RecordatorioPagoCorreo.html(c).contains("pendientes de pagos anteriores"));
    }

    @Test
    void sinPendienteElMontoVaSolo() {
        var c = contenido(BigDecimal.ZERO, BigDecimal.ZERO);
        String texto = RecordatorioPagoCorreo.texto(c);
        assertTrue(texto.contains("Monto: $6,945.44\n"), texto);
        assertFalse(texto.contains("pendientes de pagos anteriores"), texto);
        assertFalse(texto.contains("saldo vencido"), texto);
    }
}
