package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.FinanzasIngresosDto;
import com.werealestate.backend.dto.FinanzasValorDto;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.ReciboVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaComisionDevolucionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Caracterización de los números de Finanzas (ingresos esperados contra recibidos, detalle del mes, valor vendido y
 * cobro por venta) con una venta de 5,000: enganche de 2,000 el 10 de enero y tres mensualidades de 1,000 (feb-abr).
 * Se pagan el enganche, febrero y marzo; el recibo de marzo se anula el 20 de marzo (contra-asiento).
 */
class FinanzasIngresosTest {

    private FinanzasService service;
    private Venta venta;

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    @BeforeEach
    void preparar() {
        VentaRepository ventas = mock(VentaRepository.class);
        VentaLoteRepository ventaLotes = mock(VentaLoteRepository.class);
        VentaAportacionRepository aportaciones = mock(VentaAportacionRepository.class);
        PagoVentaRepository pagos = mock(PagoVentaRepository.class);
        VentaComisionDevolucionRepository devoluciones = mock(VentaComisionDevolucionRepository.class);
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario actual = mock(Usuario.class);
        when(actual.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(actual);

        Desarrollo desarrollo = mock(Desarrollo.class);
        when(desarrollo.getId()).thenReturn(10L);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");
        Lote lote = new Lote(desarrollo, "2", "7", d("200"));
        ReflectionTestUtils.setField(lote, "id", 1L);

        venta = new Venta("Cliente Uno", null, null, "Prueba", LocalDate.of(2026, 1, 10), d("1000"), 3,
                "Enganche", d("2000"), 10, false, null);
        ReflectionTestUtils.setField(venta, "id", 1L);
        VentaLote vl = new VentaLote(venta, lote, d("5000"));

        PagoVenta enganche = pago(1, "2026-01-10", TipoAbono.ENGANCHE, "2000", null);
        PagoVenta febrero = pago(2, "2026-02-12", TipoAbono.MENSUALIDAD, "1000", null);
        PagoVenta marzo = pago(3, "2026-03-05", TipoAbono.MENSUALIDAD, "1000", LocalDate.of(2026, 3, 20));

        when(ventas.findAll()).thenReturn(List.of(venta));
        when(ventaLotes.findAllConLoteYDesarrollo()).thenReturn(List.of(vl));
        when(aportaciones.findAll()).thenReturn(List.of());
        when(pagos.findAllConRecibo()).thenReturn(List.of(enganche, febrero, marzo));
        when(pagos.findAllVigentes()).thenReturn(List.of(enganche, febrero));
        when(pagos.totalVigente()).thenReturn(d("3000"));
        when(devoluciones.findAll()).thenReturn(List.of());
        service = new FinanzasService(ventas, ventaLotes, aportaciones, pagos, devoluciones, provider);
    }

    private PagoVenta pago(long id, String fecha, TipoAbono tipo, String monto, LocalDate anulado) {
        ReciboVenta recibo = new ReciboVenta(venta, "F" + id, LocalDate.parse(fecha), null, null);
        if (anulado != null) recibo.anular("error", null, anulado);
        PagoVenta p = new PagoVenta(venta, recibo, d(monto), tipo, null, List.of(), false);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void ingresosPorMesConContraAsientoDeReciboAnulado() {
        FinanzasIngresosDto r = service.ingresos("2026-01", "2026-04", null);

        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::mes)
                .containsExactly("2026-01", "2026-02", "2026-03", "2026-04");
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::esperado)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("2000"), d("1000"), d("1000"), d("1000"));
        // Marzo: entra el abono de 1,000 (día 5) y sale el contra-asiento de -1,000 (día 20 del mismo mes).
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::recibido)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("2000"), d("1000"), d("0"), d("0"));
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::diferencia)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0"), d("0"), d("-1000"), d("-1000"));
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::atrasoAcumulado)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0"), d("0"), d("1000"), d("2000"));
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::ventas).containsExactly(1, 1, 1, 1);
        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::futuro).containsOnly(false);
    }

    @Test
    void elRangoSoloRecortaLasFilasPeroElAtrasoSigueDesdeElPrimerMes() {
        FinanzasIngresosDto r = service.ingresos("2026-03", "2026-03", null);

        assertThat(r.meses()).hasSize(1);
        assertThat(r.meses().get(0).mes()).isEqualTo("2026-03");
        assertThat(r.meses().get(0).atrasoAcumulado()).isEqualByComparingTo("1000");
    }

    @Test
    void otroDesarrolloNoTraeNada() {
        FinanzasIngresosDto r = service.ingresos("2026-01", "2026-04", 99L);

        assertThat(r.meses()).extracting(FinanzasIngresosDto.IngresoMes::esperado)
                .usingElementComparator(BigDecimal::compareTo)
                .doesNotContain(d("2000"));
    }

    @Test
    void detalleDeMarzoTraeLoEsperadoElAbonoYElContraAsiento() {
        FinanzasIngresosDto.Detalle det = service.ingresosDelMes("2026-03", 10L);

        assertThat(det.esperados()).hasSize(1);
        assertThat(det.esperados().get(0).concepto()).isEqualTo("Mensualidad 2/3");
        assertThat(det.esperados().get(0).fechaEsperada()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(det.esperados().get(0).desarrollo()).isEqualTo("SAMAI Campestre");
        assertThat(det.recibidos()).extracting(FinanzasIngresosDto.Abono::concepto)
                .containsExactly("Abono", "Anulación de recibo");
        assertThat(det.recibidos()).extracting(FinanzasIngresosDto.Abono::monto)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("1000"), d("-1000"));
        assertThat(det.devolucionesComision()).isEmpty();
    }

    @Test
    void valorVendidoTotalCobradoYPorCobrar() {
        FinanzasValorDto v = service.valorVendido();

        assertThat(v.total()).isEqualByComparingTo("5000");
        assertThat(v.lotes()).isEqualTo(1);
        assertThat(v.cobrado()).isEqualByComparingTo("3000");
        assertThat(v.saldoPendiente()).isEqualByComparingTo("2000");
        assertThat(v.porDesarrollo()).hasSize(1);
        assertThat(v.detalle()).hasSize(1);
    }

    @Test
    void cobroPorVentaCuentaElAtrasoDeLosMesesVencidos() {
        FinanzasService.CobroVenta c = service.cobroPorVenta().get(1L);

        // Enganche y febrero pagados; marzo y abril de 2026 ya vencieron (hoy es posterior): falta lo de esos meses.
        assertThat(c.abonado()).isEqualByComparingTo("3000");
        assertThat(c.atrasoMonto()).isEqualByComparingTo("2000");
        assertThat(c.pagosAtrasados()).isEqualTo(2);
        assertThat(c.mensualidadesAtrasadas()).isEqualTo(2);
    }
}
