package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaReestructurarRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VentaReestructuracion;
import com.werealestate.backend.repository.VentaReestructuracionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Caracterización de {@link com.werealestate.backend.service.VentaService#reestructurar}: cómo se rehace el plan
 * (enganche, aportaciones, mensualidad nueva, etiqueta de forma de pago), sus validaciones y sus mensajes de error.
 * Contra la BD real dentro de una transacción que se revierte.
 *
 * Venta base: precio $120,000, enganche $20,000, mensualidad $5,000 x 20 meses desde enero de 2026 (día 15). */
class VentaReestructurarIntegracionTest extends PruebaDeIntegracion {

    @Autowired VentaReestructuracionRepository reestructuraciones;

    private Usuario admin;
    private VentaDto venta;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        venta = nuevaVenta("120000", "20000", "5000", 20);
    }

    private VentaDto nuevaVenta(String precio, String enganche, String mensualidad, Integer plazo) {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        return crearVenta(admin, lote, precio, enganche, mensualidad, plazo, LocalDate.of(2026, 1, 15));
    }

    private void abonar(VentaDto de, TipoAbono tipo, String monto) {
        reciboVentaService.registrarPago(de.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(tipo, new BigDecimal(monto), null, null, null))));
    }

    private static VentaReestructurarRequest.Aportacion aportacion(int anio, int mes, String monto) {
        return new VentaReestructurarRequest.Aportacion(anio, mes, new BigDecimal(monto));
    }

    private VentaReestructurarRequest pedido(int plazo, int dia, List<VentaReestructurarRequest.Aportacion> aportaciones) {
        return new VentaReestructurarRequest(CONTRASENA, "  cambio de plan  ", plazo, dia, false, aportaciones, null, null);
    }

    private VentaReestructurarRequest pedidoConEnganche(int plazo, String etiqueta, String enganche) {
        return new VentaReestructurarRequest(CONTRASENA, "motivo", plazo, 10, false, null, etiqueta, enganche == null ? null : new BigDecimal(enganche));
    }

    private static void assertMensaje(String esperado, Throwable e) {
        assertEquals(esperado, e.getMessage());
    }

    private static void assertDinero(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void soloAdminOAdministracionConSuContrasenaPuedenReestructurar() {
        iniciarSesionComo(usuario(Role.ASESOR));
        assertMensaje("Solo un administrador o personal de administración puede reestructurar una venta",
                assertThrows(ForbiddenOperationException.class, () -> reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, null))));

        iniciarSesionComo(admin);
        var mala = new VentaReestructurarRequest("mala", "m", 10, 10, false, null, null, null);
        assertMensaje("Contraseña incorrecta",
                assertThrows(ForbiddenOperationException.class, () -> reestructuracionVentaService.reestructurar(venta.id(), mala)));

        iniciarSesionComo(usuario(Role.LIDER_AREA));
        assertEquals(10, reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, null)).plazoMeses());
    }

    @Test
    void ventaInexistenteDaNoEncontrado() {
        assertMensaje("Venta no encontrada",
                assertThrows(ResourceNotFoundException.class, () -> reestructuracionVentaService.reestructurar(-1L, pedido(10, 10, null))));
    }

    // ---------------------------------------------------------------- plan nuevo sin aportaciones

    @Test
    void conservaElEngancheOriginalYRepartePrecioMenosEngancheEnMensualidadesIguales() {
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 20, null));

        // 120,000 - 20,000 = 100,000 entre 10 meses
        assertDinero("10000", r.mensualidad());
        assertEquals(10, r.plazoMeses());
        assertEquals(20, r.diaPago());
        assertEquals("Reestructurada · con enganche · 10 meses", r.formaPago());
        assertDinero("20000", r.enganche());
        assertDinero("120000", r.precioVenta());
        assertTrue(r.aportaciones().isEmpty());
    }

    @Test
    void laMensualidadSeRedondeaHaciaAbajoALosCentavos() {
        // 100,000 / 3 = 33333.33 (los centavos que sobran los absorbe la última mensualidad)
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(3, 10, null));
        assertDinero("33333.33", r.mensualidad());
    }

    @Test
    void guardaLaReestructuracionConElCorteElMotivoRecortadoYElResumenDelPlanAnterior() {
        abonar(venta, TipoAbono.ENGANCHE, "20000");
        abonar(venta, TipoAbono.APARTADO, "3000");

        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 20, null));

        List<VentaReestructuracion> guardadas = reestructuraciones.findByVentaIdOrderByIdAsc(venta.id());
        assertEquals(1, guardadas.size());
        VentaReestructuracion g = guardadas.get(0);
        assertEquals(LocalDate.now(), g.getFechaCorte());
        assertDinero("23000", g.getAbonadoAlCorte());
        // el "saldo al corte" guarda lo que se reparte (precio menos enganche), no precio menos abonado
        assertDinero("100000", g.getSaldoAlCorte());
        assertEquals("cambio de plan", g.getMotivo());
        assertEquals(admin.getId(), g.getUsuario().getId());
        assertTrue(g.isCompleta());
        // el plan anterior: 20 meses de $5,000 desde enero de 2026
        assertEquals("Esquema de prueba", g.getAntFormaPago());
        assertDinero("5000", g.getAntMensualidad());
        assertEquals(20, g.getAntPlazoMeses());
        assertEquals(15, g.getAntDiaPago());
        assertDinero("20000", g.getAntEnganche());
        assertTrue(g.getResumenAnterior().startsWith("Esquema de prueba: 20 meses desde 2026-01, mensualidad "), g.getResumenAnterior());
        assertTrue(g.getResumenNuevo().startsWith("Reestructurada · con enganche · 10 meses: 10 meses desde 2026-01, mensualidad "), g.getResumenNuevo());
    }

    @Test
    void despuesDeReestructurarLosMesesCobrablesSiguenSiendoLosDelRangoDeTodosLosPlanes() {
        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 20, null));
        BigDecimal uno = new BigDecimal("1000");
        // el plan original llegaba a 2027-08 y el nuevo arranca en el mismo primer mes (2026-01): el rango suma los dos
        var fuera = assertThrows(ValidationException.class, () -> reciboVentaService.registrarPago(venta.id(),
                new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null, List.of(new PagoVentaCreateRequest.Linea(
                        TipoAbono.MENSUALIDAD, uno, List.of(new PagoVentaCreateRequest.Mes(2025, 12)), null, null)))));
        assertMensaje("El mes 2025-12 está fuera del plazo de la venta (2026-01 a 2027-08)", fuera);
    }

    @Test
    void sePuedeReestructurarVariasVecesYCadaUnaQuedaRegistradaRepartiendoSiempreElPrecioMenosElEngancheOriginal() {
        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 20, null));
        VentaDto segunda = reestructuracionVentaService.reestructurar(venta.id(), pedido(5, 5, null));

        assertEquals(2, reestructuraciones.findByVentaIdOrderByIdAsc(venta.id()).size());
        assertDinero("20000", segunda.mensualidad()); // 100,000 / 5
        assertEquals(5, segunda.plazoMeses());
        assertEquals("Reestructurada · con enganche · 5 meses", segunda.formaPago());
    }

    // ---------------------------------------------------------------- enganche nuevo en una venta que no tenía

    @Test
    void ventaSinEngancheConEngancheNuevoLoAgregaYReparteElRestoConLaEtiquetaPorDefecto() {
        VentaDto sin = nuevaVenta("100000", null, "5000", 20);
        VentaDto r = reestructuracionVentaService.reestructurar(sin.id(), pedidoConEnganche(10, null, "30000"));

        assertDinero("30000", r.enganche());
        assertEquals("Enganche", r.engancheLabel());
        assertDinero("7000", r.mensualidad()); // (100,000 - 30,000) / 10
        assertEquals("Reestructurada · con enganche · 10 meses", r.formaPago());
    }

    @Test
    void ventaSinEngancheYSinEngancheNuevoReparteTodoElPrecio() {
        VentaDto sin = nuevaVenta("100000", null, "5000", 20);
        VentaDto r = reestructuracionVentaService.reestructurar(sin.id(), pedido(10, 10, null));

        assertDinero("10000", r.mensualidad());
        assertEquals("Reestructurada · 10 meses", r.formaPago());
        assertTrue(r.enganche() == null);
    }

    @Test
    void ventaSinEngancheConEngancheCeroONegativoLoIgnora() {
        VentaDto sin = nuevaVenta("100000", null, "5000", 20);
        // el DTO exige >= 0.01 al validar por HTTP, pero el servicio también lo ignora si no es positivo
        VentaDto r = reestructuracionVentaService.reestructurar(sin.id(), pedidoConEnganche(10, "Enganche", "0"));
        assertDinero("10000", r.mensualidad());
    }

    @Test
    void laEtiquetaDelEngancheNuevoDebeSerEngancheOPagoInicial() {
        VentaDto sin = nuevaVenta("100000", null, "5000", 20);
        assertMensaje("El primer pago debe ser «Enganche» o «Pago inicial»", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(sin.id(), pedidoConEnganche(10, "Anticipo", "10000"))));
        VentaDto r = reestructuracionVentaService.reestructurar(sin.id(), pedidoConEnganche(10, "  Pago inicial ", "10000"));
        assertEquals("Pago inicial", r.engancheLabel());
    }

    @Test
    void elEngancheNuevoNoPuedeSerIgualOMayorAlPrecio() {
        VentaDto sin = nuevaVenta("100000", null, "5000", 20);
        assertMensaje("El enganche no puede ser igual o mayor al precio", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(sin.id(), pedidoConEnganche(10, "Enganche", "100000"))));
    }

    @Test
    void siLaVentaYaTeniaEngancheElDelPedidoSeIgnora() {
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedidoConEnganche(10, "Anticipo inválido", "99999"));
        assertDinero("20000", r.enganche());
        assertEquals("Enganche", r.engancheLabel());
        assertDinero("10000", r.mensualidad());
    }

    @Test
    void siElEngancheCubreTodoElPrecioNoHayMensualidadesQueReestructurar() {
        // OJO: comportamiento actual: el enganche original puede dejar solo $0.01 por repartir y entonces se rechaza.
        VentaDto casiTodo = nuevaVenta("100000", "99999.99", "5000", 20);
        assertMensaje("El enganche cubre todo el precio: no hay mensualidades que reestructurar",
                assertThrows(ConflictException.class, () -> reestructuracionVentaService.reestructurar(casiTodo.id(), pedido(10, 10, null))));
    }

    // ---------------------------------------------------------------- aportaciones

    @Test
    void conAportacionesLosMesesConAportacionPaganEsaAportacionYElRestoSeReparteIgual() {
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 6, "30000"))));

        // (100,000 - 30,000) / 9 meses regulares = 7777.77 (hacia abajo)
        assertDinero("7777.77", r.mensualidad());
        // OJO: comportamiento actual, posible bug: con enganche y aportaciones la etiqueta pasa de 50 caracteres y se
        // corta a media palabra ("… + aportac").
        assertEquals("Reestructurada · con enganche · 10 meses + aportac", r.formaPago());
        assertEquals(1, r.aportaciones().size());
        assertEquals(2026, r.aportaciones().get(0).anio());
        assertEquals(6, r.aportaciones().get(0).mes());
        assertDinero("30000", r.aportaciones().get(0).monto());
    }

    @Test
    void elResumenDelPlanNuevoNombraLasAportacionesEnSingularOPlural() {
        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 6, "30000"))));
        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 5, "10000"), aportacion(2026, 7, "10000"))));

        List<VentaReestructuracion> guardadas = reestructuraciones.findByVentaIdOrderByIdAsc(venta.id());
        assertTrue(guardadas.get(0).getResumenNuevo().contains(" + 1 aportación por "), guardadas.get(0).getResumenNuevo());
        assertTrue(guardadas.get(1).getResumenNuevo().contains(" + 2 aportaciones por "), guardadas.get(1).getResumenNuevo());
        // el resumen anterior de la segunda es el plan que dejó la primera, con su aportación
        assertTrue(guardadas.get(1).getResumenAnterior().contains(" + 1 aportación por "), guardadas.get(1).getResumenAnterior());
    }

    @Test
    void laVentaSoloMuestraLasAportacionesDelPlanVigente() {
        reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 6, "30000"))));
        VentaDto segunda = reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 8, "10000"))));

        assertEquals(1, segunda.aportaciones().size());
        assertEquals(8, segunda.aportaciones().get(0).mes());
    }

    @Test
    void aportacionFueraDelPlazoNuevoORepetidaSeRechaza() {
        assertMensaje("La aportación de 2030-01 está fuera del plazo nuevo (2026-01 a 2026-10)", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2030, 1, "1000"))))));
        assertMensaje("La aportación de 2025-12 está fuera del plazo nuevo (2026-01 a 2026-10)", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2025, 12, "1000"))))));
        assertMensaje("Hay dos aportaciones en 2026-03", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, List.of(aportacion(2026, 3, "1000"), aportacion(2026, 3, "2000"))))));
    }

    @Test
    void aportacionesQueCubrenTodoLoQueHayQueRepartirSeRechazan() {
        assertMensaje("Las aportaciones cubren todo lo que hay que repartir ($100000): reduce alguna o el plazo",
                assertThrows(ValidationException.class, () -> reestructuracionVentaService.reestructurar(venta.id(),
                        pedido(10, 10, List.of(aportacion(2026, 3, "100000"))))));
        assertTrue(assertThrows(ValidationException.class, () -> reestructuracionVentaService.reestructurar(venta.id(),
                pedido(10, 10, List.of(aportacion(2026, 3, "150000"))))).getMessage().startsWith("Las aportaciones cubren todo lo que hay que repartir ("));
    }

    @Test
    void todosLosMesesAportacionesDebenSumarExactamenteLoQueSeReparte() {
        // plazo 2 con dos aportaciones: deben sumar 100,000
        assertMensaje("Todos los meses son aportaciones: deben sumar $100000", assertThrows(ValidationException.class,
                () -> reestructuracionVentaService.reestructurar(venta.id(), pedido(2, 10, List.of(aportacion(2026, 1, "40000"), aportacion(2026, 2, "50000"))))));

        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(2, 10, List.of(aportacion(2026, 1, "40000"), aportacion(2026, 2, "60000"))));
        assertDinero("0", r.mensualidad());
        assertEquals(2, r.aportaciones().size());
    }

    @Test
    void laFormaDePagoSeRecortaACincuentaCaracteres() {
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(600, 10, List.of(aportacion(2026, 1, "1000"))));
        // "Reestructurada · con enganche · 600 meses + aportaciones" mide más de 50
        assertEquals(50, r.formaPago().length());
        assertEquals("Reestructurada · con enganche · 600 meses + aporta", r.formaPago());
    }

    // ---------------------------------------------------------------- saldo y depósito

    @Test
    void ventaLiquidadaNoSeReestructura() {
        VentaDto contado = nuevaVenta("50000", null, null, null);
        abonar(contado, TipoAbono.APARTADO, "50000");
        assertMensaje("La venta ya está liquidada: no hay saldo que reestructurar",
                assertThrows(ConflictException.class, () -> reestructuracionVentaService.reestructurar(contado.id(), pedido(10, 10, null))));
    }

    @Test
    void conUnCentavoDeSaldoYaSeConsideraLiquidada() {
        VentaDto casi = nuevaVenta("50000", null, "5000", 10);
        abonar(casi, TipoAbono.APARTADO, "49999.99");
        assertMensaje("La venta ya está liquidada: no hay saldo que reestructurar",
                assertThrows(ConflictException.class, () -> reestructuracionVentaService.reestructurar(casi.id(), pedido(10, 10, null))));
    }

    @Test
    void conAbonosElPlanNuevoSigueRepartiendoElPrecioCompletoMenosElEnganche() {
        abonar(venta, TipoAbono.ENGANCHE, "20000");
        reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.MENSUALIDAD, new BigDecimal("5000"),
                        List.of(new PagoVentaCreateRequest.Mes(2026, 1)), null, null))));
        VentaDto r = reestructuracionVentaService.reestructurar(venta.id(), pedido(10, 10, null));

        // se rehace TODO el plan (decisión del usuario): lo abonado no baja la mensualidad nueva
        assertDinero("10000", r.mensualidad());
        assertDinero("25000", r.totalAbonado());
    }
}
