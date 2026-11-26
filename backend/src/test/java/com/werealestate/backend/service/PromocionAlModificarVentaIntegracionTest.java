package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.werealestate.backend.dto.VentaAportacionItemRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaUpdateRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.repository.PromocionRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Poner, cambiar y quitar la promoción al modificar una venta (contra la BD local, en una transacción que se revierte). */
class PromocionAlModificarVentaIntegracionTest extends PruebaDeIntegracion {

    @Autowired PromocionRepository promociones;
    @Autowired VentaComisionRepository comisiones;
    @Autowired EntityManager em;
    @Autowired PromocionVentaService promocionVenta;

    private Usuario asesor;
    private VentaDto venta;
    private LocalDate fecha;

    @BeforeEach
    void preparar() {
        iniciarSesionComo(usuario(Role.ADMIN));
        asesor = usuario(Role.ASESOR);
        fecha = LocalDate.now().minusDays(3);
        Lote a = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        venta = crearVenta(asesor, a, "160000", null, "2000", 60, fecha);
        em.flush();
    }

    private Promocion promocion(String bonoUnLote) {
        Promocion p = new Promocion();
        p.setNombre("Promo " + etiqueta());
        p.setProyecto("samai");
        p.setTipoPrecio(TipoPrecioPromocion.LOTE);
        p.setModalidad(ModalidadPromocion.BONO_LOTES);
        p.setBonoUnLote(new BigDecimal(bonoUnLote));
        p.setBonoDosLotes(new BigDecimal("20000"));
        p.setBonoTresLotes(new BigDecimal("30000"));
        p.setPlazoFijo(60);
        p.setActiva(true);
        p.setFechaFin(LocalDateTime.now().plusDays(5));
        return promociones.saveAndFlush(p);
    }

    private VentaDto modificar(Long promocionId, String password, String mensualidad) {
        VentaDto v = ventaService.actualizar(venta.id(), new VentaUpdateRequest(
                null, null, null, asesor.getId(), null, venta.formaPago(), fecha, new BigDecimal(mensualidad), 60,
                null, null, null, null, null, null, null, true, promocionId, password, null));
        em.flush();
        return v;
    }

    private Promocion mensualidadFija() {
        Promocion p = new Promocion();
        p.setNombre("Fija " + etiqueta());
        p.setProyecto("samai");
        p.setTipoPrecio(TipoPrecioPromocion.LOTE);
        p.setModalidad(ModalidadPromocion.MENSUALIDAD_FIJA);
        p.setMensualidadFija(new BigDecimal("1900"));
        p.setActiva(true);
        p.setFechaFin(LocalDateTime.now().plusDays(5));
        return promociones.saveAndFlush(p);
    }

    @Test
    void mensualidadFijaNoCambiaElPrecioYReemplazaLasAportaciones() {
        Promocion p = mensualidadFija();
        YearMonth mes = YearMonth.from(fecha).plusMonths(6);
        VentaDto v = ventaService.actualizar(venta.id(), new VentaUpdateRequest(
                null, null, null, asesor.getId(), null, venta.formaPago(), fecha, new BigDecimal("1900"), 60,
                null, null, null, null, null, null, null, true, p.getId(), CONTRASENA,
                List.of(new VentaAportacionItemRequest(mes.getYear(), mes.getMonthValue(), new BigDecimal("50000")))));
        em.flush();
        assertEquals(p.getNombre(), v.promocionNombre());
        assertNull(v.promocionBono());
        assertEquals(0, new BigDecimal("1900").compareTo(v.promocionMensualidadFija()));
        assertEquals(0, new BigDecimal("160000").compareTo(v.precioVenta()));
        assertEquals(0, new BigDecimal("1900").compareTo(v.mensualidad()));
        assertEquals(1, v.aportaciones().size());
        assertEquals(0, new BigDecimal("50000").compareTo(v.aportaciones().get(0).monto()));
    }

    @Test
    void mensualidadFijaNoSePuedeEnNuevaVenta() {
        Promocion p = mensualidadFija();
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        assertThrows(ValidationException.class, () -> promocionVenta.aplicar(p.getId(), fecha, List.of(lote)));
    }

    @Test
    void ponerLaPromocionRestaElBonoDelPrecioYRecalculaLaComision() {
        Promocion p = promocion("10000");
        VentaDto v = modificar(p.getId(), CONTRASENA, "2500");
        assertEquals(p.getNombre(), v.promocionNombre());
        assertEquals(0, new BigDecimal("150000").compareTo(v.precioVenta()));
        assertEquals(0, new BigDecimal("2500").compareTo(v.mensualidad()));
        VentaComision c = comisiones.findByVentaIdAndSecundariaFalse(venta.id()).orElseThrow();
        assertEquals(0, new BigDecimal("150000").compareTo(c.getBase()));
    }

    @Test
    void cambiarLaPromocionDevuelveElBonoAnteriorYQuitarlaRegresaElPrecio() {
        modificar(promocion("10000").getId(), CONTRASENA, "2500");
        VentaDto cambiada = modificar(promocion("15000").getId(), CONTRASENA, "2416.67");
        assertEquals(0, new BigDecimal("145000").compareTo(cambiada.precioVenta()));
        VentaDto sin = modificar(null, CONTRASENA, "2666.67");
        assertNull(sin.promocionNombre());
        assertEquals(0, new BigDecimal("160000").compareTo(sin.precioVenta()));
    }

    @Test
    void pideLaContrasena() {
        Long id = promocion("10000").getId();
        assertThrows(ForbiddenOperationException.class, () -> modificar(id, "otra", "2500"));
    }

    @Test
    void repartoDelBonoEnPartesIguales() {
        var partes = VentaService.partesDelBono(new BigDecimal("10000.01"), 3);
        assertEquals(0, new BigDecimal("3333.33").compareTo(partes.get(0)));
        assertEquals(0, new BigDecimal("3333.35").compareTo(partes.get(2)));
        assertEquals(0, BigDecimal.ZERO.compareTo(VentaService.partesDelBono(null, 2).get(1)));
    }
}
