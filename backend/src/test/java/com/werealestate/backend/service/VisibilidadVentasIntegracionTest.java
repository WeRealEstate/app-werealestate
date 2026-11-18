package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.ClienteListaDto;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.FinanzasValorDto;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.ModalidadPago;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VisibilidadVentas;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La cuenta «We Real Estate (Dirección)» no ve las ventas en efectivo en ninguna parte (VisibilidadVentas); cualquier
 * otro admin las ve como siempre. Contra la BD local, en una transacción que se revierte.
 */
class VisibilidadVentasIntegracionTest extends PruebaDeIntegracion {

    @Autowired EntityManager em;
    @Autowired ClienteService clienteService;
    @Autowired FinanzasService finanzasService;
    @Autowired ComisionService comisionService;
    @Autowired ScoreService scoreService;
    @Autowired EstadoCuentaService estadoCuentaService;
    @Autowired LoteConsultaService loteConsultaService;
    @Autowired NotificacionService notificacionService;
    @Autowired com.werealestate.backend.repository.VentaRepository ventaRepository;

    private Usuario admin;
    private Usuario direccion;
    private Usuario asesor;
    private VentaDto efectivo;
    private VentaDto transferencia;
    private Long clienteEfectivo;
    private Long loteEfectivo;

    @BeforeEach
    void preparar() {
        // NumeracionVisible guarda su cargador en un campo estático: en la suite conviven varios contextos de Spring y
        // el último en arrancar lo deja apuntando a su propia conexión (que no ve los datos de esta transacción).
        com.werealestate.backend.model.NumeracionVisible.usarCargador(ventaRepository::filasDeNumeracion);
        com.werealestate.backend.model.NumeracionVisible.olvidar();
        admin = usuario(Role.ADMIN);
        direccion = usuarios.findByEmail(VisibilidadVentas.CORREO_SIN_EFECTIVO)
                .orElseGet(() -> usuarios.save(new Usuario("We Real Estate (Dirección)", VisibilidadVentas.CORREO_SIN_EFECTIVO,
                        passwordEncoder.encode(CONTRASENA), Role.ADMIN, null)));
        asesor = usuario(Role.ASESOR);
        iniciarSesionComo(admin);
        var lote1 = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        var lote2 = lote(samai(), etiqueta(), "2", "200", EstadoLote.DISPONIBLE);
        loteEfectivo = lote1.getId();
        efectivo = crearVenta(asesor, lote1, "100000", null, null, null, LocalDate.now());
        transferencia = crearVenta(asesor, lote2, "120000", null, null, null, LocalDate.now());
        modalidad(efectivo.id(), ModalidadPago.EFECTIVO);
        modalidad(transferencia.id(), ModalidadPago.TRANSFERENCIA);
        clienteEfectivo = ((Number) em.createNativeQuery("select cliente_id from venta where id = :v")
                .setParameter("v", efectivo.id()).getSingleResult()).longValue();
    }

    private void modalidad(Long ventaId, ModalidadPago m) {
        em.createNativeQuery("update venta set modalidad_pago = :m where id = :v")
                .setParameter("m", m.name()).setParameter("v", ventaId).executeUpdate();
        em.flush();
        em.clear();
    }

    private List<Long> idsDeVentasBuscadas() {
        return ventaService.buscarPaginado(null, false, 0, 500, null).contenido().stream().map(VentaDto::id).toList();
    }

    @Test
    void otroAdminVeTodoComoSiempre() {
        iniciarSesionComo(admin);
        assertTrue(idsDeVentasBuscadas().contains(efectivo.id()));
        assertEquals(efectivo.id(), ventaService.obtener(efectivo.id()).id());
        assertTrue(clienteService.listar().stream().anyMatch(c -> c.id().equals(clienteEfectivo)));
        assertTrue(finanzasService.valorVendido().detalle().stream().anyMatch(l -> efectivo.id().equals(l.ventaId())));
        assertTrue(comisionService.listar().stream().anyMatch(c -> efectivo.id().equals(c.ventaId())));
    }

    @Test
    void direccionNoVeLaVentaEnEfectivoEnVentas() {
        iniciarSesionComo(direccion);
        List<Long> ids = idsDeVentasBuscadas();
        assertFalse(ids.contains(efectivo.id()));
        assertTrue(ids.contains(transferencia.id()));
        assertThrows(ResourceNotFoundException.class, () -> ventaService.obtener(efectivo.id()));
        // Con su numeración, el número real de la venta en efectivo es otra venta (o ninguna), nunca ella.
        try {
            assertFalse(efectivo.id().equals(ventaService.obtenerPorNumero(efectivo.etiqueta()).id()));
        } catch (ResourceNotFoundException esperado) {
            // tampoco existe con ese número
        }
        assertThrows(ResourceNotFoundException.class, () -> estadoCuentaService.deVenta(efectivo.id()));
        assertThrows(ResourceNotFoundException.class, () -> ventaService.obtenerPorLote(loteEfectivo));
        assertEquals(transferencia.id(), ventaService.obtener(transferencia.id()).id());
    }

    @Test
    void losContadoresNoCuentanLaVentaEnEfectivo() {
        iniciarSesionComo(admin);
        var todos = ventaService.contadores(samai().getId());
        iniciarSesionComo(direccion);
        var sinEfectivo = ventaService.contadores(samai().getId());
        assertEquals(todos.ventasRegistradas() - contarEfectivo(), sinEfectivo.ventasRegistradas());
    }

    private long contarEfectivo() {
        return ((Number) em.createNativeQuery("select count(distinct v.id) from venta v join venta_lote vl on vl.venta_id = v.id "
                + "join lote l on l.id = vl.lote_id where l.desarrollo_id = :d and v.modalidad_pago = 'EFECTIVO'")
                .setParameter("d", samai().getId()).getSingleResult()).longValue();
    }

    @Test
    void elClienteQueSoloComproEnEfectivoNoExisteParaDireccion() {
        iniciarSesionComo(direccion);
        List<ClienteListaDto> lista = clienteService.listar();
        assertFalse(lista.stream().anyMatch(c -> c.id().equals(clienteEfectivo)));
        assertThrows(ResourceNotFoundException.class, () -> clienteService.obtener(clienteEfectivo));
    }

    @Test
    void finanzasComisionesYScoreNoLaCuentan() {
        iniciarSesionComo(direccion);
        FinanzasValorDto valor = finanzasService.valorVendido();
        assertFalse(valor.detalle().stream().anyMatch(l -> efectivo.id().equals(l.ventaId())));
        assertTrue(valor.detalle().stream().anyMatch(l -> transferencia.id().equals(l.ventaId())));
        List<ComisionDto> comisiones = comisionService.listar();
        assertFalse(comisiones.stream().anyMatch(c -> efectivo.id().equals(c.ventaId())));
        assertTrue(comisiones.stream().anyMatch(c -> transferencia.id().equals(c.ventaId())));

        LocalDate hoy = LocalDate.now();
        long conDireccion = scoreService.ranking(hoy, hoy).totalVentas();
        iniciarSesionComo(admin);
        long conAdmin = scoreService.ranking(hoy, hoy).totalVentas();
        assertEquals(conAdmin - 1, conDireccion);
    }

    @Test
    void elLoteVaSinElChipDeLaVentaPeroTampocoComoSinVenta() {
        iniciarSesionComo(direccion);
        LoteDto l = loteConsultaService.conVentas(lotes.findById(loteEfectivo).orElseThrow());
        assertEquals(null, l.ventaNumero());
        assertFalse(l.sinVentaRegistrada());
        iniciarSesionComo(admin);
        assertEquals(efectivo.etiqueta(), loteConsultaService.conVentas(lotes.findById(loteEfectivo).orElseThrow()).ventaNumero());
    }

    @Test
    void elAvisoDeVentaNuevaNoLeLlega() {
        // Los avisos también se calculan sin sesión (push en segundo plano): se decide por el usuario destinatario.
        sinSesion();
        List<Long> paraDireccion = notificacionService.listarPara(direccion).stream()
                .filter(n -> "VENTA_NUEVA".equals(n.tipo())).map(NotificacionDto::ventaId).toList();
        List<Long> paraAdmin = notificacionService.listarPara(usuario(Role.ADMIN)).stream()
                .filter(n -> "VENTA_NUEVA".equals(n.tipo())).map(NotificacionDto::ventaId).toList();
        assertTrue(paraAdmin.contains(efectivo.id()));
        assertFalse(paraDireccion.contains(efectivo.id()));
        assertTrue(paraDireccion.contains(transferencia.id()));
    }

    @Test
    void direccionNoPuedeRegistrarUnaVentaEnEfectivo() {
        iniciarSesionComo(direccion);
        var lote = lote(samai(), etiqueta(), "3", "200", EstadoLote.DISPONIBLE);
        var cliente = cliente("Comprador " + etiqueta());
        var request = new VentaCreateRequest(
                List.of(new VentaLoteItemRequest(lote.getId(), new BigDecimal("90000"))),
                cliente.getId(), null, null, asesor.getId(), null,
                "Contado", LocalDate.now(), null, null, null, null,
                LocalDate.now().getDayOfMonth(), true, null, null,
                false, null, null, null, null, null, null, null, ModalidadPago.EFECTIVO);
        assertThrows(ValidationException.class, () -> ventaService.crear(request));
    }

    /** El número que ve Dirección: el real menos las ventas en efectivo del mismo desarrollo con número menor o igual. */
    private long visibleDe(VentaDto v) {
        long real = ((Number) em.createNativeQuery("select numero_desarrollo from venta where id = :v")
                .setParameter("v", v.id()).getSingleResult()).longValue();
        long efectivoAntes = ((Number) em.createNativeQuery("select count(*) from venta where desarrollo_id = "
                + "(select desarrollo_id from venta where id = :v) and modalidad_pago = 'EFECTIVO' and numero_desarrollo <= :n")
                .setParameter("v", v.id()).setParameter("n", real).getSingleResult()).longValue();
        return real - efectivoAntes;
    }

    @Test
    void direccionVeLosNumerosConsecutivosSinLasVentasEnEfectivo() {
        iniciarSesionComo(admin);
        VentaDto real = ventaService.obtener(transferencia.id());
        long visible = visibleDe(transferencia);
        assertTrue(visible < real.numero(), "la venta en efectivo anterior ya no cuenta");

        iniciarSesionComo(direccion);
        com.werealestate.backend.model.NumeracionVisible.olvidar();
        VentaDto suya = ventaService.obtener(transferencia.id());
        assertEquals("S-" + visible, suya.etiqueta());
        assertEquals(visible, suya.numero());
        // Su dirección y su buscador usan su número.
        assertEquals(transferencia.id(), ventaService.obtenerPorNumero("S-" + visible).id());
        assertTrue(ventaService.buscarPaginado("S-" + visible, false, 0, 50, null).contenido().stream()
                .anyMatch(v -> v.id().equals(transferencia.id())));
        // No escribe números a mano.
        var lote = lote(samai(), etiqueta(), "4", "200", EstadoLote.DISPONIBLE);
        var cliente = cliente("Comprador " + etiqueta());
        var conNumero = new VentaCreateRequest(
                List.of(new VentaLoteItemRequest(lote.getId(), new BigDecimal("90000"))),
                cliente.getId(), 7L, null, asesor.getId(), null,
                "Contado", LocalDate.now(), null, null, null, null,
                LocalDate.now().getDayOfMonth(), true, null, null,
                false, null, null, null, null, null, null, null, ModalidadPago.TRANSFERENCIA);
        assertThrows(ValidationException.class, () -> ventaService.crear(conNumero));

        // Los demás siguen viendo el número real.
        iniciarSesionComo(admin);
        com.werealestate.backend.model.NumeracionVisible.olvidar();
        assertEquals(real.etiqueta(), ventaService.obtener(transferencia.id()).etiqueta());
    }
}
