package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.ReciboAnularRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaPausaRequest;
import com.werealestate.backend.dto.VentaReestructurarRequest;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.service.ComisionService;
import com.werealestate.backend.service.FinanzasService;
import com.werealestate.backend.service.PausaMensualidadService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Caracterización de las cargas en bloque de Finanzas y Comisiones: lo que calcula {@link FinanzasService#cobroPorVenta}
 * para toda la cartera debe ser exactamente lo que da {@link FinanzasService#cobroDeVenta} venta por venta, y cada
 * comisión del listado debe salir igual que en su detalle (que se arma solo con lo de esa comisión). Ventas con casos
 * borde: mensualidades con meses, recibo anulado, pausa de mensualidades, reestructuración, contado y comisión
 * cancelada. Además fija cuántas consultas hacen las cargas en bloque (no una por cada pago o venta).
 *
 * Contra la BD real dentro de una transacción que se revierte; antes de cada cálculo se vacía la sesión de JPA para que
 * las colecciones perezosas se carguen como en una petición real.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FinanzasComisionesCargaIntegracionTest extends PruebaDeIntegracion {

    @Autowired EntityManager em;
    @Autowired FinanzasService finanzas;
    @Autowired ComisionService comisiones;
    @Autowired PausaMensualidadService pausas;
    @Autowired VentaRepository ventas;
    @Autowired VentaComisionRepository comisionRepository;

    private VentaDto conPausaYAnulado;
    private VentaDto reestructurada;
    private VentaDto contado;
    private VentaDto conComisionCancelada;

    @BeforeEach
    void preparar() {
        Usuario admin = usuario(Role.ADMIN);
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(admin);

        conPausaYAnulado = nuevaVenta(asesor, "120000", "20000", "5000", 20);
        abonar(conPausaYAnulado, LocalDate.of(2026, 1, 15), TipoAbono.ENGANCHE, "20000", null);
        abonar(conPausaYAnulado, LocalDate.of(2026, 2, 10), TipoAbono.MENSUALIDAD, "10000",
                List.of(new PagoVentaCreateRequest.Mes(2026, 1), new PagoVentaCreateRequest.Mes(2026, 2)));
        List<PagoVentaDto> anulado = abonar(conPausaYAnulado, LocalDate.of(2026, 3, 15), TipoAbono.MENSUALIDAD, "5000",
                List.of(new PagoVentaCreateRequest.Mes(2026, 3)));
        reciboVentaService.anularRecibo(conPausaYAnulado.id(), anulado.get(0).reciboId(), new ReciboAnularRequest("prueba"));
        abonar(conPausaYAnulado, LocalDate.of(2026, 4, 20), TipoAbono.MENSUALIDAD, "2500",
                List.of(new PagoVentaCreateRequest.Mes(2026, 3)));
        pausas.pausar(conPausaYAnulado.id(), new VentaPausaRequest(CONTRASENA, "pausa de prueba", 6, LocalDate.of(2026, 9, 15)));

        reestructurada = nuevaVenta(asesor, "90000", "10000", "4000", 20);
        abonar(reestructurada, LocalDate.of(2026, 1, 15), TipoAbono.ENGANCHE, "10000", null);
        abonar(reestructurada, LocalDate.of(2026, 3, 1), TipoAbono.APARTADO, "3000", null);
        reestructuracionVentaService.reestructurar(reestructurada.id(),
                new VentaReestructurarRequest(CONTRASENA, "cambio", 12, 20, false, null, null, null));

        contado = nuevaVenta(asesor, "60000", null, null, null);
        abonar(contado, LocalDate.of(2026, 1, 20), TipoAbono.APARTADO, "15000", null);

        conComisionCancelada = nuevaVenta(asesor, "80000", "8000", "3000", 24);
        abonar(conComisionCancelada, LocalDate.of(2026, 1, 15), TipoAbono.ENGANCHE, "8000", null);
        comisionRepository.findAllByVentaId(conComisionCancelada.id()).forEach(c -> comisiones.cancelar(c.getId()));

        em.flush();
    }

    private VentaDto nuevaVenta(Usuario asesor, String precio, String enganche, String mensualidad, Integer plazo) {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        return crearVenta(asesor, lote, precio, enganche, mensualidad, plazo, LocalDate.of(2026, 1, 15));
    }

    private List<PagoVentaDto> abonar(VentaDto venta, LocalDate fecha, TipoAbono tipo, String monto, List<PagoVentaCreateRequest.Mes> meses) {
        return reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(fecha, "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(tipo, new BigDecimal(monto), meses, null, null))));
    }

    private List<VentaDto> casos() {
        return List.of(conPausaYAnulado, reestructurada, contado, conComisionCancelada);
    }

    @Test
    void elCobroDeTodaLaCarteraEsElMismoQueVentaPorVenta() {
        em.clear();
        Map<Long, FinanzasService.CobroVenta> enBloque = finanzas.cobroPorVenta();
        for (VentaDto v : casos()) {
            em.clear();
            FinanzasService.CobroVenta solo = finanzas.cobroDeVenta(ventas.findById(v.id()).orElseThrow());
            FinanzasService.CobroVenta bloque = enBloque.get(v.id());
            assertNotNull(bloque, "falta la venta " + v.id());
            assertMismoCobro(solo, bloque);
        }
        // contado: todo el precio venció el día de la venta y solo hay $15,000 abonados
        assertDinero("45000", enBloque.get(contado.id()).atrasoMonto());
        assertDinero("15000", enBloque.get(contado.id()).abonado());
        // el recibo anulado no cuenta: 20,000 + 10,000 + 2,500
        assertDinero("32500", enBloque.get(conPausaYAnulado.id()).abonado());
    }

    @Test
    void cadaComisionDelListadoSaleIgualQueEnSuDetalle() {
        em.clear();
        List<ComisionDto> listado = comisiones.listar();
        int revisadas = 0;
        for (VentaDto v : casos()) {
            for (VentaComision c : comisionRepository.findAllByVentaId(v.id())) {
                ComisionDto enListado = listado.stream().filter(d -> d.id().equals(c.getId())).findFirst().orElseThrow();
                em.clear();
                ComisionDto enDetalle = comisiones.obtener(c.getId()).comision();
                assertEquals(enDetalle, enListado, "comisión " + c.getId());
                revisadas++;
            }
        }
        assertTrue(revisadas >= casos().size() - 1, "cada venta activa tiene su comisión");
        assertTrue(listado.stream().noneMatch(d -> conComisionCancelada.id().equals(d.ventaId())),
                "la comisión cancelada fue eliminada permanentemente");
        assertTrue(ventas.findById(conComisionCancelada.id()).orElseThrow().isSinComision(),
                "la venta queda marcada sin comisión para no regenerarla");
        assertFalse(listado.isEmpty());
    }

    @Test
    void loCobradoDeValorVendidoEsLaSumaDeLosAbonosVigentes() {
        em.clear();
        BigDecimal sumaDeLineas = pagosVigentes().stream().map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cobrado = finanzas.valorVendido().cobrado();
        assertEquals(sumaDeLineas, cobrado, "misma cifra y misma escala");
    }

    /** Las cargas en bloque no hacen una consulta por cada bloque de pagos o ventas (antes: 35 y 41 con ~2,500 pagos). */
    @Test
    void lasCargasEnBloqueUsanPocasConsultas() {
        Statistics st = estadisticas();
        em.clear();
        st.clear();
        finanzas.cobroPorVenta();
        long cobro = st.getPrepareStatementCount();
        em.clear();
        st.clear();
        comisiones.listar();
        long listado = st.getPrepareStatementCount();
        assertTrue(cobro <= 8, "cobroPorVenta hizo " + cobro + " consultas");
        assertTrue(listado <= 16, "listar comisiones hizo " + listado + " consultas");
    }

    private List<PagoVenta> pagosVigentes() {
        return em.createQuery("select p from PagoVenta p join fetch p.recibo r where r.anulado = false", PagoVenta.class)
                .getResultList();
    }

    private static void assertMismoCobro(FinanzasService.CobroVenta esperado, FinanzasService.CobroVenta real) {
        assertDinero(esperado.atrasoMonto().toPlainString(), real.atrasoMonto());
        assertEquals(esperado.mensualidadesAtrasadas(), real.mensualidadesAtrasadas());
        assertEquals(esperado.pagosAtrasados(), real.pagosAtrasados());
        assertDinero(esperado.esperadoMes().toPlainString(), real.esperadoMes());
        assertDinero(esperado.recibidoMes().toPlainString(), real.recibidoMes());
        assertEquals(esperado.fechaMes(), real.fechaMes());
        assertDinero(esperado.abonado().toPlainString(), real.abonado());
    }

    private static void assertDinero(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    Statistics estadisticas() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }
}
