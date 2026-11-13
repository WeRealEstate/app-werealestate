package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.dto.ClienteDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaReestructurarRequest;
import com.werealestate.backend.dto.VentaUpdateRequest;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.service.ClienteService;
import com.werealestate.backend.service.EstadoCuentaService;
import com.werealestate.backend.service.FinanzasService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La venta S-16 en la BD: se reestructuró completa cuando su fecha de venta era junio y después se corrigió a julio.
 * El plan no debe arrancar antes del mes de la venta y todos los módulos (tabla de amortización, Finanzas, Clientes)
 * deben decir lo mismo. También, que editar la fecha de venta mueva el inicio del plan (salvo que haya pagos marcados en
 * meses anteriores). Contra la BD real dentro de una transacción que se revierte.
 */
class InicioPlanVentaIntegracionTest extends PruebaDeIntegracion {

    private static final LocalDate JUNIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate JULIO = LocalDate.of(2026, 7, 1);

    @Autowired EntityManager em;
    @Autowired VentaRepository ventas;
    @Autowired EstadoCuentaService estadoCuenta;
    @Autowired FinanzasService finanzas;
    @Autowired ClienteService clienteService;

    private Usuario admin;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
    }

    /** Venta de junio: precio 300,000, enganche 60,000 y 60 mensualidades de 4,000 el día 1. */
    private VentaDto ventaDeJunio() {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        return crearVenta(admin, lote, "300000", "60000", "4000", 60, JUNIO);
    }

    private void reestructurarCompleta(VentaDto v) {
        reestructuracionVentaService.reestructurar(v.id(),
                new VentaReestructurarRequest(CONTRASENA, "cambio", 60, 1, false, null, null, null));
    }

    private void pagarMensualidadDeJunio(VentaDto v) {
        reciboVentaService.registrarPago(v.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 6, 5), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.MENSUALIDAD, new BigDecimal("4000"),
                        List.of(new PagoVentaCreateRequest.Mes(2026, 6)), null, null))));
    }

    private void cambiarFechaDeVenta(VentaDto v, LocalDate nueva) {
        Venta e = ventas.findById(v.id()).orElseThrow();
        ventaService.actualizar(v.id(), new VentaUpdateRequest(
                null, null, null, e.getUsuarioAsesor().getId(), null, e.getFormaPago(), nueva, e.getMensualidad(),
                e.getPlazoMeses(), e.getEngancheLabel(), e.getEnganche(), e.getDiaPago(), e.isPrimeraMensualidadMesVenta(),
                e.getNotas(), null, null));
        em.flush();
    }

    /** Deja la venta como estaba S-16: el inicio del plan guardado en junio con la venta ya en julio. */
    private void dejarComoS16(VentaDto v) {
        cambiarFechaDeVenta(v, JULIO);
        em.createNativeQuery("update venta set inicio_plan = :f where id = :id")
                .setParameter("f", JUNIO).setParameter("id", v.id()).executeUpdate();
        em.clear();
        assertEquals(JUNIO, ventas.findById(v.id()).orElseThrow().getInicioPlan());
    }

    private static AmortizacionDto.Fila mensualidad(AmortizacionDto a, int numero) {
        return a.filas().stream().filter(f -> f.numero() != null && f.numero() == numero).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- editar la fecha de venta

    @Test
    void alEditarLaFechaDeVentaElInicioDelPlanReestructuradoLaSigue() {
        VentaDto v = ventaDeJunio();
        reestructurarCompleta(v);
        assertEquals(JUNIO, ventas.findById(v.id()).orElseThrow().getInicioPlan());

        cambiarFechaDeVenta(v, JULIO);

        assertEquals(JULIO, ventas.findById(v.id()).orElseThrow().getInicioPlan());
        em.clear();
        assertEquals(JULIO, mensualidad(estadoCuenta.deVenta(v.id()), 1).fecha());
    }

    @Test
    void conUnPagoMarcadoEnJunioEditarLaFechaNoMueveElInicioDelPlan() {
        VentaDto v = ventaDeJunio();
        pagarMensualidadDeJunio(v);
        reestructurarCompleta(v);

        cambiarFechaDeVenta(v, JULIO);

        assertEquals(JUNIO, ventas.findById(v.id()).orElseThrow().getInicioPlan());
        em.clear();
        assertEquals(JUNIO, mensualidad(estadoCuenta.deVenta(v.id()), 1).fecha());
    }

    @Test
    void sinReestructurarEditarLaFechaMueveElPlanComoSiempre() {
        VentaDto v = ventaDeJunio();
        cambiarFechaDeVenta(v, JULIO);
        assertNotNull(v);
        em.clear();
        assertEquals(null, ventas.findById(v.id()).orElseThrow().getInicioPlan());
        assertEquals(JULIO, mensualidad(estadoCuenta.deVenta(v.id()), 1).fecha());
    }

    // ---------------------------------------------------------------- S-16 ya guardada así

    @Test
    void laVentaDeS16ArrancaEnJulioConSesentaMensualidades() {
        VentaDto v = ventaDeJunio();
        reestructurarCompleta(v);
        dejarComoS16(v);

        AmortizacionDto a = estadoCuenta.deVenta(v.id());

        assertEquals(JULIO, mensualidad(a, 1).fecha());
        assertEquals(LocalDate.of(2031, 6, 1), mensualidad(a, 60).fecha());
        assertEquals(60, a.filas().stream().filter(f -> f.numero() != null).count());
        assertEquals("2026-07", a.primerMesPlan());
        // Nada se espera en junio: ni en la tabla mes con mes ni en el calendario de ingresos de Finanzas.
        assertTrue(a.detalleMensual().stream().noneMatch(f -> f.fecha() != null && f.fecha().isBefore(JULIO)));
        em.clear();
        assertTrue(finanzas.ingresosDelMes("2026-06", null).esperados().stream().noneMatch(c -> v.id().equals(c.ventaId())));
        assertFalse(finanzas.ingresosDelMes("2026-07", null).esperados().stream().noneMatch(c -> v.id().equals(c.ventaId())));
    }

    @Test
    void conUnPagoMarcadoEnJunioLaVentaDeS16SeQuedaEnJunio() {
        VentaDto v = ventaDeJunio();
        pagarMensualidadDeJunio(v);
        reestructurarCompleta(v);
        dejarComoS16(v);

        assertEquals(JUNIO, mensualidad(estadoCuenta.deVenta(v.id()), 1).fecha());
    }

    @Test
    void tablaFinanzasYClientesDicenLoMismoDeLaVentaDeS16() {
        VentaDto v = ventaDeJunio();
        reestructurarCompleta(v);
        dejarComoS16(v);

        em.clear();
        AmortizacionDto tabla = estadoCuenta.deVenta(v.id());
        em.clear();
        FinanzasService.CobroVenta solo = finanzas.cobroDeVenta(ventas.findById(v.id()).orElseThrow());
        em.clear();
        FinanzasService.CobroVenta enBloque = finanzas.cobroPorVenta().get(v.id());
        em.clear();
        Long clienteId = ventas.findById(v.id()).orElseThrow().getClienteRef().getId();
        ClienteDto.ClienteVentaDto cliente = clienteService.obtener(clienteId).ventas().stream()
                .filter(c -> c.ventaId().equals(v.id())).findFirst().orElseThrow();

        assertTrue(tabla.atrasoMonto().signum() > 0, "sin abonos y con meses vencidos hay atraso");
        for (BigDecimal atraso : List.of(solo.atrasoMonto(), enBloque.atrasoMonto(), cliente.atrasoMonto())) {
            assertEquals(0, tabla.atrasoMonto().compareTo(atraso), "atraso " + atraso + " vs tabla " + tabla.atrasoMonto());
        }
        assertEquals(tabla.pagosAtrasados(), solo.pagosAtrasados());
        assertEquals(tabla.pagosAtrasados(), enBloque.pagosAtrasados());
        assertEquals(tabla.pagosAtrasados(), cliente.pagosAtrasados());
        assertEquals(tabla.atrasadoDesde(), cliente.atrasadoDesde());
        assertEquals(tabla.proximaFecha(), cliente.proximaFecha());
        // El primer mes atrasado es julio, no junio.
        assertEquals(JULIO, tabla.atrasadoDesde());
    }
}
