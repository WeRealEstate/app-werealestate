package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ComisionDetalleDto;
import com.werealestate.backend.dto.ComisionDevolucionRequest;
import com.werealestate.backend.dto.ComisionEntregaRequest;
import com.werealestate.backend.dto.ComisionUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.EstadoComision;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.ModalidadComision;
import com.werealestate.backend.model.ModalidadPago;
import com.werealestate.backend.model.OrigenGasto;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.TipoGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.model.VentaComisionDevengo;
import com.werealestate.backend.model.VentaComisionDevolucion;
import com.werealestate.backend.model.VentaComisionEntrega;
import com.werealestate.backend.model.VentaComisionEntregaAbono;
import com.werealestate.backend.model.VentaLote;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Caracterización del cálculo de ComisionService: qué se gana de cada abono (regla nueva por tipo y regla anterior),
 * quién puede cuánto (editar, entregar, devolver) y la sincronización entrega ↔ gasto. Fija el comportamiento tal
 * como estaba antes de limpiar el servicio. Los repositorios son en memoria (mocks con listas). */
class ComisionCalculoTest {

    // Un miércoles: su sábado siguiente es el 2026-11-07.
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 11, 4);
    private static final LocalDate SABADO = LocalDate.of(2026, 11, 7);

    private VentaComisionRepository comisiones;
    private VentaComisionDevengoRepository devengoRepo;
    private VentaComisionEntregaRepository entregaRepo;
    private VentaComisionEntregaAbonoRepository abonoRepo;
    private VentaComisionDevolucionRepository devolucionRepo;
    private PagoVentaRepository pagos;
    private VentaLoteRepository ventaLotes;
    private GastoRepository gastos;
    private final List<VentaComisionDevengo> devengos = new ArrayList<>();
    private final List<VentaComisionEntrega> entregas = new ArrayList<>();
    private final List<VentaComisionEntregaAbono> abonosDeTicket = new ArrayList<>();
    private final List<VentaComisionDevolucion> devoluciones = new ArrayList<>();
    private final List<Gasto> gastosGuardados = new ArrayList<>();
    private final List<PagoVenta> pagosVigentes = new ArrayList<>();
    private Venta venta;
    private ComisionService service;
    private long siguienteId = 1;

    @BeforeEach
    void preparar() {
        comisiones = mock(VentaComisionRepository.class);
        when(comisiones.save(any(VentaComision.class))).thenAnswer(inv -> {
            VentaComision c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", siguienteId++);
            return c;
        });
        devengoRepo = mock(VentaComisionDevengoRepository.class);
        when(devengoRepo.save(any(VentaComisionDevengo.class))).thenAnswer(inv -> {
            devengos.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        doAnswer(inv -> {
            devengos.removeIf(d -> d.getComision().getId().equals(inv.getArgument(0)));
            return null;
        }).when(devengoRepo).deleteByComisionId(anyLong());
        when(devengoRepo.findByComisionIdOrderByFechaEntregaAscIdAsc(anyLong())).thenAnswer(inv -> devengos.stream()
                .filter(d -> d.getComision().getId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(VentaComisionDevengo::getFechaEntrega))
                .toList());
        entregaRepo = mock(VentaComisionEntregaRepository.class);
        when(entregaRepo.save(any(VentaComisionEntrega.class))).thenAnswer(inv -> {
            VentaComisionEntrega e = inv.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", siguienteId++);
                entregas.add(e);
            }
            return e;
        });
        when(entregaRepo.existsByComisionId(anyLong())).thenAnswer(inv -> !entregas.isEmpty());
        when(entregaRepo.findByComisionIdOrderByFechaDescIdDesc(anyLong())).thenAnswer(inv -> entregas.stream()
                .sorted(Comparator.comparing(VentaComisionEntrega::getId).reversed()).toList());
        when(entregaRepo.findById(anyLong())).thenAnswer(inv -> entregas.stream()
                .filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        when(entregaRepo.findByGastoId(anyLong())).thenAnswer(inv -> entregas.stream()
                .filter(e -> e.getGasto() != null && e.getGasto().getId().equals(inv.getArgument(0))).findFirst());
        abonoRepo = mock(VentaComisionEntregaAbonoRepository.class);
        when(abonoRepo.save(any(VentaComisionEntregaAbono.class))).thenAnswer(inv -> {
            abonosDeTicket.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        doAnswer(inv -> {
            abonosDeTicket.removeIf(a -> ReflectionTestUtils.getField(a, "entrega") instanceof VentaComisionEntrega e
                    && e.getId().equals(inv.getArgument(0)));
            return null;
        }).when(abonoRepo).deleteByEntregaId(anyLong());
        devolucionRepo = mock(VentaComisionDevolucionRepository.class);
        when(devolucionRepo.save(any(VentaComisionDevolucion.class))).thenAnswer(inv -> {
            devoluciones.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(devolucionRepo.findByComisionIdOrderByFechaAscIdAsc(anyLong())).thenAnswer(inv -> devoluciones);
        pagos = mock(PagoVentaRepository.class);
        when(pagos.findVigentesByVentaIdOrderByFechaAscIdAsc(anyLong())).thenAnswer(inv -> pagosVigentes);
        ventaLotes = mock(VentaLoteRepository.class);
        VentaLote lote = mock(VentaLote.class);
        when(lote.getPrecio()).thenReturn(new BigDecimal("100000"));
        com.werealestate.backend.model.Lote fisico = mock(com.werealestate.backend.model.Lote.class);
        com.werealestate.backend.model.Desarrollo desarrollo = mock(com.werealestate.backend.model.Desarrollo.class);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");
        when(fisico.getDesarrollo()).thenReturn(desarrollo);
        when(fisico.getManzana()).thenReturn("4");
        when(fisico.getNumeroLote()).thenReturn("12");
        when(lote.getLote()).thenReturn(fisico);
        when(ventaLotes.findByVentaId(anyLong())).thenReturn(List.of(lote));
        gastos = mock(GastoRepository.class);
        when(gastos.save(any(Gasto.class))).thenAnswer(inv -> {
            Gasto g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", siguienteId++);
            gastosGuardados.add(g);
            return g;
        });
        TipoGastoRepository tipos = mock(TipoGastoRepository.class);
        when(tipos.findFirstByNombreIgnoreCase("Comisiones")).thenReturn(Optional.of(new TipoGasto("Comisiones", false)));
        FinanzasService finanzas = mock(FinanzasService.class);
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(admin);
        service = new ComisionService(comisiones, devengoRepo, entregaRepo, abonoRepo, devolucionRepo,
                mock(VentaRepository.class), ventaLotes, pagos, gastos, tipos, finanzas, provider);

        venta = mock(Venta.class);
        when(venta.getId()).thenReturn(7L);
        when(venta.getEtiqueta()).thenReturn("S-7");
        when(venta.getCliente()).thenReturn("Cliente Uno");
        when(venta.getFechaVenta()).thenReturn(MIERCOLES);
        when(venta.getUsuarioAsesor()).thenReturn(mock(Usuario.class));
    }

    private void pago(TipoAbono tipo, String monto, LocalDate fecha) {
        PagoVenta p = mock(PagoVenta.class);
        when(p.getTipo()).thenReturn(tipo);
        when(p.getMonto()).thenReturn(new BigDecimal(monto));
        when(p.getFecha()).thenReturn(fecha);
        pagosVigentes.add(p);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperaba " + esperado + " y fue " + real);
    }

    /** Comisión existente de 5,000 (5 % de 100,000) de la venta, con la regla indicada. */
    private VentaComision existente(boolean reglaNueva, ModalidadComision modalidad) {
        VentaComision c = new VentaComision(venta, "Cliente Uno", venta.getUsuarioAsesor(), null,
                new BigDecimal("100000"), new BigDecimal("5"), new BigDecimal("5000"), modalidad);
        if (reglaNueva) c.marcarReglaNueva();
        ReflectionTestUtils.setField(c, "id", siguienteId++);
        when(comisiones.findById(c.getId())).thenReturn(Optional.of(c));
        when(comisiones.findByVentaIdAndSecundariaFalse(7L)).thenReturn(Optional.of(c));
        return c;
    }

    // ------------------------------------------------------------ regla nueva: por tipo de abono

    @Test
    void ventaNuevaCreaLaComisionDel5PorCientoSobreElValorDeLosLotes() {
        pago(TipoAbono.ENGANCHE, "6000", MIERCOLES);

        service.sincronizarVentaNueva(venta, null, null, null, null);

        assertEquals(1, devengos.size());
        VentaComision c = devengos.get(0).getComision();
        igual("5000", c.getMonto());
        igual("5", c.getPorcentaje());
        assertTrue(c.isReglaNueva());
        assertEquals(ModalidadComision.UNA_EXHIBICION, c.getModalidad()); // el enganche cubrió la comisión
        igual("5000", devengos.get(0).getMonto()); // tope: lo que faltaba
        assertEquals(SABADO, devengos.get(0).getFechaEntrega());
        assertEquals(MIERCOLES, devengos.get(0).getFechaOrigen());
    }

    @Test
    void ventaNuevaConPorcentajeElegidoLoMarcaComoManual() {
        service.sincronizarVentaNueva(venta, new BigDecimal("7.5"), null, null, null);

        ArgumentCaptorHolder.assertMontoManual(comisiones, "7500.00");
    }

    @Test
    void reglaNuevaEngancheCompletoMensualidadYAportacionLaMitadApartadoYSinTipoNada() {
        existente(true, ModalidadComision.PARCIALIDADES);
        pago(TipoAbono.ENGANCHE, "2000", MIERCOLES);
        pago(TipoAbono.APARTADO, "500", MIERCOLES);
        pago(TipoAbono.MENSUALIDAD, "2000", MIERCOLES.plusDays(10));
        pago(null, "900", MIERCOLES.plusDays(11));
        pago(TipoAbono.APORTACION, "4000", MIERCOLES.plusDays(12));
        pago(TipoAbono.A_CUENTA_COMISION, "8000", MIERCOLES.plusDays(13));

        service.sincronizar(venta);

        // 2000 + 1000 + 2000 y la última (4000) solo aporta lo que faltaba: nada, ya se completó.
        assertEquals(3, devengos.size());
        igual("2000", devengos.get(0).getMonto());
        igual("1000", devengos.get(1).getMonto());
        igual("2000", devengos.get(2).getMonto());
        assertEquals(ModalidadComision.PARCIALIDADES, devengos.get(0).getComision().getModalidad());
    }

    @Test
    void reglaNuevaUnAporteParcialCompletaLaComisionYElSiguienteYaNoCuenta() {
        existente(true, ModalidadComision.PARCIALIDADES);
        pago(TipoAbono.ENGANCHE, "4500", MIERCOLES);
        pago(TipoAbono.MENSUALIDAD, "2000", MIERCOLES.plusDays(7));
        pago(TipoAbono.MENSUALIDAD, "2000", MIERCOLES.plusDays(14));

        service.sincronizar(venta);

        assertEquals(2, devengos.size());
        igual("4500", devengos.get(0).getMonto());
        igual("500", devengos.get(1).getMonto());
    }

    @Test
    void reglaNuevaConContratoFirmadoDespuesNoSeEntregaAntesDelSabadoDeLaFirma() {
        existente(true, ModalidadComision.PARCIALIDADES);
        when(venta.isContratoFirmado()).thenReturn(true);
        when(venta.getContratoFechaFirma()).thenReturn(SABADO.plusDays(10)); // jueves 17 nov → sábado 21
        pago(TipoAbono.ENGANCHE, "1000", MIERCOLES);

        service.sincronizar(venta);

        assertEquals(LocalDate.of(2026, 11, 21), devengos.get(0).getFechaEntrega());
        assertEquals(MIERCOLES, devengos.get(0).getFechaOrigen());
    }

    @Test
    void unAbonoEnSabadoSeEntregaElSabadoSiguiente() {
        existente(true, ModalidadComision.PARCIALIDADES);
        pago(TipoAbono.ENGANCHE, "1000", SABADO);

        service.sincronizar(venta);

        assertEquals(SABADO.plusDays(7), devengos.get(0).getFechaEntrega());
        assertEquals(DayOfWeek.SATURDAY, devengos.get(0).getFechaEntrega().getDayOfWeek());
    }

    // ------------------------------------------------------------ regla anterior

    @Test
    void reglaAnteriorUnaExhibicionGeneraLaComisionCompletaEnLaFechaDeLaVenta() {
        existente(false, ModalidadComision.UNA_EXHIBICION);
        // Sin entregas la modalidad se vuelve a calcular: el enganche de 5,000 o más cubre la comisión.
        when(venta.getEnganche()).thenReturn(new BigDecimal("5000"));
        when(venta.getEngancheLabel()).thenReturn("Pago inicial");

        service.sincronizar(venta);

        assertEquals(1, devengos.size());
        assertNull(devengos.get(0).getPagoVenta());
        igual("5000", devengos.get(0).getMonto());
        assertEquals(MIERCOLES, devengos.get(0).getFechaOrigen());
        assertEquals(SABADO, devengos.get(0).getFechaEntrega());
    }

    @Test
    void reglaAnteriorParcialidadesTomaLaMitadDeCadaAbonoHastaCompletar() {
        existente(false, ModalidadComision.PARCIALIDADES);
        pago(TipoAbono.APARTADO, "4000", MIERCOLES); // la regla anterior no distingue tipos
        pago(TipoAbono.MENSUALIDAD, "6001", MIERCOLES.plusDays(7)); // mitad 3000.50 (HALF_UP)
        pago(TipoAbono.MENSUALIDAD, "9000", MIERCOLES.plusDays(14)); // solo falta 0 → ya no cuenta

        service.sincronizar(venta);

        assertEquals(2, devengos.size());
        igual("2000", devengos.get(0).getMonto());
        igual("3000", devengos.get(1).getMonto()); // completó los 5000
    }

    @Test
    void unaComisionCanceladaNoReconstruyeLoGanado() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);
        c.setCancelada(true);
        pago(TipoAbono.ENGANCHE, "1000", MIERCOLES);

        service.sincronizar(venta);

        assertTrue(devengos.isEmpty());
    }

    // ------------------------------------------------------------ editar

    @Test
    void editarExigeSoloUnoDeLosDos() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);

        assertEquals("Indica el porcentaje o el monto (solo uno)", assertThrows(ValidationException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(null, null))).getMessage());
        assertEquals("Indica el porcentaje o el monto (solo uno)", assertThrows(ValidationException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(BigDecimal.ONE, BigDecimal.TEN))).getMessage());
    }

    @Test
    void editarPorMontoCalculaElPorcentajeYRespetaElTope() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);

        service.editar(c.getId(), new ComisionUpdateRequest(null, new BigDecimal("8000")));

        igual("8000", c.getMonto());
        igual("8", c.getPorcentaje());
        assertTrue(c.isMontoManual());
        assertEquals("La comisión no puede pasar de 10% del valor de la venta", assertThrows(ValidationException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(null, new BigDecimal("10001")))).getMessage());
        assertEquals("El monto no puede ser negativo", assertThrows(ValidationException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(null, new BigDecimal("-1")))).getMessage());
    }

    @Test
    void editarPorPorcentajeCalculaElMontoYValidaElRango() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);

        service.editar(c.getId(), new ComisionUpdateRequest(new BigDecimal("3.5"), null));

        igual("3500", c.getMonto());
        assertEquals("El porcentaje debe estar entre 0 y 10", assertThrows(ValidationException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(new BigDecimal("10.5"), null))).getMessage());
    }

    @Test
    void editarNoPuedeDejarLaComisionPorDebajoDeLoYaPagado() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);
        when(venta.isContratoFirmado()).thenReturn(true);
        pago(TipoAbono.ENGANCHE, "5000", MIERCOLES);
        service.sincronizar(venta);
        service.entregar(c.getId(), entrega("3000"));

        assertEquals("La comisión no puede ser menor que lo ya pagado ($3000)", assertThrows(ConflictException.class,
                () -> service.editar(c.getId(), new ComisionUpdateRequest(null, new BigDecimal("2999")))).getMessage());
    }

    // ------------------------------------------------------------ entregar y devolver

    private static ComisionEntregaRequest entrega(String monto) {
        return new ComisionEntregaRequest(new BigDecimal(monto), SABADO, " nota ", true, false, ModalidadPago.EFECTIVO);
    }

    private VentaComision comisionConGanado() {
        VentaComision c = existente(true, ModalidadComision.PARCIALIDADES);
        when(venta.isContratoFirmado()).thenReturn(true);
        pago(TipoAbono.ENGANCHE, "2000", MIERCOLES);
        pago(TipoAbono.MENSUALIDAD, "2000", MIERCOLES.plusDays(7)); // aporta 1000
        service.sincronizar(venta);
        return c;
    }

    @Test
    void entregarCreaElGastoLaEntregaYRepartePorAbonoLoQueCubre() {
        VentaComision c = comisionConGanado();

        ComisionDetalleDto d = service.entregar(c.getId(), entrega("2500"));

        assertEquals(1, gastosGuardados.size());
        Gasto g = gastosGuardados.get(0);
        assertEquals("Comisión · Cliente Uno (venta S-7)", g.getConcepto());
        assertEquals(OrigenGasto.COMISION, g.getOrigen());
        igual("2500", g.getMonto());
        assertEquals(1, entregas.size());
        assertEquals("nota", entregas.get(0).getNotas());
        igual("2500", entregas.get(0).getEntregadoAcumulado());
        igual("5000", entregas.get(0).getComisionMonto());
        assertEquals(2, abonosDeTicket.size());
        assertEquals("Enganche", abonosDeTicket.get(0).getConcepto());
        igual("2000", abonosDeTicket.get(0).getMonto());
        assertEquals("Mensualidad", abonosDeTicket.get(1).getConcepto());
        igual("500", abonosDeTicket.get(1).getMonto());
        igual("2500", d.comision().entregado());
        igual("500", d.comision().porEntregar());
        assertEquals(EstadoComision.PARCIAL, d.comision().estado());
    }

    @Test
    void entregarMasDeLoGanadoSeRechazaConElMaximo() {
        VentaComision c = comisionConGanado();

        assertEquals("El monto máximo a pagar es $3000.00: es la comisión generada pendiente de pago",
                assertThrows(ConflictException.class, () -> service.entregar(c.getId(), entrega("3000.01"))).getMessage());
    }

    @Test
    void entregarConContratoSinFirmarOComisionCanceladaSeRechaza() {
        VentaComision c = comisionConGanado();
        when(venta.isContratoFirmado()).thenReturn(false);
        assertEquals("El contrato de esta venta no está firmado: la comisión queda retenida hasta registrar la firma en la venta",
                assertThrows(ConflictException.class, () -> service.entregar(c.getId(), entrega("100"))).getMessage());

        c.setCancelada(true);
        assertEquals("Esta comisión está cancelada: reactívala para registrar su pago",
                assertThrows(ConflictException.class, () -> service.entregar(c.getId(), entrega("100"))).getMessage());
    }

    @Test
    void devolverNoPuedePasarDeLoEntregadoNetoYLuegoDejaElSaldoDevuelto() {
        VentaComision c = comisionConGanado();
        service.entregar(c.getId(), entrega("3000"));

        ComisionDetalleDto d = service.devolver(c.getId(),
                new ComisionDevolucionRequest(new BigDecimal("1000"), null, null));

        igual("1000", d.comision().devuelto());
        igual("2000", d.comision().entregado());
        assertEquals("El monto máximo a devolver es $2000 (lo pagado al asesor en esta comisión)",
                assertThrows(ConflictException.class, () -> service.devolver(c.getId(),
                        new ComisionDevolucionRequest(new BigDecimal("2000.01"), null, null))).getMessage());
    }

    @Test
    void entregarDespuesDeUnaDevolucionCubreElTramoCorrecto() {
        VentaComision c = comisionConGanado();
        service.entregar(c.getId(), entrega("3000"));
        service.devolver(c.getId(), new ComisionDevolucionRequest(new BigDecimal("1000"), null, null));
        abonosDeTicket.clear();

        // Entregado neto 2000: la nueva entrega de 500 sale del tramo 2000–2500 de lo ganado (enganche 2000, mensualidad 1000).
        service.entregar(c.getId(), entrega("500"));

        assertEquals(1, abonosDeTicket.size());
        assertEquals("Mensualidad", abonosDeTicket.get(0).getConcepto());
        igual("500", abonosDeTicket.get(0).getMonto());
    }

    // ------------------------------------------------------------ gasto ↔ entrega

    @Test
    void corregirElMontoDeLaEntregaAjustaElAcumuladoDeLasPosterioresYSusAbonos() {
        VentaComision c = comisionConGanado();
        service.entregar(c.getId(), entrega("1000"));
        service.entregar(c.getId(), entrega("1500"));
        Long gastoPrimera = gastosGuardados.get(0).getId();

        service.corregirEntregaDeGasto(gastoPrimera, SABADO.plusDays(1), new BigDecimal("1200"));

        VentaComisionEntrega primera = entregas.get(0);
        VentaComisionEntrega segunda = entregas.get(1);
        igual("1200", primera.getMonto());
        assertEquals(SABADO.plusDays(1), primera.getFecha());
        igual("1200", primera.getEntregadoAcumulado()); // 1000 + 200
        igual("2700", segunda.getEntregadoAcumulado()); // 2500 + 200
        List<VentaComisionEntregaAbono> deLaSegunda = abonosDeTicket.stream()
                .filter(a -> ReflectionTestUtils.getField(a, "entrega") == segunda).toList();
        // La segunda ahora cubre el tramo 1200–2700: 800 de enganche y 700 de mensualidad.
        assertEquals(2, deLaSegunda.size());
        igual("800", deLaSegunda.get(0).getMonto());
        igual("700", deLaSegunda.get(1).getMonto());
    }

    @Test
    void corregirEntregaNoPuedePasarDeLoGanado() {
        VentaComision c = comisionConGanado();
        service.entregar(c.getId(), entrega("2000"));

        assertEquals("Solo hay $3000.00 ganados en esta comisión para esta entrega; el cliente no ha pagado más",
                assertThrows(ConflictException.class, () -> service.corregirEntregaDeGasto(
                        gastosGuardados.get(0).getId(), SABADO, new BigDecimal("3000.01"))).getMessage());
    }

    @Test
    void anularEntregaBorraLaEntregaYSuGasto() {
        VentaComision c = comisionConGanado();
        service.entregar(c.getId(), entrega("1000"));
        VentaComisionEntrega e = entregas.get(0);

        service.anularEntrega(c.getId(), e.getId());

        org.mockito.Mockito.verify(entregaRepo).delete(e);
        org.mockito.Mockito.verify(gastos).delete(e.getGasto());
    }

    /** Pequeño ayudante para verificar el guardado de la comisión nueva. */
    private static final class ArgumentCaptorHolder {
        static void assertMontoManual(VentaComisionRepository repo, String monto) {
            var captor = org.mockito.ArgumentCaptor.forClass(VentaComision.class);
            org.mockito.Mockito.verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            VentaComision c = captor.getAllValues().get(0);
            igual(monto, c.getMonto());
            assertTrue(c.isMontoManual());
        }
    }
}
