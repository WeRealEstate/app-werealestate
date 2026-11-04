package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ComisionDesgloseDto;
import com.werealestate.backend.dto.ComisionEntregaRequest;
import com.werealestate.backend.dto.ComisionesPorEntregarDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.ModalidadComision;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.model.VentaComisionDevengo;
import com.werealestate.backend.model.VentaComisionEntrega;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.TipoGastoRepository;
import com.werealestate.backend.repository.VentaComisionDevengoRepository;
import com.werealestate.backend.repository.VentaComisionDevolucionRepository;
import com.werealestate.backend.repository.VentaComisionEntregaAbonoRepository;
import com.werealestate.backend.repository.VentaComisionEntregaRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** El desglose de comisiones (Finanzas → Comisiones): las cifras cuadran, por asesor y por desarrollo; y una comisión
 * cancelada ya no cuenta, no sale en la lista del sábado ni se puede entregar. */
class ComisionDesgloseTest {

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate SABADO = HOY.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

    private VentaComisionRepository comisiones;
    private final List<VentaComision> todas = new ArrayList<>();
    private final List<VentaComisionDevengo> devengos = new ArrayList<>();
    private final List<VentaComisionEntrega> entregas = new ArrayList<>();
    private final List<Object[]> desarrollos = new ArrayList<>();
    private ComisionService service;
    private long siguienteId = 1;

    @BeforeEach
    void preparar() {
        comisiones = mock(VentaComisionRepository.class);
        when(comisiones.findAll()).thenReturn(todas);
        when(comisiones.findAllByOrderByIdDesc()).thenReturn(todas);
        VentaComisionDevengoRepository devengoRepo = mock(VentaComisionDevengoRepository.class);
        when(devengoRepo.findAllConPagoOrdenados()).thenReturn(devengos);
        VentaComisionEntregaRepository entregaRepo = mock(VentaComisionEntregaRepository.class);
        when(entregaRepo.findAll()).thenReturn(entregas);
        VentaLoteRepository ventaLotes = mock(VentaLoteRepository.class);
        when(ventaLotes.desarrollosPorVenta()).thenReturn(desarrollos);
        FinanzasService finanzas = mock(FinanzasService.class);
        when(finanzas.cobroPorVenta()).thenReturn(Map.of());
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(admin);
        service = new ComisionService(comisiones, devengoRepo, entregaRepo, mock(VentaComisionEntregaAbonoRepository.class),
                mock(VentaComisionDevolucionRepository.class), mock(VentaRepository.class), ventaLotes,
                mock(PagoVentaRepository.class), mock(GastoRepository.class), mock(TipoGastoRepository.class), finanzas,
                provider);
    }

    private static Usuario interno(long id, String nombre) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id);
        when(u.getNombre()).thenReturn(nombre);
        return u;
    }

    private static AsesorExterno externo(long id, String nombre) {
        AsesorExterno a = new AsesorExterno(nombre, "95100000" + id, null);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    /** Una venta con lotes de esos desarrollos y contrato firmado o no. */
    private Venta venta(long id, boolean contratoFirmado, String... desarrollosDeLaVenta) {
        Venta v = mock(Venta.class);
        when(v.getId()).thenReturn(id);
        when(v.getNumero()).thenReturn(id);
        when(v.isContratoFirmado()).thenReturn(contratoFirmado);
        for (String d : desarrollosDeLaVenta) desarrollos.add(new Object[] {id, d});
        return v;
    }

    private VentaComision comision(Venta venta, Usuario usuario, AsesorExterno externo, String monto, boolean reglaNueva) {
        VentaComision c = new VentaComision(venta, "Cliente " + siguienteId, usuario, externo, new BigDecimal("100000"),
                new BigDecimal("5"), new BigDecimal(monto), ModalidadComision.PARCIALIDADES);
        if (reglaNueva) c.marcarReglaNueva();
        ReflectionTestUtils.setField(c, "id", siguienteId++);
        when(comisiones.findById(c.getId())).thenReturn(Optional.of(c));
        todas.add(c);
        return c;
    }

    private void gano(VentaComision c, TipoAbono tipo, String montoAbono, String aporte, LocalDate entrega) {
        PagoVenta pago = mock(PagoVenta.class);
        when(pago.getTipo()).thenReturn(tipo);
        when(pago.getMonto()).thenReturn(new BigDecimal(montoAbono));
        devengos.add(new VentaComisionDevengo(c, pago, entrega.minusDays(3), entrega, new BigDecimal(aporte)));
    }

    private void entregado(VentaComision c, String monto) {
        VentaComisionEntrega e = mock(VentaComisionEntrega.class);
        when(e.getComision()).thenReturn(c);
        when(e.getMonto()).thenReturn(new BigDecimal(monto));
        entregas.add(e);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperaba " + esperado + " y fue " + real);
    }

    /** Ana (interna, SAMAI): ganó 3,000 (1,000 retrasado, 1,000 para este sábado y 1,000 después), se le entregaron
     * 500. Beto (externo, Nanuu): contrato sin firmar con 2,000 ganados (detenido). Ceci (interna, venta eliminada):
     * se le entregaron 1,200 de 1,000 ganados. Una cancelada de Ana con 700 ganados no cuenta. Una venta mixta de
     * Beto con 300 ganados para este sábado. */
    private void escenario() {
        Usuario ana = interno(5, "Ana");
        AsesorExterno beto = externo(3, "Beto");
        Usuario ceci = interno(8, "Ceci");

        VentaComision deAna = comision(venta(10, true, "SAMAI Campestre"), ana, null, "5000", false);
        gano(deAna, TipoAbono.MENSUALIDAD, "2000", "1000", SABADO.minusWeeks(2));
        gano(deAna, TipoAbono.MENSUALIDAD, "2000", "1000", SABADO);
        gano(deAna, TipoAbono.MENSUALIDAD, "2000", "1000", SABADO.plusWeeks(1));
        entregado(deAna, "500");

        VentaComision deBeto = comision(venta(11, false, "Aldea Nanuu"), null, beto, "4000", true);
        gano(deBeto, TipoAbono.ENGANCHE, "2000", "2000", SABADO.minusWeeks(1));

        VentaComision deCeci = comision(null, ceci, null, "1000", false);
        gano(deCeci, TipoAbono.MENSUALIDAD, "2000", "1000", SABADO.minusWeeks(3));
        entregado(deCeci, "1200");

        VentaComision cancelada = comision(venta(12, true, "SAMAI Campestre"), ana, null, "3000", false);
        gano(cancelada, TipoAbono.MENSUALIDAD, "1400", "700", SABADO.minusWeeks(2));
        cancelada.setCancelada(true);

        VentaComision mixta = comision(venta(13, true, "SAMAI Campestre", "Aldea Nanuu"), null, beto, "1000", true);
        gano(mixta, TipoAbono.ENGANCHE, "300", "300", SABADO);
    }

    @Test
    void elTotalCuadraYSeReparte() {
        escenario();

        ComisionDesgloseDto d = service.desglose();
        ComisionDesgloseDto.Cifras t = d.total();

        assertEquals(SABADO, d.sabado());
        assertEquals(4, t.comisiones());
        igual("11000", t.total());
        igual("6300", t.ganado());
        igual("4700", t.faltaPorGenerar());
        igual("1700", t.entregado());
        igual("2800", t.porEntregar());
        igual("500", t.retrasado());
        igual("1300", t.tocaSabado());
        igual("1000", t.masAdelante());
        igual("2000", t.detenido());
        igual("200", t.entregadoDeMas());
        assertEquals(1, d.comisionesDetenidas());

        // Las sumas que muestra la pantalla.
        igual(t.total().toPlainString(), t.faltaPorGenerar().add(t.ganado()));
        igual(t.ganado().toPlainString(),
                t.entregado().add(t.porEntregar()).add(t.detenido()).subtract(t.entregadoDeMas()));
        igual(t.porEntregar().toPlainString(), t.retrasado().add(t.tocaSabado()).add(t.masAdelante()));
    }

    @Test
    void porAsesorPorNombreYSinLaCancelada() {
        escenario();

        List<ComisionDesgloseDto.Fila> filas = service.desglose().porAsesor();

        assertEquals(List.of("Ana", "Beto", "Ceci"), filas.stream().map(ComisionDesgloseDto.Fila::nombre).toList());
        ComisionDesgloseDto.Fila ana = filas.get(0);
        assertEquals("U-5", ana.clave());
        assertFalse(ana.externo());
        assertEquals(1, ana.cifras().comisiones());
        igual("2500", ana.cifras().porEntregar());
        ComisionDesgloseDto.Fila beto = filas.get(1);
        assertEquals("E-3", beto.clave());
        assertTrue(beto.externo());
        assertEquals(2, beto.cifras().comisiones());
        igual("2000", beto.cifras().detenido());
        igual("300", beto.cifras().porEntregar());
        igual("200", filas.get(2).cifras().entregadoDeMas());
    }

    @Test
    void porDesarrolloConVariosYEliminadaAlFinal() {
        escenario();

        List<ComisionDesgloseDto.Fila> filas = service.desglose().porDesarrollo();

        assertEquals(List.of("Aldea Nanuu", "SAMAI Campestre", "Varios desarrollos", "Venta eliminada"),
                filas.stream().map(ComisionDesgloseDto.Fila::nombre).toList());
        igual("2000", filas.get(0).cifras().ganado());
        igual("3000", filas.get(1).cifras().ganado());
        igual("300", filas.get(2).cifras().ganado());
        igual("1000", filas.get(3).cifras().ganado());
    }

    @Test
    void laListaDelSabadoTraeLosAbonosYNoLasCanceladas() {
        escenario();

        ComisionesPorEntregarDto pe = service.porEntregar(null);

        assertEquals(SABADO, pe.sabado());
        assertEquals(List.of("Cliente 1", "Cliente 5"), pe.items().stream().map(i -> i.comision().cliente()).toList());
        ComisionesPorEntregarDto.Item deAna = pe.items().get(0);
        igual("1500", deAna.montoAlSabado());
        igual("2000", deAna.ganadoAlSabado());
        igual("500", deAna.yaEntregado());
        assertEquals(2, deAna.abonos().size());
        assertEquals("Mensualidad", deAna.abonos().get(0).concepto());
        assertEquals(50, deAna.abonos().get(0).porcentaje());
        assertEquals(false, deAna.abonos().get(0).parcial());
        igual("2000", deAna.abonos().get(0).montoAbono());
        assertEquals(100, pe.items().get(1).abonos().get(0).porcentaje());
        igual("1800", pe.total());
    }

    @Test
    void elUltimoAbonoQueCompletaLaComisionLoDice() {
        Usuario ana = interno(5, "Ana");
        VentaComision c = comision(venta(10, true, "SAMAI Campestre"), ana, null, "1500", true);
        gano(c, TipoAbono.ENGANCHE, "1000", "1000", SABADO.minusWeeks(1));
        gano(c, TipoAbono.MENSUALIDAD, "2000", "500", SABADO);

        ComisionesPorEntregarDto.Item item = service.porEntregar(null).items().get(0);

        assertEquals(50, item.abonos().get(1).porcentaje());
        assertEquals(true, item.abonos().get(1).parcial());
        assertEquals(false, item.abonos().get(0).parcial());
    }

    @Test
    void unaCanceladaNoCuentaEnRetrasadasNiSeEntrega() {
        escenario();
        VentaComision cancelada = todas.get(3);

        assertEquals(1, service.resumen().retrasadas());
        igual("500", service.resumen().montoRetrasado());
        ConflictException e = assertThrows(ConflictException.class, () -> service.entregar(cancelada.getId(),
                new ComisionEntregaRequest(new BigDecimal("100"), HOY, null, true, false, null)));
        assertTrue(e.getMessage().contains("cancelada"));
        assertTrue(service.listar().stream().filter(c -> c.id().equals(cancelada.getId()))
                .allMatch(c -> !c.retrasada() && c.porEntregar().signum() == 0));
    }
}
