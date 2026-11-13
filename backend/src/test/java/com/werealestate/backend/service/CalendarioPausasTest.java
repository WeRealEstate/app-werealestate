package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaPausa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pausa de mensualidades (ver VentaPausa): la mensualidad N pasa a una fecha posterior y lo que sigue se recorre mes a
 * mes, con el día de la fecha nueva; el plazo sigue completo y los meses de la pausa no tienen renglón ni atraso.
 */
class CalendarioPausasTest {

    private static final BigDecimal PRECIO = new BigDecimal("282936.00");

    /** Como la venta S-17: 60 mensualidades de $2,712.47 el día 1 desde junio de 2026. */
    private static Venta venta() {
        return new Venta("Cliente", null, null, "Prueba", LocalDate.of(2026, 6, 7), new BigDecimal("2712.47"), 60,
                null, null, 1, true, null);
    }

    private static void pausar(Venta v, int desde, LocalDate anterior, LocalDate nueva) {
        v.agregarPausa(new VentaPausa(v, null, desde, anterior, nueva, "Prueba", null));
    }

    private static CalendarioPagos.Concepto numero(List<CalendarioPagos.Concepto> cs, int n) {
        return cs.stream().filter(c -> c.numero() != null && c.numero() == n).findFirst().orElseThrow();
    }

    @Test
    void lasMensualidadesSiguientesSeRecorrenDesdeLaFechaNueva() {
        Venta v = venta();
        pausar(v, 3, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 1));
        // La aportación del mes de la mensualidad 9 (feb 2027) ya se guardó recorrida 5 meses (jul 2027).
        VentaAportacion aportacion = new VentaAportacion(v, 2027, 7, new BigDecimal("26750.00"));

        List<CalendarioPagos.Concepto> cs = CalendarioPagos.conceptos(v, PRECIO, List.of(aportacion), List.of());

        assertThat(cs).hasSize(60);
        assertThat(numero(cs, 2).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(numero(cs, 3).fecha()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(numero(cs, 4).fecha()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(numero(cs, 9).concepto()).isEqualTo("Aportación");
        assertThat(numero(cs, 9).fecha()).isEqualTo(LocalDate.of(2027, 7, 1));
        // Plazo completo: la 60 se va 5 meses después (antes mayo 2031).
        assertThat(numero(cs, 60).fecha()).isEqualTo(LocalDate.of(2031, 10, 1));
        // Ningún renglón en los meses de la pausa.
        assertThat(cs).noneMatch(c -> c.fecha().isAfter(LocalDate.of(2026, 7, 1)) && c.fecha().isBefore(LocalDate.of(2027, 1, 1)));
        assertThat(CalendarioPagos.rangoDeMeses(v, List.of())[1]).isEqualTo(YearMonth.of(2031, 10));
    }

    @Test
    void losMesesDeLaPausaNoCuentanComoAtraso() {
        Venta v = venta();
        pausar(v, 3, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 1));
        List<CalendarioPagos.Concepto> cs = CalendarioPagos.conceptos(v, PRECIO, List.of(), List.of());

        CalendarioPagos.EstadoDeCuenta c =
                CalendarioPagos.estadoDeCuenta(cs, PRECIO, new BigDecimal("5424.94"), LocalDate.of(2026, 11, 12));

        assertThat(c.atrasoMonto()).isEqualByComparingTo("0");
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.proximaFecha()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void elDiaDeLaFechaNuevaEsElDiaDePagoDeAhiEnAdelante() {
        Venta v = venta();
        pausar(v, 3, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 31));
        List<CalendarioPagos.Concepto> cs = CalendarioPagos.conceptos(v, PRECIO, List.of(), List.of());

        assertThat(numero(cs, 2).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(numero(cs, 3).fecha()).isEqualTo(LocalDate.of(2027, 1, 31));
        // Febrero no tiene 31: último día del mes.
        assertThat(numero(cs, 4).fecha()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(numero(cs, 5).fecha()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void variasPausasSeSuman() {
        Venta v = venta();
        pausar(v, 3, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 1));
        pausar(v, 10, LocalDate.of(2027, 8, 1), LocalDate.of(2028, 1, 5));
        List<CalendarioPagos.Concepto> cs = CalendarioPagos.conceptos(v, PRECIO, List.of(), List.of());

        assertThat(cs).hasSize(60);
        assertThat(numero(cs, 9).fecha()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(numero(cs, 10).fecha()).isEqualTo(LocalDate.of(2028, 1, 5));
        assertThat(numero(cs, 11).fecha()).isEqualTo(LocalDate.of(2028, 2, 5));
    }

    @Test
    void unaPausaDeshechaYaNoCuenta() {
        Venta v = venta();
        VentaPausa p = new VentaPausa(v, null, 3, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 1), "Prueba", null);
        v.agregarPausa(p);
        p.deshacer(null, "Error");
        List<CalendarioPagos.Concepto> cs = CalendarioPagos.conceptos(v, PRECIO, List.of(), List.of());

        assertThat(numero(cs, 3).fecha()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(numero(cs, 60).fecha()).isEqualTo(LocalDate.of(2031, 5, 1));
    }
}
