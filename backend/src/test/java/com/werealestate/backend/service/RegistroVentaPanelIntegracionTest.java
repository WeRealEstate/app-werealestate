package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** "Registrar venta" desde el panel del asesor interno (contra la BD local, en una transacción que se revierte). */
class RegistroVentaPanelIntegracionTest extends PruebaDeIntegracion {

    @Autowired WeFormService formService;
    @Autowired RegistroVentaPanelService panel;

    @Test
    void elAsesorVeLosFormulariosActivosYElSuyoSinPin() {
        iniciarSesionComo(usuario(Role.ADMIN));
        var form = formService.crear(new WeFormDtos.CrearRequest("Prueba " + etiqueta(), null, samai().getId()));
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        assertTrue(panel.listar().stream().anyMatch(f -> f.slug().equals(form.slug()) && "samai".equals(f.proyecto())));
        assertEquals(asesor.getNombre(), panel.obtener(form.slug()).asesorNombre());
    }

    @Test
    void soloAsesoresYElModuloEsDeEllos() {
        iniciarSesionComo(usuario(Role.ADMIN));
        assertThrows(ForbiddenOperationException.class, () -> panel.listar());
        iniciarSesionComo(usuario(Role.LIDER_AREA));
        assertThrows(ForbiddenOperationException.class, () -> panel.listar());
        assertTrue(ModulosAcceso.porDefecto(Role.ASESOR).contains(Modulo.REGISTRAR_VENTA));
        assertTrue(!ModulosAcceso.maximos(Role.LIDER_AREA).contains(Modulo.REGISTRAR_VENTA));
    }
}
