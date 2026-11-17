package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.ReasignarVariosLeadsRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Etiqueta;
import com.werealestate.backend.model.EtiquetaColor;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EtiquetaRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.service.LeadService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reasignación masiva de leads y alcance de Administración (LIDER_AREA) sobre los leads de todos, contra la BD real. */
class LeadReasignarVariosIntegracionTest extends PruebaDeIntegracion {

    @Autowired LeadService leadService;
    @Autowired LeadRepository leads;
    @Autowired EtiquetaRepository etiquetas;
    @Autowired EntityManager em;

    private Lead lead(Usuario asesor, long numero, String nombre) {
        Lead l = new Lead(nombre, "9510000000", null, "Prueba", samai(), asesor, null);
        l.setNumero(numero);
        return leads.saveAndFlush(l);
    }

    private Lead recargar(Lead l) {
        em.clear();
        return leads.findById(l.getId()).orElseThrow();
    }

    @Test
    void reasignaVariosConNumerosConsecutivosYQuitaEtiquetas() {
        Usuario origen = usuario(Role.ASESOR);
        Usuario destino = usuario(Role.ASESOR);
        lead(destino, 7, "Ya del destino");
        Lead a = lead(origen, 1, "A");
        Lead b = lead(origen, 2, "B");
        Lead c = lead(origen, 3, "C");
        Etiqueta et = etiquetas.saveAndFlush(new Etiqueta("Caliente", EtiquetaColor.values()[0], origen));
        a.getEtiquetas().add(et);
        leads.saveAndFlush(a);
        iniciarSesionComo(usuario(Role.ADMIN));

        int n = leadService.reasignarVarios(
                new ReasignarVariosLeadsRequest(List.of(a.getId(), b.getId(), c.getId(), a.getId()), destino.getId()));

        assertEquals(3, n);
        List<Long> numeros = List.of(recargar(a).getNumero(), recargar(b).getNumero(), recargar(c).getNumero());
        assertEquals(List.of(8L, 9L, 10L), numeros);
        Lead ra = recargar(a);
        assertEquals(destino.getId(), ra.getAsesor().getId());
        assertTrue(ra.getEtiquetas().isEmpty());
    }

    @Test
    void losQueYaSonDelDestinoSeDejanIgual() {
        Usuario destino = usuario(Role.ASESOR);
        Usuario origen = usuario(Role.ASESOR);
        Lead propio = lead(destino, 4, "Propio");
        Lead ajeno = lead(origen, 1, "Ajeno");
        iniciarSesionComo(usuario(Role.ADMIN));

        int n = leadService.reasignarVarios(new ReasignarVariosLeadsRequest(List.of(propio.getId(), ajeno.getId()), destino.getId()));

        assertEquals(1, n);
        assertEquals(4L, recargar(propio).getNumero());
        assertEquals(5L, recargar(ajeno).getNumero());
    }

    @Test
    void siUnIdNoExisteNoSeCambiaNinguno() {
        Usuario origen = usuario(Role.ASESOR);
        Usuario destino = usuario(Role.ASESOR);
        Lead a = lead(origen, 1, "A");
        iniciarSesionComo(usuario(Role.ADMIN));

        assertThrows(ResourceNotFoundException.class, () ->
                leadService.reasignarVarios(new ReasignarVariosLeadsRequest(List.of(a.getId(), -999L), destino.getId())));
        assertEquals(origen.getId(), recargar(a).getAsesor().getId());
    }

    @Test
    void unAsesorNoPuedeReasignar() {
        Usuario asesor = usuario(Role.ASESOR);
        Lead a = lead(asesor, 1, "A");
        iniciarSesionComo(asesor);

        assertThrows(ForbiddenOperationException.class, () ->
                leadService.reasignarVarios(new ReasignarVariosLeadsRequest(List.of(a.getId()), asesor.getId())));
    }

    @Test
    void administracionPuedeReasignarVarios() {
        Usuario origen = usuario(Role.ASESOR);
        Usuario destino = usuario(Role.ASESOR);
        Lead a = lead(origen, 1, "A");
        iniciarSesionComo(usuario(Role.LIDER_AREA));

        assertEquals(1, leadService.reasignarVarios(new ReasignarVariosLeadsRequest(List.of(a.getId()), destino.getId())));
        assertEquals(destino.getId(), recargar(a).getAsesor().getId());
    }

    @Test
    void administracionAbreYEditaLeadsAjenosPeroPipelineSoloMuestraLosSuyos() {
        Usuario asesor = usuario(Role.ASESOR);
        Usuario administracion = usuario(Role.LIDER_AREA);
        Lead ajeno = lead(asesor, 1, "Ajeno " + etiqueta());
        Lead propio = lead(administracion, 1, "Propio " + etiqueta());
        iniciarSesionComo(administracion);

        assertEquals(ajeno.getId(), leadService.obtener(ajeno.getId()).id());
        // GET /api/leads es lo que usa el Pipeline: para Administración sigue siendo solo lo suyo (el resumen igual).
        List<Long> delPipeline = leadService.listar().stream().map(LeadDto::id).toList();
        assertTrue(delPipeline.contains(propio.getId()));
        assertTrue(!delPipeline.contains(ajeno.getId()));
        // las alertas de fríos de todo el equipo también son suyas ahora
        leadService.listarFrios();
    }

    @Test
    void administracionNoPuedeBorrarLeads() {
        Usuario asesor = usuario(Role.ASESOR);
        Lead a = lead(asesor, 1, "A");
        iniciarSesionComo(usuario(Role.LIDER_AREA));

        assertThrows(ForbiddenOperationException.class, () -> leadService.eliminar(a.getId()));
    }
}
