package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.MovimientoLoteDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.service.HistorialLoteService;
import com.werealestate.backend.service.LoteConsultaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Caracterización de {@link LoteConsultaService#buscarPaginado} (filtros y visibilidad de «Reservado por We») y de
 * {@link HistorialLoteService#buscarMovimientos}, contra la BD real dentro de una transacción que se revierte. Cada prueba
 * filtra por su propia etiqueta de manzana para no mezclarse con lotes reales. */
class LoteBuscarPaginadoIntegracionTest extends PruebaDeIntegracion {

    @Autowired LoteConsultaService consultaService;
    @Autowired HistorialLoteService historialService;
    @Autowired MovimientoLoteRepository movimientos;

    private PaginaDto<LoteDto> buscar(String manzana, String numero, Long desarrolloId, String estado, String supMin,
            String supMax, Boolean reservadoWe) {
        return consultaService.buscarPaginado(manzana, numero, desarrolloId, estado,
                supMin == null ? null : new BigDecimal(supMin), supMax == null ? null : new BigDecimal(supMax),
                reservadoWe, 0, 100);
    }

    private static List<String> numeros(PaginaDto<LoteDto> pagina) {
        return pagina.contenido().stream().map(LoteDto::numeroLote).toList();
    }

    // ---------------------------------------------------------------- filtros simples

    @Test
    void manzanaFiltraPorTextoContenidoSinImportarMayusculasYRecortaEspacios() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag + "A", "1", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag + "B", "2", "200", EstadoLote.DISPONIBLE);

        assertEquals(List.of("1"), numeros(buscar(" " + tag.toLowerCase() + "a ", null, null, null, null, null, null)));
        assertEquals(List.of("1", "2"), numeros(buscar(tag.toLowerCase(), null, null, null, null, null, null)));
    }

    @Test
    void numeroDeLoteFiltraPorTextoContenidoSinImportarMayusculas() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "12A", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "13", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "212a", "200", EstadoLote.DISPONIBLE);

        // "12a" está contenido en "12A" y en "212a" (comodín a ambos lados)
        assertEquals(List.of("12A", "212a"), numeros(buscar(tag, "12a", null, null, null, null, null)));
        assertEquals(List.of("13"), numeros(buscar(tag, "13", null, null, null, null, null)));
    }

    @Test
    void textoEnBlancoEnManzanaONumeroNoFiltra() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);

        assertEquals(List.of("1", "2"), numeros(buscar(tag, "   ", null, "", null, null, null)));
    }

    @Test
    void desarrolloFiltraPorId() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        Desarrollo samai = samai();
        Desarrollo nanuu = nanuu();
        lote(samai, tag, "1", "200", EstadoLote.DISPONIBLE);
        lote(nanuu, tag, "2", "200", EstadoLote.DISPONIBLE);

        assertEquals(List.of("1"), numeros(buscar(tag, null, samai.getId(), null, null, null, null)));
        assertEquals(List.of("2"), numeros(buscar(tag, null, nanuu.getId(), null, null, null, null)));
        assertEquals(List.of("1", "2"), numeros(buscar(tag, null, null, null, null, null, null)));
    }

    @Test
    void superficieMinimaYMaximaSonInclusivas() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "1", "100", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "3", "300", EstadoLote.DISPONIBLE);

        assertEquals(List.of("2", "3"), numeros(buscar(tag, null, null, null, "200", null, null)));
        assertEquals(List.of("1", "2"), numeros(buscar(tag, null, null, null, null, "200", null)));
        assertEquals(List.of("2"), numeros(buscar(tag, null, null, null, "200", "200", null)));
        assertEquals(List.of(), numeros(buscar(tag, null, null, null, "301", null, null)));
    }

    @Test
    void estadoComunFiltraPorIgualdad() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "2", "200", EstadoLote.APARTADO);
        lote(samai(), tag, "3", "200", EstadoLote.EN_PROCESO_DE_FIRMA);

        assertEquals(List.of("2"), numeros(buscar(tag, null, null, "APARTADO", null, null, null)));
        assertEquals(List.of("1"), numeros(buscar(tag, null, null, "DISPONIBLE", null, null, null)));
        // reservadoWe solo importa dentro de VENDIDO: con otro estado se ignora
        assertEquals(List.of("2"), numeros(buscar(tag, null, null, "APARTADO", null, null, true)));
    }

    @Test
    void estadoQueNoExisteLanzaIllegalArgument() {
        iniciarSesionComo(usuario(Role.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> buscar(null, null, null, "NO_EXISTE", null, null, null));
    }

    @Test
    void ordenaPorLongitudYLuegoAlfabeticamenteManzanaYNumero() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "10", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "100", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);

        assertEquals(List.of("1", "2", "10", "100"), numeros(buscar(tag, null, null, null, null, null, null)));
    }

    @Test
    void paginaConHayMasYTamanoMinimoUno() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "3", "200", EstadoLote.DISPONIBLE);

        PaginaDto<LoteDto> primera = consultaService.buscarPaginado(tag, null, null, null, null, null, null, 0, 2);
        assertEquals(List.of("1", "2"), numeros(primera));
        assertTrue(primera.hayMas());
        PaginaDto<LoteDto> segunda = consultaService.buscarPaginado(tag, null, null, null, null, null, null, 1, 2);
        assertEquals(List.of("3"), numeros(segunda));
        assertFalse(segunda.hayMas());
        // pagina negativa = 0 y tamaño 0 = 1
        PaginaDto<LoteDto> raro = consultaService.buscarPaginado(tag, null, null, null, null, null, null, -5, 0);
        assertEquals(List.of("1"), numeros(raro));
        assertTrue(raro.hayMas());
    }

    @Test
    void laListaNoTraeElPoligonoDelMapa() {
        iniciarSesionComo(usuario(Role.ADMIN));
        String tag = etiqueta();
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        assertNull(buscar(tag, null, null, null, null, null, null).contenido().get(0).mapaPoligono());
    }

    // ---------------------------------------------------------------- reservados por We (admin y administración)

    /** Cuatro lotes: 1 RESERVADO_WE, 2 VENDIDO sin venta (reservado derivado), 3 VENDIDO con venta, 4 DISPONIBLE. */
    private String escenarioReservados(Usuario admin) {
        String tag = etiqueta();
        iniciarSesionComo(admin);
        lote(samai(), tag, "1", "200", EstadoLote.RESERVADO_WE);
        lote(samai(), tag, "2", "200", EstadoLote.VENDIDO);
        Lote conVenta = lote(samai(), tag, "3", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "4", "200", EstadoLote.DISPONIBLE);
        crearVenta(admin, conVenta, "100000", null, null, null, LocalDate.of(2026, 1, 15));
        // la venta sin contrato firmado deja el lote EN_PROCESO_DE_FIRMA: se pasa a VENDIDO como una venta ya firmada
        Lote l3 = lotes.findAll().stream().filter(l -> tag.equals(l.getManzana()) && "3".equals(l.getNumeroLote())).findFirst().orElseThrow();
        l3.cambiarEstado(EstadoLote.VENDIDO, null);
        lotes.saveAndFlush(l3);
        return tag;
    }

    @Test
    void adminPidiendoReservadoWeVeElEstadoReservadoMasLosVendidosSinVenta() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);

        PaginaDto<LoteDto> r = buscar(tag, null, null, "RESERVADO_WE", null, null, null);
        assertEquals(List.of("1", "2"), numeros(r));
        // el estado real se muestra a admin; el vendido sin venta sale marcado
        assertEquals(EstadoLote.RESERVADO_WE, r.contenido().get(0).estado());
        assertEquals(EstadoLote.VENDIDO, r.contenido().get(1).estado());
        assertTrue(r.contenido().get(1).sinVentaRegistrada());
    }

    @Test
    void adminPidiendoVendidoSinReservadoWeVeLosVendidosConYSinVentaPeroNoElEstadoReservado() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);

        assertEquals(List.of("2", "3"), numeros(buscar(tag, null, null, "VENDIDO", null, null, null)));
    }

    @Test
    void adminPidiendoVendidoConReservadoWeTrueVeSoloLosVendidosSinVenta() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);

        assertEquals(List.of("2"), numeros(buscar(tag, null, null, "VENDIDO", null, null, true)));
    }

    @Test
    void adminPidiendoVendidoConReservadoWeFalseVeSoloLosVendidosConVenta() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);

        PaginaDto<LoteDto> r = buscar(tag, null, null, "VENDIDO", null, null, false);
        assertEquals(List.of("3"), numeros(r));
        assertFalse(r.contenido().get(0).sinVentaRegistrada());
        assertTrue(r.contenido().get(0).ventaNumero() != null && !r.contenido().get(0).ventaNumero().isBlank());
    }

    @Test
    void administracionVeLosReservadosIgualQueAdmin() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);
        iniciarSesionComo(usuario(Role.LIDER_AREA));

        assertEquals(List.of("1", "2"), numeros(buscar(tag, null, null, "RESERVADO_WE", null, null, null)));
        assertEquals(List.of("2"), numeros(buscar(tag, null, null, "VENDIDO", null, null, true)));
        assertEquals(EstadoLote.RESERVADO_WE, buscar(tag, null, null, "RESERVADO_WE", null, null, null).contenido().get(0).estado());
    }

    @Test
    void sinFiltroDeEstadoAdminVeCadaLoteConSuEstadoRealYMarcadoSiEsVendidoSinVenta() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);

        List<LoteDto> todos = buscar(tag, null, null, null, null, null, null).contenido();
        assertEquals(List.of(EstadoLote.RESERVADO_WE, EstadoLote.VENDIDO, EstadoLote.VENDIDO, EstadoLote.DISPONIBLE),
                todos.stream().map(LoteDto::estado).toList());
        assertEquals(List.of(false, true, false, false), todos.stream().map(LoteDto::sinVentaRegistrada).toList());
    }

    // ---------------------------------------------------------------- resto de los roles

    @Test
    void asesorPidiendoVendidoOReservadoWeRecibeAmbosYElReservadoSaleDisfrazadoDeVendido() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);
        iniciarSesionComo(usuario(Role.ASESOR));

        for (String pedido : List.of("VENDIDO", "RESERVADO_WE")) {
            PaginaDto<LoteDto> r = buscar(tag, null, null, pedido, null, null, null);
            assertEquals(List.of("1", "2", "3"), numeros(r), pedido);
            assertTrue(r.contenido().stream().allMatch(l -> l.estado() == EstadoLote.VENDIDO), pedido);
            // y nada de marcas internas: ni "sin venta" ni número de venta
            assertTrue(r.contenido().stream().allMatch(l -> !l.sinVentaRegistrada() && l.ventaNumero() == null), pedido);
        }
    }

    @Test
    void asesorIgnoraElFiltroReservadoWe() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);
        iniciarSesionComo(usuario(Role.ASESOR));

        assertEquals(List.of("1", "2", "3"), numeros(buscar(tag, null, null, "VENDIDO", null, null, true)));
        assertEquals(List.of("1", "2", "3"), numeros(buscar(tag, null, null, "VENDIDO", null, null, false)));
    }

    @Test
    void asesorConOtroEstadoFiltraPorIgualdad() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);
        iniciarSesionComo(usuario(Role.EQUIPO_INTERNO));

        assertEquals(List.of("4"), numeros(buscar(tag, null, null, "DISPONIBLE", null, null, null)));
    }

    @Test
    void asesorSinFiltroDeEstadoVeElReservadoDisfrazadoDeVendido() {
        Usuario admin = usuario(Role.ADMIN);
        String tag = escenarioReservados(admin);
        iniciarSesionComo(usuario(Role.ASESOR));

        List<LoteDto> todos = buscar(tag, null, null, null, null, null, null).contenido();
        assertEquals(List.of(EstadoLote.VENDIDO, EstadoLote.VENDIDO, EstadoLote.VENDIDO, EstadoLote.DISPONIBLE),
                todos.stream().map(LoteDto::estado).toList());
    }

    // ---------------------------------------------------------------- historial de movimientos

    private MovimientoLote movimiento(Lote lote, Usuario quien, EstadoLote nuevo, String nota) {
        return movimientos.saveAndFlush(new MovimientoLote(lote, EstadoLote.DISPONIBLE, nuevo, quien, "Asesor X", "Cliente X",
                null, nota));
    }

    @Test
    void movimientosFiltranPorManzanaNumeroYDesarrolloYVanDelMasRecienteAlMasAntiguo() throws InterruptedException {
        Usuario admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        String tag = etiqueta();
        Lote a = lote(samai(), tag + "A", "7", "200", EstadoLote.DISPONIBLE);
        Lote b = lote(nanuu(), tag + "B", "8", "200", EstadoLote.DISPONIBLE);
        movimiento(a, admin, EstadoLote.APARTADO, "primero");
        Thread.sleep(5);
        movimiento(a, admin, EstadoLote.DISPONIBLE, "segundo");
        Thread.sleep(5);
        movimiento(b, admin, EstadoLote.APARTADO, "tercero");

        List<MovimientoLoteDto> todos = historialService.buscarMovimientos(tag.toLowerCase(), null, null, 0, 50).contenido();
        assertEquals(List.of("tercero", "segundo", "primero"), todos.stream().map(MovimientoLoteDto::nota).toList());
        assertEquals(2, historialService.buscarMovimientos(tag + "A", "7", null, 0, 50).contenido().size());
        assertEquals(0, historialService.buscarMovimientos(tag + "A", "8", null, 0, 50).contenido().size());
        assertEquals(1, historialService.buscarMovimientos(tag, null, nanuu().getId(), 0, 50).contenido().size());
    }

    @Test
    void movimientoDeUnLoteReservadoWeSaleDisfrazadoParaUnAsesorYCompletoParaAdmin() {
        Usuario admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        String tag = etiqueta();
        Lote reservado = lote(samai(), tag, "1", "200", EstadoLote.RESERVADO_WE);
        movimiento(reservado, admin, EstadoLote.RESERVADO_WE, "guardado por la empresa");

        MovimientoLoteDto paraAdmin = historialService.buscarMovimientos(tag, null, null, 0, 10).contenido().get(0);
        assertEquals(EstadoLote.RESERVADO_WE, paraAdmin.estadoNuevo());
        assertEquals("guardado por la empresa", paraAdmin.nota());

        iniciarSesionComo(usuario(Role.ASESOR));
        MovimientoLoteDto paraAsesor = historialService.buscarMovimientos(tag, null, null, 0, 10).contenido().get(0);
        assertEquals(EstadoLote.VENDIDO, paraAsesor.estadoNuevo());
        assertNull(paraAsesor.nota());
    }
}
