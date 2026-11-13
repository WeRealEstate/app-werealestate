package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.service.LeadService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Caracterización de {@link LeadService#buscarPaginado} contra la BD real (transacción que se revierte): búsqueda por
 * texto y por número, estados, fríos, archivados y alcance por rol. Cada prueba usa asesores propios para que los leads
 * reales de la BD no entren en los resultados. */
class LeadBuscarPaginadoIntegracionTest extends PruebaDeIntegracion {

    @Autowired LeadService leadService;
    @Autowired LeadRepository leads;

    private Lead lead(Usuario asesor, long numero, String nombre, String telefono) {
        Lead l = new Lead(nombre, telefono, null, "Prueba", samai(), asesor, null);
        l.setNumero(numero);
        return leads.saveAndFlush(l);
    }

    private PaginaDto<LeadDto> buscar(String busqueda, String estado, Long asesorId, boolean archivados) {
        return leadService.buscarPaginado(busqueda, estado, asesorId, null, archivados, 0, 100);
    }

    private static List<Long> numeros(PaginaDto<LeadDto> pagina) {
        return pagina.contenido().stream().map(LeadDto::numero).toList();
    }

    // ---------------------------------------------------------------- búsqueda

    @Test
    void buscaTextoEnNombreOTelefonoSinImportarMayusculas() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        String tag = etiqueta();
        lead(asesor, 1, "Maria " + tag, "9511110000");
        lead(asesor, 2, "Pedro", "951" + tag.substring(0, 4) + "99");
        lead(asesor, 3, "Otro", "9512220000");

        assertEquals(List.of(1L), numeros(buscar(tag.toLowerCase(), null, null, false)));
        assertEquals(List.of(2L), numeros(buscar(tag.substring(0, 4) + "99", null, null, false)));
        assertEquals(List.of(), numeros(buscar("no-existe-" + tag, null, null, false)));
    }

    @Test
    void textoEnBlancoNoFiltraYSeRecortan() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        String tag = etiqueta();
        lead(asesor, 1, "Ana " + tag, "1");
        lead(asesor, 2, "Beto", "2");

        assertEquals(2, buscar("   ", null, null, false).contenido().size());
        assertEquals(List.of(1L), numeros(buscar("  " + tag + "  ", null, null, false)));
    }

    @Test
    void unNumeroSoloBuscaTambienElLeadConEseNumero() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 12, "Sin pista", "5550001");
        lead(asesor, 13, "Otro cualquiera", "5550002");
        lead(asesor, 14, "Cliente 12 de prueba", "5550003"); // el "12" está en su nombre

        // "12": el lead número 12 y los que contienen "12" en nombre o teléfono
        assertEquals(List.of(12L, 14L), numeros(buscar("12", null, null, false)).stream().sorted().toList());
        // "#12": el lead número 12, pero el texto "12" suelto ya no coincide (el texto buscado lleva la almohadilla)
        assertEquals(List.of(12L), numeros(buscar("#12", null, null, false)));
    }

    @Test
    void conAlmohadillaTambienCoincideElTextoLiteralConAlmohadilla() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 12, "Uno", "5550001");
        lead(asesor, 20, "Dos #12", "5550002");

        // OJO: comportamiento actual: "#12" busca el número 12 y además el texto literal "#12" (el comodín lleva la almohadilla),
        // así que "Dos #12" aparece por su nombre y "12" solo por su número.
        assertEquals(List.of(12L, 20L), numeros(buscar("#12", null, null, false)).stream().sorted().toList());
    }

    @Test
    void numeroAjenoOConLetrasNoBuscaPorNumero() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 7, "Abc", "5550001");

        assertEquals(List.of(), numeros(buscar("#8", null, null, false)));
        // "7a" no es un número puro: solo busca texto
        assertEquals(List.of(), numeros(buscar("7a", null, null, false)));
        assertEquals(List.of(7L), numeros(buscar("7", null, null, false)));
    }

    @Test
    void numeroLargoDeMasDeQuinceDigitosSeTrataComoTextoYNoRevienta() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 1, "Abc", "5550001");

        assertEquals(List.of(), numeros(buscar("1234567890123456", null, null, false)));
    }

    // ---------------------------------------------------------------- estados

    @Test
    void estadoFiltraPorIgualdadYNombreInvalidoLanzaIllegalArgument() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 1, "Nuevo", "1");
        Lead interesado = lead(asesor, 2, "Interesado", "2");
        interesado.setEstado(EstadoLead.INTERESADO);
        leads.saveAndFlush(interesado);

        assertEquals(List.of(2L), numeros(buscar(null, "INTERESADO", null, false)));
        assertEquals(List.of(1L), numeros(buscar(null, "NUEVO", null, false)));
        assertEquals(List.of(1L, 2L), numeros(buscar(null, "", null, false)).stream().sorted().toList());
        assertThrows(IllegalArgumentException.class, () -> buscar(null, "NO_EXISTE", null, false));
    }

    @Test
    void friosSonLosAbiertosConMasDeLosDiasSinContactoYNoIncluyenLosCerrados() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 1, "Reciente", "1");
        Lead viejo = lead(asesor, 2, "Viejo abierto", "2");
        viejo.setFechaUltimoContacto(LocalDateTime.now().minusDays(60));
        Lead viejoGanado = lead(asesor, 3, "Viejo ganado", "3");
        viejoGanado.setFechaUltimoContacto(LocalDateTime.now().minusDays(60));
        viejoGanado.setEstado(EstadoLead.CERRADO_GANADO);
        Lead viejoPerdido = lead(asesor, 4, "Viejo perdido", "4");
        viejoPerdido.setFechaUltimoContacto(LocalDateTime.now().minusDays(60));
        viejoPerdido.setEstado(EstadoLead.CERRADO_PERDIDO);
        leads.saveAllAndFlush(List.of(viejo, viejoGanado, viejoPerdido));

        PaginaDto<LeadDto> frios = buscar(null, "FRIOS", null, false);
        assertEquals(List.of(2L), numeros(frios));
        assertTrue(frios.contenido().get(0).frio());
        // "frios" en minúsculas también vale
        assertEquals(List.of(2L), numeros(buscar(null, "frios", null, false)));
    }

    @Test
    void ordenaPorUltimoContactoDelMasRecienteAlMasAntiguo() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        Lead a = lead(asesor, 1, "A", "1");
        a.setFechaUltimoContacto(LocalDateTime.now().minusDays(3));
        Lead b = lead(asesor, 2, "B", "2");
        b.setFechaUltimoContacto(LocalDateTime.now().minusDays(1));
        Lead c = lead(asesor, 3, "C", "3");
        c.setFechaUltimoContacto(LocalDateTime.now().minusDays(2));
        leads.saveAllAndFlush(List.of(a, b, c));

        assertEquals(List.of(2L, 3L, 1L), numeros(buscar(null, null, null, false)));
    }

    @Test
    void archivadosYActivosSonListasSeparadas() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        lead(asesor, 1, "Activo", "1");
        Lead archivado = lead(asesor, 2, "Archivado", "2");
        archivado.setArchivado(true);
        leads.saveAndFlush(archivado);

        assertEquals(List.of(1L), numeros(buscar(null, null, null, false)));
        assertEquals(List.of(2L), numeros(buscar(null, null, null, true)));
    }

    @Test
    void paginaConHayMas() {
        Usuario asesor = usuario(Role.ASESOR);
        iniciarSesionComo(asesor);
        for (long i = 1; i <= 3; i++) lead(asesor, i, "L" + i, "" + i);

        PaginaDto<LeadDto> primera = leadService.buscarPaginado(null, null, null, null, false, 0, 2);
        assertEquals(2, primera.contenido().size());
        assertTrue(primera.hayMas());
        PaginaDto<LeadDto> segunda = leadService.buscarPaginado(null, null, null, null, false, 1, 2);
        assertEquals(1, segunda.contenido().size());
        assertFalse(segunda.hayMas());
    }

    // ---------------------------------------------------------------- alcance por rol

    @Test
    void asesorSoloVeSusLeadsEIgnoraElFiltroDeAsesor() {
        Usuario yo = usuario(Role.ASESOR);
        Usuario otro = usuario(Role.ASESOR);
        lead(yo, 1, "Mio", "1");
        lead(otro, 1, "Ajeno", "2");
        iniciarSesionComo(yo);

        assertEquals(List.of("Mio"), buscar(null, null, null, false).contenido().stream().map(LeadDto::nombreCliente).toList());
        // aunque pida el asesor ajeno, sigue viendo solo los suyos
        assertEquals(List.of("Mio"), buscar(null, null, otro.getId(), false).contenido().stream().map(LeadDto::nombreCliente).toList());
    }

    @Test
    void adminVeLosLeadsDeTodosYPuedeFiltrarPorAsesor() {
        Usuario asesorA = usuario(Role.ASESOR);
        Usuario asesorB = usuario(Role.ASESOR);
        String tag = etiqueta();
        lead(asesorA, 1, "De A " + tag, "1");
        lead(asesorB, 1, "De B " + tag, "2");
        iniciarSesionComo(usuario(Role.ADMIN));

        assertEquals(2, buscar(tag, null, null, false).contenido().size());
        assertEquals(List.of("De B " + tag), buscar(tag, null, asesorB.getId(), false).contenido().stream().map(LeadDto::nombreCliente).toList());
    }

    @Test
    void administracionSoloVeSusPropiosLeadsComoUnAsesor() {
        Usuario asesor = usuario(Role.ASESOR);
        Usuario administracion = usuario(Role.LIDER_AREA);
        String tag = etiqueta();
        lead(asesor, 1, "Del asesor " + tag, "1");
        lead(administracion, 1, "De administracion " + tag, "2");
        iniciarSesionComo(administracion);

        // OJO: comportamiento actual: solo ADMIN ve todos los leads; LIDER_AREA queda con el alcance de un asesor.
        assertEquals(List.of("De administracion " + tag),
                buscar(tag, null, null, false).contenido().stream().map(LeadDto::nombreCliente).toList());
    }

    @Test
    void numeroEnBusquedaSoloAplicaDentroDelAlcanceDelAsesor() {
        Usuario yo = usuario(Role.ASESOR);
        Usuario otro = usuario(Role.ASESOR);
        lead(yo, 5, "Mio", "1");
        lead(otro, 5, "Ajeno", "2");
        iniciarSesionComo(yo);

        assertEquals(List.of("Mio"), buscar("#5", null, null, false).contenido().stream().map(LeadDto::nombreCliente).toList());
    }
}
