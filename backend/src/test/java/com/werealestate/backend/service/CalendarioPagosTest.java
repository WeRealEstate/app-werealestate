package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Calendario de pagos de una venta y su estado de cuenta (lo abonado cubre del pago más antiguo al más nuevo). */
class CalendarioPagosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 3);

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private static Venta venta(LocalDate fecha, String mensualidad, Integer plazo, String engancheLabel, String enganche,
            int diaPago, boolean primeraEnMesVenta) {
        return new Venta("Cliente", null, null, "Prueba", fecha, mensualidad == null ? null : d(mensualidad), plazo,
                engancheLabel, enganche == null ? null : d(enganche), diaPago, primeraEnMesVenta, null);
    }

    private static CalendarioPagos.EstadoDeCuenta cuenta(Venta v, String precio, String abonado, List<VentaAportacion> aportaciones) {
        List<CalendarioPagos.Concepto> conceptos = CalendarioPagos.conceptos(v, d(precio), aportaciones);
        return CalendarioPagos.estadoDeCuenta(conceptos, d(precio), d(abonado), HOY);
    }

    @Test
    void engancheYMensualidadesCubiertosEnOrden() {
        // El ejemplo de la vista previa: enganche 20 mil, mensualidades de 5 mil, lleva 27 mil abonados.
        Venta v = venta(LocalDate.of(2026, 8, 15), "5000", 32, "Enganche", "20000", 15, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "180000", "27000", List.of());
        List<AmortizacionDto.Fila> f = c.filas();

        assertThat(f).hasSize(33);
        assertThat(f.get(0)).isEqualTo(new AmortizacionDto.Fila(null, LocalDate.of(2026, 8, 15), "Enganche",
                d("20000"), d("20000"), d("160000"), EstadoPagoPlan.PAGADO, BigDecimal.ZERO));
        assertThat(f.get(1)).isEqualTo(new AmortizacionDto.Fila(1, LocalDate.of(2026, 9, 15), "Mensualidad",
                d("5000"), d("5000"), d("155000"), EstadoPagoPlan.PAGADO, BigDecimal.ZERO));
        // La mensualidad 2 venció y solo se abonaron 2 mil: se da por pagada con eso y los 3 mil que faltaron se suman a
        // la siguiente (que aún no vence: le toca por 8 mil).
        assertThat(f.get(2)).isEqualTo(new AmortizacionDto.Fila(2, LocalDate.of(2026, 10, 15), "Mensualidad",
                d("2000"), d("2000"), d("153000"), EstadoPagoPlan.PAGADO, d("-3000")));
        assertThat(f.get(3).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(f.get(3).monto()).isEqualByComparingTo("8000");
        assertThat(f.get(3).arrastre()).isEqualByComparingTo("3000");
        assertThat(f.get(3).fecha()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(f.get(4).estado()).isEqualTo(EstadoPagoPlan.PENDIENTE);
        assertThat(f.get(32).saldo()).isEqualByComparingTo("0");

        assertThat(c.atrasoMonto()).isEqualByComparingTo("0");
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.atrasadoDesde()).isNull();
        assertThat(c.proximaFecha()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(c.proximoMonto()).isEqualByComparingTo("8000");
        assertThat(c.saldo()).isEqualByComparingTo("153000");
    }

    @Test
    void aportacionReemplazaSuMesYLaDeFueraDelPlazoNoLlevaNumero() {
        Venta v = venta(LocalDate.of(2026, 1, 10), "1000", 3, null, null, 10, true);
        List<VentaAportacion> aportaciones = List.of(
                new VentaAportacion(v, 2026, 2, d("5000")), new VentaAportacion(v, 2026, 6, d("2000")));
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "9000", "0", aportaciones);

        assertThat(c.filas()).extracting(AmortizacionDto.Fila::numero).containsExactly(1, 2, 3, null);
        assertThat(c.filas()).extracting(AmortizacionDto.Fila::concepto)
                .containsExactly("Mensualidad", "Aportación", "Mensualidad", "Aportación");
        // Todo vencido y sin abonos: 4 pagos atrasados, 2 de ellos mensualidades.
        assertThat(c.pagosAtrasados()).isEqualTo(4);
        assertThat(c.mensualidadesAtrasadas()).isEqualTo(2);
        assertThat(c.atrasoMonto()).isEqualByComparingTo("9000");
        assertThat(c.atrasadoDesde()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(c.proximaFecha()).isNull();
    }

    @Test
    void losCentavosDelRedondeoLosAbsorbeLaUltimaMensualidad() {
        // 90,000 / 7 = 12,857.142857… → mensualidad 12,857.14: faltan 2 centavos para el precio.
        Venta v = venta(LocalDate.of(2026, 1, 5), "12857.14", 7, "Enganche", "10000", 5, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "100000", "0", List.of());

        assertThat(c.filas()).hasSize(8);
        assertThat(c.filas().get(7).monto()).isEqualByComparingTo("12857.16");
        assertThat(c.filas().get(7).saldo()).isEqualByComparingTo("0");
        assertThat(c.filas()).noneMatch(f -> CalendarioPagos.SIN_FECHA.equals(f.concepto()));
    }

    @Test
    void loEsperadoNuncaPasaDelPrecio() {
        Venta v = venta(LocalDate.of(2027, 1, 5), "3000", 4, null, null, 5, true);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "10000", "0", List.of());

        assertThat(c.filas()).extracting(AmortizacionDto.Fila::monto)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("3000"), d("3000"), d("3000"), d("1000"));
    }

    @Test
    void contadoConSaldoCuentaComoAtrasadoDesdeElDiaSiguienteDeLaVenta() {
        Venta vencida = venta(LocalDate.of(2026, 10, 1), null, null, null, null, 1, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(vencida, "100000", "40000", List.of());
        assertThat(c.filas()).singleElement().satisfies(f -> {
            assertThat(f.concepto()).isEqualTo("Pago de contado");
            assertThat(f.numero()).isNull();
            assertThat(f.pagado()).isEqualByComparingTo("40000");
            assertThat(f.estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        });
        assertThat(c.atrasoMonto()).isEqualByComparingTo("60000");
        assertThat(c.pagosAtrasados()).isEqualTo(1);
        assertThat(c.mensualidadesAtrasadas()).isZero();

        // El mismo día de la venta todavía no está atrasado: le toca.
        Venta deHoy = venta(HOY, null, null, null, null, HOY.getDayOfMonth(), false);
        CalendarioPagos.EstadoDeCuenta hoy = cuenta(deHoy, "100000", "0", List.of());
        assertThat(hoy.filas().get(0).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(hoy.pagosAtrasados()).isZero();
        assertThat(hoy.proximaFecha()).isEqualTo(HOY);
    }

    @Test
    void loAbonadoDeMasCubrePagosFuturos() {
        Venta v = venta(LocalDate.of(2026, 9, 3), "5000", 24, null, null, 3, false);
        // Vencieron 10/03 y 11/03 (hoy vence la de noviembre: no es atraso); abonó 12,000.
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "120000", "12000", List.of());

        assertThat(c.filas().get(0).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(c.filas().get(1).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(c.filas().get(2).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(c.filas().get(2).pagado()).isEqualByComparingTo("2000");
        assertThat(c.proximoMonto()).isEqualByComparingTo("3000");
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.atrasoMonto()).isEqualByComparingTo("0");
    }

    @Test
    void enLaMismaFechaTodosLosPagosSinCubrirLeTocan() {
        // Venta a futuro con la primera mensualidad el mismo día que el enganche.
        Venta v = venta(LocalDate.of(2026, 12, 1), "1000", 10, "Pago inicial", "5000", 1, true);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "15000", "0", List.of());

        assertThat(c.filas().get(0).concepto()).isEqualTo("Pago inicial");
        assertThat(c.filas().get(0).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(c.filas().get(1).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(c.filas().get(2).estado()).isEqualTo(EstadoPagoPlan.PENDIENTE);
        assertThat(c.proximoMonto()).isEqualByComparingTo("6000");
    }

    @Test
    void loQueElPlanNoFechaQuedaEnUnRenglonFinalQueNuncaVence() {
        // Venta vieja con anualidades: el monto de "Aportación anual" no tiene mes, no se proyecta.
        Venta v = venta(LocalDate.of(2025, 1, 10), "2000", 12, "Aportación anual", "40000", 10, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "64000", "30000", List.of());

        AmortizacionDto.Fila ultima = c.filas().get(c.filas().size() - 1);
        assertThat(c.filas()).hasSize(13);
        assertThat(ultima.concepto()).isEqualTo(CalendarioPagos.SIN_FECHA);
        assertThat(ultima.fecha()).isNull();
        assertThat(ultima.monto()).isEqualByComparingTo("40000");
        // Las 12 mensualidades (24 mil) ya vencieron; lo abonado de más (6 mil) va a lo que no tiene fecha.
        assertThat(ultima.pagado()).isEqualByComparingTo("6000");
        assertThat(ultima.estado()).isEqualTo(EstadoPagoPlan.PENDIENTE);
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.filas().stream().map(AmortizacionDto.Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("64000");
    }

    @Test
    void unCentavoNoCuentaComoAtraso() {
        // Vencieron 03/09 y 03/10; le falta un centavo de la segunda.
        Venta v = venta(LocalDate.of(2026, 8, 3), "5000", 24, null, null, 3, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "120000", "9999.99", List.of());

        assertThat(c.filas().get(1).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(c.filas().get(2).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.atrasoMonto()).isEqualByComparingTo("0");
    }

    @Test
    void mensualidadVencidaPagadaEnParteSeDaPorPagadaYSuFaltantePasaALaSiguiente() {
        // Venta real: enganche 10 mil y 7 mensualidades (la última de 16,680.26 por el redondeo); lleva 75,900.
        Venta v = venta(LocalDate.of(2026, 4, 10), "16680.29", 7, "Enganche", "10000", 10, false);
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        List<CalendarioPagos.Concepto> conceptos = CalendarioPagos.conceptos(v, d("126762"), List.of());
        CalendarioPagos.EstadoDeCuenta c = CalendarioPagos.estadoDeCuenta(conceptos, d("126762"), d("75900"), hoy);
        List<AmortizacionDto.Fila> f = c.filas();

        // La 4 se alcanzó a pagar en parte: queda Pagada con lo abonado y los 821.16 que faltaron se suman a la 5.
        assertThat(f.get(4).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(f.get(4).monto()).isEqualByComparingTo("15859.13");
        assertThat(f.get(4).arrastre()).isEqualByComparingTo("-821.16");
        assertThat(f.get(5).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        assertThat(f.get(5).monto()).isEqualByComparingTo("17501.45");
        assertThat(f.get(5).arrastre()).isEqualByComparingTo("821.16");
        assertThat(f.get(6).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        assertThat(f.get(7).estado()).isEqualTo(EstadoPagoPlan.LE_TOCA);

        // El total no cambia (la tabla sigue sumando el precio) y el atraso es el mismo dinero, en 2 pagos en vez de 3.
        assertThat(f.stream().map(AmortizacionDto.Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("126762");
        assertThat(c.atrasoMonto()).isEqualByComparingTo("34181.74");
        assertThat(c.pagosAtrasados()).isEqualTo(2);
        assertThat(c.mensualidadesAtrasadas()).isEqualTo(2);
        assertThat(c.atrasadoDesde()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(c.saldo()).isEqualByComparingTo("50862");
    }

    @Test
    void sinPagoSiguienteLaMensualidadParcialSigueAtrasada() {
        // Una sola mensualidad, ya vencida y pagada en parte: no hay a dónde pasar el faltante.
        Venta v = venta(LocalDate.of(2026, 9, 3), "5000", 1, null, null, 3, false);
        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "5000", "2000", List.of());

        assertThat(c.filas().get(0).estado()).isEqualTo(EstadoPagoPlan.ATRASADO);
        assertThat(c.atrasoMonto()).isEqualByComparingTo("3000");
    }

    @Test
    void reestructuracionRespetaLoPagadoYRepartaElSaldo() {
        // Enganche 20 mil y 32 mensualidades de 5 mil; al 3 de noviembre lleva 27 mil (debía 30 mil: octubre a medias).
        // Se reestructura a 12 meses desde diciembre con una anualidad de 60 mil en junio de 2027.
        Venta v = venta(LocalDate.of(2026, 8, 15), "5000", 32, "Enganche", "20000", 15, false);
        com.werealestate.backend.model.VentaReestructuracion r = new com.werealestate.backend.model.VentaReestructuracion(
                v, HOY, d("27000"), d("153000"), "Cambio a anualidades", null,
                LocalDate.of(2026, 8, 15), "Prueba", d("5000"), 32, 15, false, "Enganche", d("20000"), "antes", "después");
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 7L);
        v.agregarReestructuracion(r);
        v.aplicarPlanReestructurado("Reestructurada", HOY, d("8454.55"), 12, 15, false);
        VentaAportacion anualidad = new VentaAportacion(v, 2027, 6, d("60000"), 7L);
        // Una aportación del plan original (ya reemplazado) no cuenta en el plan nuevo.
        VentaAportacion vieja = new VentaAportacion(v, 2027, 8, d("99999"));

        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "180000", "27000", List.of(anualidad, vieja));
        List<AmortizacionDto.Fila> f = c.filas();

        // Plan original: solo lo que se pagó con él.
        assertThat(f.get(0).concepto()).isEqualTo("Enganche");
        assertThat(f.get(2).monto()).isEqualByComparingTo("2000");
        assertThat(f.subList(0, 3)).allMatch(x -> x.tramo() == 0 && x.estado() == EstadoPagoPlan.PAGADO);
        // Plan nuevo: 12 pagos desde diciembre que suman el saldo; junio es la anualidad.
        List<AmortizacionDto.Fila> nuevo = f.subList(3, f.size());
        assertThat(nuevo).hasSize(12).allMatch(x -> x.tramo() == 1);
        assertThat(nuevo.get(0).fecha()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(nuevo.get(6).concepto()).isEqualTo("Aportación");
        assertThat(nuevo.get(6).monto()).isEqualByComparingTo("60000");
        assertThat(nuevo.stream().map(AmortizacionDto.Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("153000");
        assertThat(c.pagosAtrasados()).isZero();
        assertThat(c.proximaFecha()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(CalendarioPagos.rangoDeMeses(v)).containsExactly(
                java.time.YearMonth.of(2026, 9), java.time.YearMonth.of(2029, 4));
    }

    @Test
    void reestructuracionCompletaRehaceTodoElPlanDesdeLaVenta() {
        // Venta de mayo, mensualidades desde junio de 2,700; lleva 5,400. Se rehace a 10 meses desde junio con una
        // aportación de 10,000 en febrero de 2027: los meses de agosto a octubre (vencidos sin cubrir) salen atrasados.
        Venta v = venta(LocalDate.of(2026, 5, 20), "2700", 100, null, null, 1, false);
        com.werealestate.backend.model.VentaReestructuracion r = new com.werealestate.backend.model.VentaReestructuracion(
                v, HOY, d("5400"), d("37000"), "Ajuste", null,
                LocalDate.of(2026, 5, 20), "Prueba", d("2700"), 100, 1, false, null, null, "antes", "después").comoCompleta();
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 9L);
        v.agregarReestructuracion(r);
        v.aplicarPlanReestructurado("Reestructurada", LocalDate.of(2026, 5, 20), d("3000"), 10, 1, false);
        VentaAportacion aportacion = new VentaAportacion(v, 2027, 2, d("10000"), 9L);

        CalendarioPagos.EstadoDeCuenta c = cuenta(v, "37000", "5400", List.of(aportacion));
        List<AmortizacionDto.Fila> f = c.filas();

        assertThat(f).hasSize(10).allMatch(x -> x.tramo() == 1);
        assertThat(f.get(0).fecha()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(f.get(8).concepto()).isEqualTo("Aportación");
        assertThat(f.stream().map(AmortizacionDto.Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("37000");
        // 5,400 cubren junio (3,000) y parte de julio; julio pasa su faltante a agosto (arrastre) y de ahí en
        // adelante lo vencido sale atrasado.
        assertThat(f.get(0).estado()).isEqualTo(EstadoPagoPlan.PAGADO);
        assertThat(c.pagosAtrasados()).isGreaterThan(0);
        assertThat(c.atrasadoDesde()).isEqualTo(LocalDate.of(2026, 8, 1));
    }
}
