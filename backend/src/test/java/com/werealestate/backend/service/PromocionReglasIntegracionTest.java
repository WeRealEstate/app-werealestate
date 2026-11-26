package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.repository.PromocionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reglas de la pantalla de Promociones (12.17.0): solo admin, sin renombrar y fecha de inicio. */
class PromocionReglasIntegracionTest extends PruebaDeIntegracion {

    @Autowired PromocionService servicio;
    @Autowired PromocionRepository promociones;
    @Autowired EntityManager em;

    @BeforeEach
    void preparar() {
        iniciarSesionComo(usuario(Role.ADMIN));
        // Que las de otras pruebas o datos locales no apaguen ni cuenten aquí: cada prueba usa un proyecto propio.
    }

    private PromocionCreateRequest bono(String nombre, LocalDateTime inicio, LocalDateTime fin) {
        return new PromocionCreateRequest(nombre, "nanuu", TipoPrecioPromocion.LOTE, ModalidadPromocion.BONO_LOTES, null,
                null, new BigDecimal("20000"), new BigDecimal("30000"), 60, null, true, fin, inicio);
    }

    @Test
    void soloAdminPuedeCrearEditarActivarYEliminar() {
        PromocionDto p = servicio.crear(bono("Promo " + etiqueta(), null, null));
        iniciarSesionComo(usuario(Role.LIDER_AREA));
        assertThrows(ForbiddenOperationException.class, () -> servicio.crear(bono("Otra " + etiqueta(), null, null)));
        assertThrows(ForbiddenOperationException.class, () -> servicio.cambiarEstado(p.id(), false));
        assertThrows(ForbiddenOperationException.class, () -> servicio.eliminar(p.id()));
        iniciarSesionComo(usuario(Role.ASESOR));
        assertThrows(ForbiddenOperationException.class, () -> servicio.actualizar(p.id(), new PromocionUpdateRequest(
                null, null, null, null, null, null, null, null, null, "x", null, null, null, null)));
    }

    @Test
    void elNombreNoSeCambia() {
        PromocionDto p = servicio.crear(bono("Promo " + etiqueta(), null, null));
        assertThrows(ValidationException.class, () -> servicio.actualizar(p.id(), new PromocionUpdateRequest(
                "Otro nombre", null, null, null, null, null, null, null, null, null, null, null, null, null)));
        // Mandar el mismo nombre (el formulario lo manda siempre) sí se acepta.
        servicio.actualizar(p.id(), new PromocionUpdateRequest(
                p.nombre(), null, null, null, null, null, null, null, null, "nueva", null, null, null, null));
    }

    @Test
    void sinFechaDeInicioEmpiezaAlCrearla() {
        PromocionDto p = servicio.crear(bono("Promo " + etiqueta(), null, null));
        assertTrue(p.activa());
        assertFalse(p.programada());
        assertTrue(p.fechaInicio() != null && !p.fechaInicio().isAfter(LocalDateTime.now()));
    }

    @Test
    void conInicioFuturoQuedaProgramadaYSeActivaSolaApagandoALaAnterior() {
        PromocionDto vieja = servicio.crear(bono("Vieja " + etiqueta(), null, null));
        PromocionDto nueva = servicio.crear(bono("Nueva " + etiqueta(), LocalDateTime.now().plusDays(2), null));
        assertFalse(nueva.activa());
        assertTrue(nueva.programada());
        assertTrue(promociones.findById(vieja.id()).orElseThrow().isActiva());

        // Llega su fecha de inicio.
        var entidad = promociones.findById(nueva.id()).orElseThrow();
        entidad.setFechaInicio(LocalDateTime.now().minusMinutes(1));
        promociones.saveAndFlush(entidad);
        servicio.actualizarVigencias();
        em.flush();
        em.clear();
        assertTrue(promociones.findById(nueva.id()).orElseThrow().isActiva());
        assertTrue(promociones.findById(nueva.id()).orElseThrow().isInicioAplicado());
        assertFalse(promociones.findById(vieja.id()).orElseThrow().isActiva());
    }

    @Test
    void desactivarUnaProgramadaLaCancela() {
        PromocionDto p = servicio.crear(bono("Promo " + etiqueta(), LocalDateTime.now().plusDays(2), null));
        PromocionDto cancelada = servicio.cambiarEstado(p.id(), false);
        assertFalse(cancelada.programada());
        assertFalse(cancelada.activa());
    }

    @Test
    void laFechaDeFinVaDespuesDelInicio() {
        assertThrows(ValidationException.class, () -> servicio.crear(bono("Promo " + etiqueta(),
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(2))));
    }
}
