package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.AmortizacionDto.Fila;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.PagoVenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** La tabla mes con mes: cada abono en su mes con su fecha, y el atraso arrastrado hasta el último renglón. */
class TablaMensualTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static PagoVenta pago(int mes, int dia, String monto) {
        PagoVenta p = mock(PagoVenta.class);
        when(p.getFecha()).thenReturn(LocalDate.of(2026, mes, dia));
        when(p.getMonto()).thenReturn(d(monto));
        return p;
    }

    /** La venta de la captura: enganche de 10 mil en abril y 7 mensualidades de $16,680.29 (la última, $16,680.26). */
    private static List<CalendarioPagos.Concepto> plan() {
        List<CalendarioPagos.Concepto> c = new ArrayList<>();
        c.add(new CalendarioPagos.Concepto(null, LocalDate.of(2026, 4, 10), "Enganche", d("10000"), false));
        for (int k = 1; k <= 7; k++) {
            c.add(new CalendarioPagos.Concepto(k, LocalDate.of(2026, 4 + k, 10), "Mensualidad",
                    d(k == 7 ? "16680.26" : "16680.29"), true));
        }
        return c;
    }

    @Test
    void mensualidadEsLoEsperadoMasLoAtrasadoYLosPagosDelMesNoSeSuman() {
        List<PagoVenta> pagos = List.of(
                pago(4, 10, "10000"), pago(4, 27, "7400"), pago(5, 21, "10000"),
                pago(6, 22, "10000"), pago(6, 30, "8500"), pago(7, 21, "10000"), pago(8, 21, "10000"),
                pago(9, 21, "10000"), pago(9, 28, "9412.50"));

        List<Fila> f = TablaMensual.construir(plan(), List.of(), d("126762"), pagos, HOY);

        assertThat(f).hasSize(8);
        // Abril: pagó de más (17,400 contra 10,000): se suma al total pagado y NO baja la mensualidad de mayo.
        assertThat(f.get(0).monto()).isEqualByComparingTo("10000");
        assertThat(f.get(0).pagos()).hasSize(2);
        assertThat(f.get(0).abonadoMes()).isEqualByComparingTo("17400");
        assertThat(f.get(1).monto()).isEqualByComparingTo("16680.29");
        assertThat(f.get(1).arrastre()).isEqualByComparingTo("0");
        // Mayo pagó de menos (10,000 de 16,680.29): el faltante pasa a junio.
        assertThat(f.get(2).monto()).isEqualByComparingTo("23360.58");
        // Junio: dos abonos de días distintos, cada uno en su línea.
        assertThat(f.get(2).pagos()).extracting(p -> p.fecha().getDayOfMonth()).containsExactly(22, 30);
        // Julio también quedó corto: el faltante se suma a agosto, y así hasta el último renglón.
        assertThat(f.get(3).estado()).isEqualTo(EstadoPagoPlan.ABONADO); // pagó algo, no toda la mensualidad
        assertThat(f.get(3).monto()).isEqualByComparingTo("21540.87");
        assertThat(f.get(4).monto()).isEqualByComparingTo("28221.16");
        assertThat(f.get(5).monto()).isEqualByComparingTo("34901.45");
        assertThat(f.get(5).estado()).isEqualTo(EstadoPagoPlan.ABONADO);
        assertThat(f.get(6).monto()).isEqualByComparingTo("32169.24"); // octubre: venció sin pagos
        assertThat(f.get(6).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        assertThat(f.get(7).monto()).isEqualByComparingTo("48849.50"); // noviembre: lo suyo más todo lo atrasado
        assertThat(f.get(7).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
    }

    @Test
    void losMesesFuturosNoArrastranFaltantes() {
        List<CalendarioPagos.Concepto> plan = new ArrayList<>(plan());
        plan.add(new CalendarioPagos.Concepto(8, LocalDate.of(2026, 12, 10), "Mensualidad", d("5"), true));
        List<Fila> f = TablaMensual.construir(plan, List.of(), d("126767"), List.of(pago(4, 10, "10000")), HOY);

        assertThat(f.get(7).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA); // 10/nov: trae el atraso
        assertThat(f.get(7).monto()).isGreaterThan(d("16680.26"));
        assertThat(f.get(8).estado()).isEqualTo(EstadoPagoPlan.PENDIENTE);
        assertThat(f.get(8).monto()).isEqualByComparingTo("5"); // diciembre: solo lo suyo
    }

    @Test
    void alCorrienteSigueIgualAlPlan() {
        List<Fila> f = TablaMensual.construir(plan(), List.of(), d("126762"),
                List.of(pago(4, 10, "10000"), pago(5, 10, "16680.29")), LocalDate.of(2026, 5, 20));
        assertThat(f.get(0).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(f.get(1).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(f.get(2).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(f.get(2).monto()).isEqualByComparingTo("16680.29");
        assertThat(f.get(7).saldo()).isEqualByComparingTo("0");
    }

    @Test
    void losCuatroEstadosPorMesYLaEtiquetaDeACuentaDeComision() {
        PagoVenta aCuenta = mock(PagoVenta.class);
        when(aCuenta.getFecha()).thenReturn(LocalDate.of(2026, 6, 15));
        when(aCuenta.getMonto()).thenReturn(d("16680.29"));
        when(aCuenta.getTipo()).thenReturn(com.werealestate.backend.model.TipoAbono.A_CUENTA_COMISION);
        List<PagoVenta> pagos = List.of(
                pago(4, 10, "10000"),   // enganche cubierto
                pago(5, 10, "5000"),    // mayo: algo, no toda la mensualidad
                aCuenta);               // junio (más lo que faltó de mayo no alcanza, pero llegó dinero): ver abajo

        List<Fila> f = TablaMensual.construir(plan(), List.of(), d("126762"), pagos, HOY);

        assertThat(f.get(0).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(f.get(1).estado()).isEqualTo(EstadoPagoPlan.ABONADO);
        assertThat(f.get(2).pagos()).hasSize(1);
        assertThat(f.get(2).pagos().get(0).aCuentaComision()).isTrue();
        assertThat(f.get(1).pagos().get(0).aCuentaComision()).isFalse();
        // Julio en adelante, vencidos y sin ningún pago: Atrasado.
        assertThat(f.get(3).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        assertThat(f.get(6).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
    }

    private static PagoVenta mensualidad(int mes, int dia, String monto, int... mesesQueCubre) {
        PagoVenta p = pago(mes, dia, monto);
        when(p.getTipo()).thenReturn(com.werealestate.backend.model.TipoAbono.MENSUALIDAD);
        List<com.werealestate.backend.model.MesCubierto> meses = new ArrayList<>();
        for (int m : mesesQueCubre) meses.add(new com.werealestate.backend.model.MesCubierto(2026, m));
        when(p.getMeses()).thenReturn(meses);
        return p;
    }

    @Test
    void elAbonoVaAlMesQueDiceCubrirAunqueSePagueDespues() {
        PagoVenta enganche = pago(5, 2, "10000"); // enganche pagado en mayo: va a su renglón
        when(enganche.getTipo()).thenReturn(com.werealestate.backend.model.TipoAbono.ENGANCHE);
        List<PagoVenta> pagos = List.of(
                enganche,
                mensualidad(5, 10, "16680.29", 5),   // mayo a tiempo
                mensualidad(7, 21, "16680.29", 6));  // pagado en julio, cubre junio

        List<Fila> f = TablaMensual.construir(plan(), List.of(), d("126762"), pagos, LocalDate.of(2026, 7, 25));

        assertThat(f.get(0).pagos()).hasSize(1);
        assertThat(f.get(0).estado()).isEqualTo(EstadoPagoPlan.PAGADO_CON_RETRASO); // enganche del 10/04 pagado el 02/05
        assertThat(f.get(1).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(f.get(2).pagos()).extracting(p -> p.fecha()).containsExactly(LocalDate.of(2026, 7, 21));
        assertThat(f.get(2).estado()).isEqualTo(EstadoPagoPlan.PAGADO_CON_RETRASO);
        assertThat(f.get(3).pagos()).isEmpty(); // julio: el abono de julio no cae aquí
        assertThat(f.get(3).monto()).isEqualByComparingTo("16680.29");
    }

    @Test
    void unAbonoDeVariosMesesLlenaCadaMensualidadYElExcedenteVaALaDeuda() {
        List<PagoVenta> pagos = List.of(pago(4, 10, "10000"), mensualidad(5, 10, "40000", 5, 6));

        List<Fila> f = TablaMensual.construir(plan(), List.of(), d("126762"), pagos, LocalDate.of(2026, 5, 20));

        assertThat(f.get(1).abonadoMes()).isEqualByComparingTo("16680.29");
        assertThat(f.get(1).pagos().get(0).parteDe()).isEqualByComparingTo("40000");
        assertThat(f.get(2).abonadoMes()).isEqualByComparingTo("23319.71");
        assertThat(f.get(2).excedente()).isEqualByComparingTo("6639.42");
        assertThat(f.get(2).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        // El excedente no baja la mensualidad de julio.
        assertThat(f.get(3).monto()).isEqualByComparingTo("16680.29");
    }
}
