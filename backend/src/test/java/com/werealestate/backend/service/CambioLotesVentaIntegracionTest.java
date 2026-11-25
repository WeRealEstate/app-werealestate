package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaCambiarLotesRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.repository.VentaComisionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Cambiar los lotes de una venta registrada (contra la BD local, en una transacción que se revierte). */
class CambioLotesVentaIntegracionTest extends PruebaDeIntegracion {

    @Autowired CambioLotesVentaService cambio;
    @Autowired VentaComisionRepository comisiones;
    @Autowired EntityManager em;

    private Usuario admin;
    private Lote viejo;
    private Lote nuevo;
    private VentaDto venta;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        viejo = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        nuevo = lote(samai(), etiqueta(), "2", "200", EstadoLote.DISPONIBLE);
        venta = crearVenta(usuario(Role.ASESOR), viejo, "100000", null, "1000", 60, LocalDate.now().minusMonths(2));
        em.flush();
    }

    private VentaDto cambiar(List<VentaLoteItemRequest> lotes, String mensualidad) {
        VentaDto v = cambio.cambiar(venta.id(), new VentaCambiarLotesRequest(
                lotes, mensualidad == null ? null : new BigDecimal(mensualidad), null, CONTRASENA));
        em.flush();
        return v;
    }

    private static VentaLoteItemRequest item(Lote l, String precio) {
        return new VentaLoteItemRequest(l.getId(), new BigDecimal(precio));
    }

    private EstadoLote estadoDe(Lote l) {
        em.flush();
        em.clear();
        return lotes.findById(l.getId()).orElseThrow().getEstado();
    }

    @Test
    void sustituirLiberaElViejoMarcaElNuevoYRecalculaLaComision() {
        VentaDto v = cambiar(List.of(item(nuevo, "150000")), "2500");
        assertEquals(0, new BigDecimal("150000").compareTo(v.precioVenta()));
        assertEquals(0, new BigDecimal("2500").compareTo(v.mensualidad()));
        assertEquals(1, v.lotes().size());
        VentaComision c = comisiones.findByVentaIdAndSecundariaFalse(venta.id()).orElseThrow();
        assertEquals(0, new BigDecimal("150000").compareTo(c.getBase()));
        assertEquals(0, new BigDecimal("7500").compareTo(c.getMonto()));
        assertEquals(EstadoLote.DISPONIBLE, estadoDe(viejo));
        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, estadoDe(nuevo));
    }

    @Test
    void agregarUnLoteConservaElAnterior() {
        VentaDto v = cambiar(List.of(item(viejo, "100000"), item(nuevo, "90000")), "3000");
        assertEquals(2, v.lotes().size());
        assertEquals(0, new BigDecimal("190000").compareTo(v.precioVenta()));
        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, estadoDe(viejo));
        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, estadoDe(nuevo));
    }

    @Test
    void precioMenorQueLoAbonadoSePermiteConSaldoCero() {
        reciboVentaService.registrarPago(venta.id(), new PagoVentaCreateRequest(LocalDate.now(), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, new BigDecimal("50000"), null, null, null))));
        em.flush();
        VentaDto v = cambiar(List.of(item(nuevo, "40000")), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(v.saldoPendiente()));
        assertNull(v.promocionNombre());
    }

    @Test
    void pideLaContrasena() {
        assertThrows(ForbiddenOperationException.class, () -> cambio.cambiar(venta.id(),
                new VentaCambiarLotesRequest(List.of(item(nuevo, "1000")), null, null, "otra")));
    }

    @Test
    void noAceptaLotesDeOtroDesarrolloNiDeOtraVenta() {
        Lote nanuu = lote(nanuu(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        assertThrows(ValidationException.class, () -> cambiar(List.of(item(nanuu, "1000")), null));
        VentaDto otra = crearVenta(usuario(Role.ASESOR), nuevo, "100000", null, null, null, LocalDate.now());
        assertTrue(otra.id() != null);
        assertThrows(ConflictException.class, () -> cambiar(List.of(item(nuevo, "1000")), null));
    }
}
