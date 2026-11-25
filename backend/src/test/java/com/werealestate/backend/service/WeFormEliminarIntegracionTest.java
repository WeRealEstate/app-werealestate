package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Eliminar un formulario con sus respuestas (contra la BD local, en una transacción que se revierte). */
class WeFormEliminarIntegracionTest extends PruebaDeIntegracion {

    @Autowired WeFormService formService;
    @Autowired EntityManager em;

    private long crearFormularioConRespuesta() {
        long desarrollo = samai().getId();
        long formId = ((Number) em.createNativeQuery("insert into we_form (nombre, slug, desarrollo_id) values ('Prueba', :s, :d) returning id")
                .setParameter("s", "prueba-" + etiqueta()).setParameter("d", desarrollo).getSingleResult()).longValue();
        long preguntaId = ((Number) em.createNativeQuery("insert into we_form_pregunta (form_id, orden, tipo, titulo) values (:f, 1, 'CORTA', 'Nombre') returning id")
                .setParameter("f", formId).getSingleResult()).longValue();
        long respuestaId = ((Number) em.createNativeQuery("insert into we_form_respuesta (form_id, asesor_nombre) values (:f, 'Asesor') returning id")
                .setParameter("f", formId).getSingleResult()).longValue();
        em.createNativeQuery("insert into we_form_respuesta_item (respuesta_id, pregunta_id, pregunta_titulo, pregunta_tipo, valor, orden) "
                + "values (:r, :p, 'Nombre', 'CORTA', 'Juan', 1)")
                .setParameter("r", respuestaId).setParameter("p", preguntaId).executeUpdate();
        return formId;
    }

    private long contar(String tabla, long formId) {
        String sql = switch (tabla) {
            case "we_form" -> "select count(*) from we_form where id = :f";
            case "we_form_pregunta" -> "select count(*) from we_form_pregunta where form_id = :f";
            default -> "select count(*) from we_form_respuesta where form_id = :f";
        };
        return ((Number) em.createNativeQuery(sql).setParameter("f", formId).getSingleResult()).longValue();
    }

    @Test
    void elAdminLoEliminaConSusPreguntasYRespuestas() {
        iniciarSesionComo(usuario(Role.ADMIN));
        long formId = crearFormularioConRespuesta();
        formService.eliminar(formId, CONTRASENA);
        em.flush();
        assertEquals(0, contar("we_form", formId));
        assertEquals(0, contar("we_form_pregunta", formId));
        assertEquals(0, contar("we_form_respuesta", formId));
    }

    @Test
    void noSinContrasenaNiSinSerAdmin() {
        iniciarSesionComo(usuario(Role.ADMIN));
        long formId = crearFormularioConRespuesta();
        assertThrows(ForbiddenOperationException.class, () -> formService.eliminar(formId, "otra"));
        Usuario lider = usuario(Role.LIDER_AREA);
        iniciarSesionComo(lider);
        assertThrows(ForbiddenOperationException.class, () -> formService.eliminar(formId, CONTRASENA));
        assertFalse(contar("we_form", formId) == 0);
    }
}
