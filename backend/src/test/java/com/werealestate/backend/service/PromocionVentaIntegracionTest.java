package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.PromocionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Registrar una venta con una promoción de bono (contra la BD local, en una transacción que se revierte). */
class PromocionVentaIntegracionTest extends PruebaDeIntegracion {

    @Autowired PromocionRepository promociones;
    @Autowired PromocionVentaService promocionVenta;

    private Usuario asesor;

    @BeforeEach
    void preparar() {
        iniciarSesionComo(usuario(Role.ADMIN));
        asesor = usuario(Role.ASESOR);
    }

    private Promocion promocion(boolean activa, LocalDateTime fin) {
        Promocion p = new Promocion();
        p.setNombre("Promo " + etiqueta());
        p.setProyecto("samai");
        p.setTipoPrecio(TipoPrecioPromocion.LOTE);
        p.setModalidad(ModalidadPromocion.BONO_LOTES);
        p.setBonos(java.util.Map.of(1, new BigDecimal("10000"), 2, new BigDecimal("20000"), 3, new BigDecimal("30000")));
        p.setPlazoFijo(60);
        p.setActiva(activa);
        p.setFechaFin(fin);
        return promociones.saveAndFlush(p);
    }

    private VentaCreateRequest venta(Long promocionId, LocalDate fecha, String superficie) {
        var lote = lote(samai(), etiqueta(), "1", superficie, EstadoLote.DISPONIBLE);
        var cliente = cliente("Comprador " + etiqueta());
        return new VentaCreateRequest(
                List.of(new VentaLoteItemRequest(lote.getId(), new BigDecimal("90000"))), cliente.getId(), null, null,
                asesor.getId(), null, "Sin enganche · 60 MSI", fecha, new BigDecimal("1500"), 60, null, null,
                fecha.getDayOfMonth(), true, null, null, false, null, null, null, null, null, null, null, null,
                promocionId);
    }

    @Test
    void guardaElNombreYElBonoSegunElNumeroDeLotes() {
        Promocion p = promocion(true, LocalDateTime.now().plusDays(5));
        VentaDto dto = ventaService.crear(venta(p.getId(), LocalDate.now(), "200"));
        assertEquals(p.getNombre(), dto.promocionNombre());
        assertEquals(0, new BigDecimal("10000").compareTo(dto.promocionBono()));
    }

    @Test
    void sinPromocionQuedaVacio() {
        VentaDto dto = ventaService.crear(venta(null, LocalDate.now(), "200"));
        assertNull(dto.promocionNombre());
        assertNull(dto.promocionBono());
    }

    @Test
    void unaVencidaSirveParaVentasDeAntesDeSuFinPeroNoDeDespues() {
        Promocion p = promocion(false, LocalDateTime.now().minusDays(3));
        LocalDate antes = LocalDate.now().minusDays(10);
        assertTrue(promocionVenta.paraVenta(antes, false).stream().anyMatch(d -> d.id().equals(p.getId())));
        assertEquals(p.getNombre(), ventaService.crear(venta(p.getId(), antes, "200")).promocionNombre());
        assertFalse(promocionVenta.paraVenta(LocalDate.now(), false).stream().anyMatch(d -> d.id().equals(p.getId())));
        assertThrows(ValidationException.class, () -> ventaService.crear(venta(p.getId(), LocalDate.now(), "200")));
    }

    @Test
    void unaApagadaAManoAntesDeVencerNoSePuedeUsar() {
        Promocion p = promocion(false, LocalDateTime.now().plusDays(5));
        assertThrows(ValidationException.class, () -> ventaService.crear(venta(p.getId(), LocalDate.now(), "200")));
    }

    @Test
    void unaDeLotesNoAplicaAHectareas() {
        Promocion p = promocion(true, LocalDateTime.now().plusDays(5));
        assertThrows(ValidationException.class, () -> ventaService.crear(venta(p.getId(), LocalDate.now(), "9000")));
    }
}
