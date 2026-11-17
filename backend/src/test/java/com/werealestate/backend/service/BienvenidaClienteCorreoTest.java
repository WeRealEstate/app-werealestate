package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Contenido de la bienvenida de SAMAI: nombre escapado, negritas, enlaces de redes e imágenes incrustadas. */
class BienvenidaClienteCorreoTest {

    @Test
    void elNombreVaEnNegritasYEscapado() {
        var c = new BienvenidaClienteCorreo.Contenido("Ana <b>&\"Ñ\"");
        String html = BienvenidaClienteCorreo.html(c);
        assertTrue(html.contains("Estimado(a) <strong>Ana &lt;b&gt;&amp;&quot;&Ntilde;&quot;:</strong>")
                || html.contains("Estimado(a) <strong>Ana &lt;b&gt;&amp;&quot;Ñ&quot;:</strong>"), html);
        assertFalse(html.contains("<b>&"), "no deja pasar HTML del nombre");
        assertTrue(BienvenidaClienteCorreo.texto(c).contains("Estimado(a) Ana <b>&\"Ñ\":"));
    }

    @Test
    void llevaLosTextosFijosLosEnlacesYSinRastrosDeMailchimp() {
        String html = BienvenidaClienteCorreo.html(new BienvenidaClienteCorreo.Contenido("Laura"));
        assertTrue(html.contains("<strong>We Real Estate</strong>"));
        assertTrue(html.contains("<strong>SAMAI CAMPESTRE</strong>"));
        assertTrue(html.contains("<strong>Equipo We Real Estate.</strong>"));
        assertTrue(html.contains("https://www.facebook.com/share/1Jak98kFTh/"));
        assertTrue(html.contains("https://youtube.com/@werealestatemx?si=itL90TcjCqXcgrnK"));
        assertTrue(html.contains("Gracias por confiar en We Real Estate y formar parte de SAMAI Campestre."));
        assertFalse(html.contains("View this email"));
        assertFalse(html.contains("*|"));
        assertFalse(html.contains("UNIQID"));
    }

    @Test
    void todasLasImagenesExistenEnResourcesYSeUsanConSuCid() {
        String html = BienvenidaClienteCorreo.html(new BienvenidaClienteCorreo.Contenido("Laura"));
        assertEquals(6, BienvenidaClienteCorreo.IMAGENES.size());
        for (var i : BienvenidaClienteCorreo.IMAGENES) {
            assertNotNull(BienvenidaClienteCorreoTest.class.getClassLoader().getResource(i.recurso()), i.recurso());
            assertTrue(html.contains("cid:" + i.cid()), i.cid());
        }
    }
}
