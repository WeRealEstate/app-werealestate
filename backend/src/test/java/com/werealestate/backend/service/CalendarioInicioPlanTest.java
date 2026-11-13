package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.MesCubierto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaPausa;
import com.werealestate.backend.model.VentaReestructuracion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El plan de pagos nunca arranca antes del MES de la fecha de venta (caso S-16: se reestructuró completa en junio y
 * después se corrigió la fecha de venta a julio, y {@code inicioPlan} se quedó en junio). Excepción: si un abono vigente
 * marcó un mes anterior al de la venta, el plan se queda como está.
 */
class CalendarioInicioPlanTest {

    private static final BigDecimal PRECIO = new BigDecimal("300000.00");
    private static final BigDecimal MENSUALIDAD = new BigDecimal("4000.00");

    private static final List<PagoVenta> PAGOS_NINGUNO = List.of();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** Venta normal: 60 mensualidades el día 1, la primera el mes de la venta. */
    private static Venta normal(LocalDate fechaVenta) {
        return new Venta("Cliente", null, null, "Prueba", fechaVenta, MENSUALIDAD, 60, "Enganche", d("60000"), 1, true, null);
    }

    /** La venta tal como quedó S-16: fecha de venta julio, reestructurada completa con el plan arrancando en junio. */
    private static Venta s16(LocalDate inicioPlan) {
        Venta v = normal(LocalDate.of(2026, 7, 1));
        VentaReestructuracion r = new VentaReestructuracion(v, LocalDate.of(2026, 11, 11), d("10000"), d("240000"), "Prueba", null,
                LocalDate.of(2026, 6, 1), "Esquema", MENSUALIDAD, 60, 1, true, "Enganche", d("60000"), "a", "b").comoCompleta();
        v.agregarReestructuracion(r);
        v.aplicarPlanReestructurado("Reestructurada", inicioPlan, MENSUALIDAD, 60, 1, true);
        return v;
    }

    private static PagoVenta pago(String monto, int anio, int mes) {
        PagoVenta p = mock(PagoVenta.class);
        when(p.getMonto()).thenReturn(d(monto));
        when(p.getFecha()).thenReturn(LocalDate.of(2026, 8, 1));
        when(p.getMeses()).thenReturn(List.of(new MesCubierto(anio, mes)));
        return p;
    }

    private static PagoVenta pagoSinMeses(String monto, LocalDate fecha) {
        PagoVenta p = mock(PagoVenta.class);
        when(p.getMonto()).thenReturn(d(monto));
        when(p.getFecha()).thenReturn(fecha);
        when(p.getMeses()).thenReturn(List.of());
        return p;
    }

    private static List<CalendarioPagos.Concepto> conceptos(Venta v, List<VentaAportacion> aportaciones, List<PagoVenta> pagos) {
        return CalendarioPagos.conceptos(v, PRECIO, aportaciones, pagos);
    }

    private static List<CalendarioPagos.Concepto> mensualidades(List<CalendarioPagos.Concepto> cs) {
        return cs.stream().filter(c -> c.numero() != null).toList();
    }

    // ---------------------------------------------------------------- sin cambios (caracterización)

    @Test
    void ventaNormalSinReestructurarNoCambia() {
        Venta v = normal(LocalDate.of(2026, 7, 1));
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of());
        assertThat(cs.get(0).concepto()).isEqualTo("Enganche");
        assertThat(cs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(mensualidades(cs)).hasSize(60);
        assertThat(mensualidades(cs).get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(mensualidades(cs).get(59).fecha()).isEqualTo(LocalDate.of(2031, 6, 1));
    }

    @Test
    void ventaNormalConPrimeraMensualidadEnElMesSiguienteNoCambia() {
        Venta v = new Venta("C", null, null, "P", LocalDate.of(2026, 7, 20), MENSUALIDAD, 60, null, null, 5, false, null);
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of());
        assertThat(cs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    void reestructuracionCompletaCuyoInicioEsElMesDeLaVentaNoCambia() {
        Venta v = s16(LocalDate.of(2026, 7, 1));
        assertThat(mensualidades(conceptos(v, List.of(), List.of())).get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void reestructuracionPorSaldoNoCambia() {
        Venta v = normal(LocalDate.of(2026, 1, 15));
        VentaReestructuracion r = new VentaReestructuracion(v, LocalDate.of(2026, 11, 11), d("100000"), d("200000"), "Prueba", null,
                LocalDate.of(2026, 1, 15), "Esquema", MENSUALIDAD, 60, 15, true, "Enganche", d("60000"), "a", "b");
        v.agregarReestructuracion(r);
        v.aplicarPlanReestructurado("Reestructurada", LocalDate.of(2026, 11, 11), d("5000"), 40, 15, false);
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of());
        // El tramo nuevo arranca en diciembre (el mes siguiente al corte) y el anterior conserva lo pagado desde enero.
        assertThat(cs.stream().filter(c -> c.tramo() == 1 && c.numero() != null).findFirst().orElseThrow().fecha())
                .isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(cs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    // ---------------------------------------------------------------- el bug

    @Test
    void elPlanNoArrancaAntesDelMesDeLaVenta() {
        Venta v = s16(LocalDate.of(2026, 6, 1));
        VentaAportacion feb = new VentaAportacion(v, 2027, 2, d("4000"), null);
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(feb), List.of());
        List<CalendarioPagos.Concepto> ms = cs.stream().filter(c -> c.numero() != null).toList();

        assertThat(ms).hasSize(60);
        assertThat(ms.get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(ms.get(0).numero()).isEqualTo(1);
        // El plazo se respeta completo: cada pago se corre un mes (antes terminaba en mayo de 2031).
        assertThat(ms.get(59).fecha()).isEqualTo(LocalDate.of(2031, 6, 1));
        // La aportación se queda en su mes; solo cambia a qué número sustituye (la 8 en vez de la 9).
        CalendarioPagos.Concepto aportacion = ms.stream().filter(c -> "Aportación".equals(c.concepto())).findFirst().orElseThrow();
        assertThat(aportacion.fecha()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(aportacion.numero()).isEqualTo(8);
        // El pago inicial va el día de la venta, no el del inicio guardado.
        assertThat(cs.get(0).concepto()).isEqualTo("Enganche");
        assertThat(cs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(CalendarioPagos.rangoDeMeses(v, PAGOS_NINGUNO)).containsExactly(YearMonth.of(2026, 7), YearMonth.of(2031, 6));
    }

    @Test
    void conUnPagoMarcadoEnJunioElPlanSeQuedaComoEsta() {
        Venta v = s16(LocalDate.of(2026, 6, 1));
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of(pago("4000", 2026, 6)));
        assertThat(mensualidades(cs).get(0).fecha()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(CalendarioPagos.rangoDeMeses(v, List.of(pago("4000", 2026, 6)))[0]).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    void unPagoMarcadoEnElMesDeLaVentaOPosteriorNoImpideElAjuste() {
        Venta v = s16(LocalDate.of(2026, 6, 1));
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of(pago("4000", 2026, 7), pago("4000", 2026, 8)));
        assertThat(mensualidades(cs).get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void unPagoSinMesesComoUnApartadoAntesDeLaVentaNoImpideElAjuste() {
        Venta v = s16(LocalDate.of(2026, 6, 1));
        List<CalendarioPagos.Concepto> cs = conceptos(v, List.of(), List.of(pagoSinMeses("5000", LocalDate.of(2026, 5, 20))));
        assertThat(mensualidades(cs).get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void laPausaSigueFuncionandoSobreElInicioNuevo() {
        Venta v = s16(LocalDate.of(2026, 6, 1));
        v.agregarPausa(new VentaPausa(v, null, 3, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1), "Prueba", null));
        List<CalendarioPagos.Concepto> ms = mensualidades(conceptos(v, List.of(), List.of()));
        assertThat(ms.get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(ms.get(1).fecha()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(ms.get(2).fecha()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(ms.get(59).fecha()).isEqualTo(LocalDate.of(2031, 10, 1));
    }

    @Test
    void elMesDeLaVentaGanaAunqueElInicioGuardadoSeaDeOtroAnio() {
        Venta v = s16(LocalDate.of(2025, 12, 1));
        assertThat(mensualidades(conceptos(v, List.of(), List.of())).get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
