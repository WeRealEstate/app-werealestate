package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.ReciboAnularRequest;
import com.werealestate.backend.dto.ReciboEditarRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AccionRecibo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.ReciboVentaHistorial;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ReciboVentaHistorialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Caracterización de {@link com.werealestate.backend.service.VentaService#editarRecibo}: correcciones de folio, nota,
 * fecha, tipo, meses, aportación, extemporáneo y monto, sus validaciones y los mensajes de error, y la bitácora que
 * deja. Contra la BD real dentro de una transacción que se revierte.
 *
 * Venta base: precio $120,000, enganche $20,000, mensualidad $5,000 x 20 meses, desde enero de 2026 (el plan cubre de
 * 2026-01 a 2027-08). */
class VentaEditarReciboIntegracionTest extends PruebaDeIntegracion {

    @Autowired ReciboVentaHistorialRepository historial;

    private Usuario admin;
    private VentaDto venta;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        venta = crearVenta(admin, lote, "120000", "20000", "5000", 20, LocalDate.of(2026, 1, 15));
    }

    // ---------------------------------------------------------------- ayudas

    private static PagoVentaCreateRequest.Mes mes(int anio, int mes) {
        return new PagoVentaCreateRequest.Mes(anio, mes);
    }

    private PagoVentaDto pagar(TipoAbono tipo, String monto, List<PagoVentaCreateRequest.Mes> meses, LocalDate fecha) {
        return reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(fecha, "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(tipo, new BigDecimal(monto), meses, null, null)))).get(0);
    }

    private PagoVentaDto pagarMensualidad(String monto, int anio, int mes) {
        return pagar(TipoAbono.MENSUALIDAD, monto, List.of(mes(anio, mes)), LocalDate.of(2026, 2, 1));
    }

    private ReciboEditarRequest.Linea linea(PagoVentaDto pago, TipoAbono tipo, List<PagoVentaCreateRequest.Mes> meses,
            Boolean extemporaneo, String monto) {
        return new ReciboEditarRequest.Linea(pago.id(), tipo, meses, null, extemporaneo, monto == null ? null : new BigDecimal(monto));
    }

    private ReciboEditarRequest edicion(PagoVentaDto pago, String folio, LocalDate fecha, ReciboEditarRequest.Linea... lineas) {
        return new ReciboEditarRequest(folio, fecha, pago.notas(), List.of(lineas), null, null);
    }

    private ReciboEditarRequest edicionDeMonto(PagoVentaDto pago, String monto, String password, String motivo) {
        return new ReciboEditarRequest(pago.folio(), null, pago.notas(),
                List.of(linea(pago, pago.tipo(), pago.meses().stream().map(m -> mes(m.anio(), m.mes())).toList(), false, monto)),
                password, motivo);
    }

    private List<PagoVentaCreateRequest.Mes> mesesDe(PagoVentaDto pago) {
        return pago.meses().stream().map(m -> mes(m.anio(), m.mes())).toList();
    }

    private String ultimoDetalleDeBitacora(PagoVentaDto pago, AccionRecibo accion) {
        List<ReciboVentaHistorial> filas = historial.findByReciboId(pago.reciboId()).stream()
                .filter(h -> h.getAccion() == accion).toList();
        assertFalse(filas.isEmpty(), "falta la fila " + accion + " de la bitácora");
        return filas.get(filas.size() - 1).getDetalle();
    }

    private BigDecimal abonado() {
        return ventaService.obtener(venta.id()).totalAbonado();
    }

    private static void assertMensaje(String esperado, Throwable e) {
        assertEquals(esperado, e.getMessage());
    }

    // ---------------------------------------------------------------- permisos y existencia

    @Test
    void soloAdminOAdministracionPuedenEditar() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        iniciarSesionComo(usuario(Role.ASESOR));
        var e = assertThrows(ForbiddenOperationException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null))));
        assertMensaje("Solo un administrador o personal de administración puede gestionar ventas", e);
    }

    @Test
    void administracionPuedeEditar() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        iniciarSesionComo(usuario(Role.LIDER_AREA));
        var lineas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null)));
        assertEquals(1, lineas.size());
    }

    @Test
    void ventaInexistenteYReciboDeOtraVentaDanNoEncontrado() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var req = edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null));
        assertMensaje("Venta no encontrada", assertThrows(ResourceNotFoundException.class,
                () -> reciboVentaService.editarRecibo(-1L, pago.reciboId(), req)));
        assertMensaje("Recibo no encontrado", assertThrows(ResourceNotFoundException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), -1L, req)));
    }

    @Test
    void reciboAnuladoNoSeEdita() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        reciboVentaService.anularRecibo(venta.id(), pago.reciboId(), new ReciboAnularRequest("error de captura"));
        var e = assertThrows(ConflictException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null))));
        assertMensaje("Un recibo anulado no se puede editar", e);
    }

    // ---------------------------------------------------------------- folio, nota y fecha

    @Test
    void folioRepetidoDeOtroReciboSePermite() {
        // Los folios se pueden repetir (decisión del usuario, 2026-11-13; V84).
        PagoVentaDto uno = pagarMensualidad("5000", 2026, 2);
        PagoVentaDto dos = pagarMensualidad("5000", 2026, 3);
        var repetido = reciboVentaService.editarRecibo(venta.id(), dos.reciboId(),
                edicion(dos, uno.folio(), null, linea(dos, dos.tipo(), mesesDe(dos), false, null)));
        assertEquals(uno.folio(), repetido.get(0).folio());

        // con su propio folio (aunque cambie mayúsculas/espacios) no hay conflicto
        var ok = reciboVentaService.editarRecibo(venta.id(), dos.reciboId(),
                edicion(dos, "  " + dos.folio() + " ", null, linea(dos, dos.tipo(), mesesDe(dos), false, null)));
        assertEquals(dos.folio(), ok.get(0).folio());
    }

    @Test
    void folioNuevoNotaYFechaSeGuardanYQuedanEnLaBitacora() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2); // fecha 01/02/2026
        String folioNuevo = "NUEVO-" + etiqueta();
        var req = new ReciboEditarRequest(folioNuevo, LocalDate.of(2026, 3, 5), "  una nota  ",
                List.of(linea(pago, pago.tipo(), mesesDe(pago), false, null)), null, null);

        List<PagoVentaDto> editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), req);

        assertEquals(folioNuevo, editadas.get(0).folio());
        assertEquals("una nota", editadas.get(0).notas());
        assertEquals(LocalDate.of(2026, 3, 5), editadas.get(0).fecha());
        assertEquals("Folio " + pago.folio() + " → " + folioNuevo + " | Nota actualizada | Fecha 01/02/2026 → 05/03/2026",
                ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO));
    }

    @Test
    void fechaNulaConservaLaFechaDelRecibo() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null)));
        assertEquals(pago.fecha(), editadas.get(0).fecha());
    }

    @Test
    void sinCambiosLaBitacoraDiceSinCambios() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, pago.tipo(), mesesDe(pago), false, null)));
        assertEquals("Sin cambios", ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO));
    }

    @Test
    void lasLineasQueNoSeMencionanTambienTomanLaFechaNueva() {
        var lineas = reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.ENGANCHE, new BigDecimal("1000"), null, null, null),
                        new PagoVentaCreateRequest.Linea(TipoAbono.MENSUALIDAD, new BigDecimal("5000"), List.of(mes(2026, 2)), null, null))));
        PagoVentaDto enganche = lineas.get(0);
        PagoVentaDto mensualidad = lineas.get(1);

        var editadas = reciboVentaService.editarRecibo(venta.id(), enganche.reciboId(), edicion(enganche, enganche.folio(),
                LocalDate.of(2026, 4, 4), linea(enganche, TipoAbono.ENGANCHE, null, false, null)));

        assertEquals(2, editadas.size());
        assertTrue(editadas.stream().allMatch(l -> LocalDate.of(2026, 4, 4).equals(l.fecha())));
        assertEquals(TipoAbono.MENSUALIDAD, editadas.stream().filter(l -> l.id().equals(mensualidad.id())).findFirst().orElseThrow().tipo());
    }

    @Test
    void lineaQueNoPerteneceAlReciboSeRechaza() {
        PagoVentaDto uno = pagarMensualidad("5000", 2026, 2);
        PagoVentaDto dos = pagarMensualidad("5000", 2026, 3);
        var e = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), uno.reciboId(),
                edicion(uno, uno.folio(), null, linea(dos, dos.tipo(), mesesDe(dos), false, null))));
        assertMensaje("La línea " + dos.id() + " no pertenece a este recibo", e);
    }

    // ---------------------------------------------------------------- tipo, meses, extemporáneo

    @Test
    void cambiarLosMesesDeUnaMensualidadActualizaLaLineaYLoDejaEnLaBitacora() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicion(pago, pago.folio(), null,
                linea(pago, TipoAbono.MENSUALIDAD, List.of(mes(2026, 4), mes(2026, 3)), false, null)));

        assertEquals(List.of(new PagoVentaDto.MesCubiertoDto(2026, 3), new PagoVentaDto.MesCubiertoDto(2026, 4)), editadas.get(0).meses());
        String detalle = ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO);
        assertTrue(detalle.startsWith("Conceptos: Mensualidad $5000"), detalle);
        assertTrue(detalle.contains("(2026-02) → Mensualidad $5000"), detalle);
        assertTrue(detalle.endsWith("(2026-03, 2026-04)"), detalle);
    }

    @Test
    void unApartadoPuedeCubrirMesesYUnaCuentaDeComisionSeComportaComoMensualidad() {
        PagoVentaDto pago = pagar(TipoAbono.APARTADO, "1000", null, LocalDate.of(2026, 2, 1));
        var apartadoConMeses = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicion(pago, pago.folio(), null,
                linea(pago, TipoAbono.APARTADO, List.of(mes(2026, 2)), false, null)));
        assertEquals(1, apartadoConMeses.get(0).meses().size());

        var aCuenta = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicion(pago, pago.folio(), null,
                linea(pago, TipoAbono.A_CUENTA_COMISION, List.of(mes(2026, 5)), false, null)));
        assertEquals(TipoAbono.A_CUENTA_COMISION, aCuenta.get(0).tipo());
        assertEquals(List.of(new PagoVentaDto.MesCubiertoDto(2026, 5)), aCuenta.get(0).meses());
    }

    @Test
    void mesFueraDelPlazoOSinMesesORepetidoSeRechaza() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        BiConsumerEdicion editar = (meses, extemporaneo) -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, TipoAbono.MENSUALIDAD, meses, extemporaneo, null)));

        assertMensaje("El mes 2030-01 está fuera del plazo de la venta (2026-01 a 2027-08)",
                assertThrows(ValidationException.class, () -> editar.aplicar(List.of(mes(2030, 1)), false)));
        assertMensaje("El mes 2025-12 está fuera del plazo de la venta (2026-01 a 2027-08)",
                assertThrows(ValidationException.class, () -> editar.aplicar(List.of(mes(2025, 12)), false)));
        assertMensaje("El mes 2026-03 está repetido",
                assertThrows(ValidationException.class, () -> editar.aplicar(List.of(mes(2026, 3), mes(2026, 3)), false)));
        assertMensaje("Indica el mes o los meses que cubre la mensualidad",
                assertThrows(ValidationException.class, () -> editar.aplicar(null, false)));
        assertMensaje("Indica el mes o los meses que cubre la mensualidad",
                assertThrows(ValidationException.class, () -> editar.aplicar(List.of(), false)));
    }

    private interface BiConsumerEdicion {
        void aplicar(List<PagoVentaCreateRequest.Mes> meses, Boolean extemporaneo);
    }

    @Test
    void extemporaneoSinMesesSoloDespuesDeQueTerminaElPlan() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        // dentro del plazo (el plan termina en 2027-08): se rechaza
        var dentro = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), LocalDate.of(2027, 8, 31), linea(pago, TipoAbono.MENSUALIDAD, null, true, null))));
        assertMensaje("El plan termina en 2027-08: un pago extemporáneo solo se registra después; dentro del plazo elige los meses que cubre", dentro);

        // con meses: se rechaza
        var conMeses = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), LocalDate.of(2028, 1, 10), linea(pago, TipoAbono.MENSUALIDAD, List.of(mes(2026, 2)), true, null))));
        assertMensaje("Un pago extemporáneo no lleva meses: solo abona al saldo", conMeses);

        // después del plazo y sin meses: se acepta y la bitácora lo anota
        var editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), LocalDate.of(2028, 1, 10), linea(pago, TipoAbono.MENSUALIDAD, null, true, null)));
        assertTrue(editadas.get(0).extemporaneo());
        assertTrue(editadas.get(0).meses().isEmpty());
        assertTrue(ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO).endsWith("(extemporáneo)"));
    }

    @Test
    void unReciboYaExtemporaneoSePuedeEditarSinRevalidarLaFecha() {
        // sin meses: hay que marcarlo extemporáneo desde el registro (y la fecha debe ser posterior al plan)
        PagoVentaDto ext = reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(LocalDate.of(2028, 1, 10), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.MENSUALIDAD, new BigDecimal("1000"), null, null, true)))).get(0);
        assertTrue(ext.extemporaneo());

        // se mueve la fecha a una dentro del plazo: como ya era extemporáneo, no se vuelve a revisar
        // OJO: comportamiento actual, posible bug: un extemporáneo editado a una fecha dentro del plazo queda guardado.
        var editadas = reciboVentaService.editarRecibo(venta.id(), ext.reciboId(),
                edicion(ext, ext.folio(), LocalDate.of(2026, 5, 1), linea(ext, TipoAbono.MENSUALIDAD, null, true, null)));
        assertTrue(editadas.get(0).extemporaneo());
        assertEquals(LocalDate.of(2026, 5, 1), editadas.get(0).fecha());
    }

    @Test
    void soloUnaMensualidadPuedeSerExtemporanea() {
        PagoVentaDto pago = pagar(TipoAbono.ENGANCHE, "1000", null, LocalDate.of(2026, 2, 1));
        var e = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, TipoAbono.ENGANCHE, null, true, null))));
        assertMensaje("Solo una mensualidad puede ser un pago extemporáneo", e);
    }

    @Test
    void reglasPorTipoDeAbono() {
        PagoVentaDto pago = pagar(TipoAbono.ENGANCHE, "1000", null, LocalDate.of(2026, 2, 1));
        BiConsumerTipo editar = (tipo, meses, aportacionId) -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null,
                        new ReciboEditarRequest.Linea(pago.id(), tipo, meses, aportacionId, false, null)));

        assertMensaje("Solo una mensualidad o un apartado cubren meses",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.ENGANCHE, List.of(mes(2026, 2)), null)));
        assertMensaje("Una mensualidad no se liga a una aportación",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.MENSUALIDAD, List.of(mes(2026, 2)), 99L)));
        assertMensaje("Un apartado no se liga a una aportación",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.APARTADO, List.of(mes(2026, 2)), 99L)));
        assertMensaje("Indica qué aportación programada paga",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.APORTACION, null, null)));
        assertMensaje("Esa aportación no pertenece a esta venta",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.APORTACION, null, -5L)));
        assertMensaje("Solo una aportación se liga a una aportación programada",
                assertThrows(ValidationException.class, () -> editar.aplicar(TipoAbono.ENGANCHE, null, 99L)));
    }

    private interface BiConsumerTipo {
        void aplicar(TipoAbono tipo, List<PagoVentaCreateRequest.Mes> meses, Long aportacionId);
    }

    @Test
    void enVentaSinEngancheElTipoEngancheSeRechazaYEnVentaDeContadoLaMensualidad() {
        // venta sin enganche
        var lote2 = lote(samai(), etiqueta(), "2", "200", EstadoLote.DISPONIBLE);
        VentaDto sinEnganche = crearVenta(admin, lote2, "60000", null, "5000", 12, LocalDate.of(2026, 1, 15));
        PagoVentaDto pago = reciboVentaService.registrarPago(sinEnganche.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, new BigDecimal("500"), null, null, null)))).get(0);
        var e = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(sinEnganche.id(), pago.reciboId(),
                edicion(pago, pago.folio(), null, linea(pago, TipoAbono.ENGANCHE, null, false, null))));
        assertMensaje("Esta venta no tiene enganche: registra el pago como mensualidad o apartado", e);

        // venta de contado
        var lote3 = lote(samai(), etiqueta(), "3", "200", EstadoLote.DISPONIBLE);
        VentaDto contado = crearVenta(admin, lote3, "50000", null, null, null, LocalDate.of(2026, 1, 15));
        PagoVentaDto pagoContado = reciboVentaService.registrarPago(contado.id(), new PagoVentaCreateRequest(LocalDate.of(2026, 2, 1), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, new BigDecimal("500"), null, null, null)))).get(0);
        var e2 = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(contado.id(), pagoContado.reciboId(),
                edicion(pagoContado, pagoContado.folio(), null, linea(pagoContado, TipoAbono.MENSUALIDAD, List.of(mes(2026, 2)), false, null))));
        assertMensaje("Esta venta no tiene mensualidades", e2);
        var e3 = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(contado.id(), pagoContado.reciboId(),
                edicion(pagoContado, pagoContado.folio(), null, linea(pagoContado, TipoAbono.APARTADO, List.of(mes(2026, 2)), false, null))));
        assertMensaje("Esta venta no tiene mensualidades: el apartado va a la deuda total", e3);
    }

    @Test
    void unaValidacionFallidaNoDejaNadaGuardado() {
        PagoVentaDto uno = pagarMensualidad("5000", 2026, 2);
        PagoVentaDto dos = pagarMensualidad("5000", 2026, 3);
        // el recibo uno: la primera línea es válida pero la segunda no pertenece al recibo
        assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), uno.reciboId(), new ReciboEditarRequest(
                "OTRO-" + etiqueta(), LocalDate.of(2026, 9, 9), "x",
                List.of(linea(uno, TipoAbono.MENSUALIDAD, List.of(mes(2026, 6)), false, null), linea(dos, TipoAbono.MENSUALIDAD, List.of(mes(2026, 7)), false, null)),
                null, null)));
        var vigentes = reciboVentaService.listarPagos(venta.id());
        PagoVentaDto despues = vigentes.stream().filter(p -> p.id().equals(uno.id())).findFirst().orElseThrow();
        assertEquals(uno.folio(), despues.folio());
        assertEquals(uno.fecha(), despues.fecha());
        assertEquals(List.of(new PagoVentaDto.MesCubiertoDto(2026, 2)), despues.meses());
    }

    // ---------------------------------------------------------------- corrección de monto

    @Test
    void corregirElMontoExigeLaContrasenaDeQuienLoHace() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        assertMensaje("Contraseña incorrecta", assertThrows(ForbiddenOperationException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "4000", null, "motivo"))));
        assertMensaje("Contraseña incorrecta", assertThrows(ForbiddenOperationException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "4000", "mala", "motivo"))));
        assertEquals(0, new BigDecimal("5000").compareTo(abonado()));
    }

    @Test
    void corregirElMontoExigeUnMotivo() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        assertMensaje("Escribe el motivo del cambio de monto", assertThrows(ValidationException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "4000", CONTRASENA, null))));
        assertMensaje("Escribe el motivo del cambio de monto", assertThrows(ValidationException.class,
                () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "4000", CONTRASENA, "   "))));
    }

    @Test
    void montoCorregidoCambiaLoAbonadoYSeAnotaEnLaBitacoraConElMotivo() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicionDeMonto(pago, "4000.005", CONTRASENA, "  se capturó de más  "));

        // se redondea a 2 decimales hacia arriba (HALF_UP)
        assertEquals(0, new BigDecimal("4000.01").compareTo(editadas.get(0).monto()));
        assertEquals(0, new BigDecimal("4000.01").compareTo(abonado()));
        String detalle = ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO);
        assertTrue(detalle.startsWith("Monto Mensualidad $5000"), detalle);
        assertTrue(detalle.contains("→ $4000.01 · Motivo: se capturó de más"), detalle);
    }

    @Test
    void montoIgualOMontoNuloNoCuentanComoCorreccionYNoPidenContrasena() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        // mismo monto con otra escala: no es corrección
        reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "5000.00", null, null));
        reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, null, null, null));
        assertEquals(0, new BigDecimal("5000").compareTo(abonado()));
        assertEquals("Sin cambios", ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO));
    }

    @Test
    void elMontoNuevoNoPuedePasarElSaldoPendiente() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        // precio 120,000; abonado 5,000 → saldo 115,000; subir a 125,000 suma 120,000 de diferencia
        var e = assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(),
                edicionDeMonto(pago, "125000", CONTRASENA, "motivo")));
        assertTrue(e.getMessage().startsWith("Con el monto nuevo el abonado pasaría del precio: el saldo pendiente es $115000"), e.getMessage());
        assertEquals(0, new BigDecimal("5000").compareTo(abonado()));

        // hasta justo el saldo sí se puede
        reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), edicionDeMonto(pago, "120000", CONTRASENA, "motivo"));
        assertEquals(0, new BigDecimal("120000").compareTo(abonado()));
    }

    @Test
    void laValidacionDeMesesVaAntesQueLaDeContrasenaDelMonto() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var req = new ReciboEditarRequest(pago.folio(), null, pago.notas(),
                List.of(linea(pago, TipoAbono.MENSUALIDAD, List.of(mes(2030, 1)), false, "4000")), null, null);
        assertThrows(ValidationException.class, () -> reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), req));
    }

    @Test
    void cambiarTipoYMontoJuntosDejaAmbosCambiosEnLaBitacora() {
        PagoVentaDto pago = pagarMensualidad("5000", 2026, 2);
        var req = new ReciboEditarRequest(pago.folio(), null, pago.notas(),
                List.of(linea(pago, TipoAbono.ENGANCHE, null, false, "3000")), CONTRASENA, "era enganche");
        var editadas = reciboVentaService.editarRecibo(venta.id(), pago.reciboId(), req);

        assertEquals(TipoAbono.ENGANCHE, editadas.get(0).tipo());
        assertTrue(editadas.get(0).meses().isEmpty());
        String detalle = ultimoDetalleDeBitacora(pago, AccionRecibo.EDITADO);
        assertTrue(detalle.contains("Monto Mensualidad $5000"), detalle);
        assertTrue(detalle.contains(" | Conceptos: Mensualidad $5000"), detalle);
        assertTrue(detalle.contains("→ Enganche $3000"), detalle);
    }
}
