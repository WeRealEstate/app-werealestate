package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.model.EstadoPagoPlan;
import com.werealestate.backend.model.PagoVenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** La columna «Abonado en el mes» ubica cada recibo por su fecha, sin contar nada dos veces. */
class EstadoCuentaAbonadoMesTest {

    private static AmortizacionDto.Fila fila(LocalDate fecha) {
        return new AmortizacionDto.Fila(null, fecha, "Mensualidad", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                EstadoPagoPlan.PENDIENTE, BigDecimal.ZERO);
    }

    private static PagoVenta pago(LocalDate fecha, String monto) {
        PagoVenta p = mock(PagoVenta.class);
        when(p.getFecha()).thenReturn(fecha);
        when(p.getMonto()).thenReturn(new BigDecimal(monto));
        return p;
    }

    @Test
    void cadaReciboVaAlRenglonDeSuMesYLaColumnaSumaLoAbonado() {
        List<AmortizacionDto.Fila> filas = List.of(
                fila(LocalDate.of(2026, 4, 10)), fila(LocalDate.of(2026, 5, 10)), fila(LocalDate.of(2026, 6, 10)));
        List<PagoVenta> pagos = List.of(
                pago(LocalDate.of(2026, 3, 30), "5"), // antes del plan: primer renglón
                pago(LocalDate.of(2026, 4, 10), "10"),
                pago(LocalDate.of(2026, 4, 27), "7.4"),
                pago(LocalDate.of(2026, 6, 22), "10"),
                pago(LocalDate.of(2026, 8, 21), "10")); // después del plan: último mes con renglón

        List<AmortizacionDto.Fila> r = EstadoCuentaService.conAbonadoMes(filas, pagos);

        assertThat(r.get(0).abonadoMes()).isEqualByComparingTo("22.4");
        assertThat(r.get(1).abonadoMes()).isEqualByComparingTo("0");
        assertThat(r.get(2).abonadoMes()).isEqualByComparingTo("20");
        assertThat(r.stream().map(AmortizacionDto.Fila::abonadoMes).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("42.4");
    }

    @Test
    void dosRenglonesDelMismoMesNoCuentanElDineroDosVeces() {
        List<AmortizacionDto.Fila> filas = List.of(fila(LocalDate.of(2026, 4, 10)), fila(LocalDate.of(2026, 4, 30)));
        List<AmortizacionDto.Fila> r = EstadoCuentaService.conAbonadoMes(filas, List.of(pago(LocalDate.of(2026, 4, 12), "10")));
        assertThat(r.get(0).abonadoMes()).isEqualByComparingTo("10");
        assertThat(r.get(1).abonadoMes()).isEqualByComparingTo("0");
    }
}
