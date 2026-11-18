package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.controller.LoteController;
import com.werealestate.backend.dto.ApartarVariosPublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.service.LoteApartadoService;
import com.werealestate.backend.service.LoteService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

/** Quién apartó un lote (V90) y qué lotes puede cotizar un asesor además de los disponibles: guardar la clave al apartar
 * (panel y plano público con PIN), limpiarla al salir del apartado, y el filtro del cotizador del panel y del público. */
class LoteApartadoIntegracionTest extends PruebaDeIntegracion {

    @Autowired LoteService loteService;
    @Autowired LoteApartadoService apartadoService;
    @Autowired LoteController controller;
    @Autowired AsesorExternoRepository externos;
    @Autowired MovimientoLoteRepository movimientos;

    /** Un PIN de 4 caracteres que casi seguro no existe en la BD local. */
    private static String pinNuevo() {
        return "Z" + UUID.randomUUID().toString().replace("-", "").substring(0, 3).toUpperCase();
    }

    private Usuario asesorConPin(String nombre, String pin) {
        Usuario u = new Usuario(nombre, "prueba-" + etiqueta().toLowerCase() + "@test.local",
                passwordEncoder.encode(CONTRASENA), Role.ASESOR, null);
        u.setPin(pin);
        return usuarios.saveAndFlush(u);
    }

    private AsesorExterno externoConPin(String nombre, String pin) {
        AsesorExterno e = new AsesorExterno(nombre, "9510000000", null);
        e.setPin(pin);
        e.setContratoEstado(EstadoContratoAsesor.VIGENTE);
        e.setAccesoSamai(true);
        e.setAccesoNanuu(true);
        return externos.saveAndFlush(e);
    }

    /** Cada petición pública viene de una IP distinta para no mezclar los intentos fallidos de unas pruebas con otras. */
    private static MockHttpServletRequest peticion() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("198.51.100." + (1 + (int) (Math.random() * 250)) + "");
        return http;
    }

    private static CambiarEstadoLotePublicoRequest apartarPublico() {
        return new CambiarEstadoLotePublicoRequest(EstadoLote.APARTADO, "Quien atendió", "Cliente", null);
    }

    private Lote recargar(Lote lote) {
        return lotes.findById(lote.getId()).orElseThrow();
    }

    private static List<String> numeros(List<LoteDto> lista, String manzana) {
        return lista.stream().filter(l -> manzana.equals(l.manzana())).map(LoteDto::numeroLote).sorted().toList();
    }

    /** Un movimiento que dejó el lote apartado a nombre de {@code nombreAsesor} (apartados sin clave, de antes de V90). */
    private void movimientoDeApartado(Lote lote, Usuario quien, String nombreAsesor) {
        movimientos.saveAndFlush(new MovimientoLote(lote, EstadoLote.DISPONIBLE, EstadoLote.APARTADO, quien, nombreAsesor,
                "Cliente", null, null));
    }

    // ---------------------------------------------------------------- plano público

    @Test
    void apartarEnElPlanoPublicoConPinGuardaLaClaveDelAsesorInterno() {
        sinSesion();
        Usuario asesor = asesorConPin("Asesor Interno", pinNuevo());
        Lote lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);

        controller.cambiarEstadoPublico(lote.getId(), apartarPublico(), asesor.getPin(), peticion());

        assertEquals(EstadoLote.APARTADO, recargar(lote).getEstado());
        assertEquals("U-" + asesor.getId(), recargar(lote).getApartadoPorClave());
    }

    @Test
    void apartarVariosEnElPlanoPublicoConPinDeAsesorExternoGuardaSuClave() {
        sinSesion();
        AsesorExterno externo = externoConPin("Asesor Externo", pinNuevo());
        String tag = etiqueta();
        Lote a = lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        Lote b = lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);

        controller.apartarVariosPublico(
                new ApartarVariosPublicoRequest(List.of(a.getId(), b.getId()), "Quien atendió", "Cliente", null),
                externo.getPin(), peticion());

        assertEquals("E-" + externo.getId(), recargar(a).getApartadoPorClave());
        assertEquals("E-" + externo.getId(), recargar(b).getApartadoPorClave());
    }

    @Test
    void apartarEnElPlanoPublicoSinPinOConPinMaloSigueFuncionandoYDejaLaClaveVacia() {
        sinSesion();
        String tag = etiqueta();
        Lote sinPin = lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        Lote pinMalo = lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);

        controller.cambiarEstadoPublico(sinPin.getId(), apartarPublico(), null, peticion());
        controller.cambiarEstadoPublico(pinMalo.getId(), apartarPublico(), "QQQQ", peticion());

        assertEquals(EstadoLote.APARTADO, recargar(sinPin).getEstado());
        assertNull(recargar(sinPin).getApartadoPorClave());
        assertEquals(EstadoLote.APARTADO, recargar(pinMalo).getEstado());
        assertNull(recargar(pinMalo).getApartadoPorClave());
    }

    // ---------------------------------------------------------------- panel y limpieza

    @Test
    void apartarDesdeElPanelGuardaAQuienLoHaceYSiAdministracionElige_AlAsesorElegido() {
        Usuario asesor = asesorConPin("Asesor Panel", null);
        AsesorExterno externo = externoConPin("Externo Panel", pinNuevo());
        String tag = etiqueta();
        Lote propio = lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        Lote paraExterno = lote(samai(), tag, "2", "200", EstadoLote.DISPONIBLE);

        iniciarSesionComo(asesor);
        loteService.cambiarEstado(propio.getId(),
                new CambiarEstadoLoteRequest(EstadoLote.APARTADO, null, null, "Cliente", null, null, null));
        assertEquals("U-" + asesor.getId(), recargar(propio).getApartadoPorClave());

        iniciarSesionComo(usuario(Role.LIDER_AREA));
        loteService.cambiarEstado(paraExterno.getId(),
                new CambiarEstadoLoteRequest(EstadoLote.APARTADO, null, "Lo apartó administración", "Cliente", null, null,
                        externo.getId()));
        assertEquals("E-" + externo.getId(), recargar(paraExterno).getApartadoPorClave());

        // pasar a otro apartado vuelve a poner la clave del asesor elegido; salir del apartado la borra
        loteService.cambiarEstado(paraExterno.getId(),
                new CambiarEstadoLoteRequest(EstadoLote.APARTADO_A_PLAZO, LocalDateTime.now().plusDays(2), "Plazo", "Cliente",
                        null, asesor.getId(), null));
        assertEquals("U-" + asesor.getId(), recargar(paraExterno).getApartadoPorClave());
        loteService.cambiarEstado(paraExterno.getId(),
                new CambiarEstadoLoteRequest(EstadoLote.DISPONIBLE, null, "Se liberó", null, null, null, null));
        assertNull(recargar(paraExterno).getApartadoPorClave());
    }

    @Test
    void alSalirDelApartadoPorCualquierCaminoLaClaveSeLimpia() {
        Lote lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.APARTADO);
        lote.setApartadoPorClave("U-1");
        // el liberador automático y la venta cambian el estado con Lote.cambiarEstado
        lote.cambiarEstado(EstadoLote.EN_PROCESO_DE_FIRMA, null);
        assertNull(lote.getApartadoPorClave());
        lote.setApartadoPorClave("U-1");
        lote.cambiarEstado(EstadoLote.DISPONIBLE, null);
        assertNull(lote.getApartadoPorClave());
    }

    // ---------------------------------------------------------------- cotizador del panel

    @Test
    void elCotizadorDelPanelDaAlAsesorSusApartadosYAdministracionTodosMenosLosReservados() {
        Usuario yo = asesorConPin("José Pérez", null);
        Usuario otro = asesorConPin("Otra Persona", null);
        String tag = etiqueta();
        lote(samai(), tag, "1", "200", EstadoLote.DISPONIBLE);
        Lote porClave = lote(samai(), tag, "2", "200", EstadoLote.APARTADO);
        porClave.setApartadoPorClave("U-" + yo.getId());
        Lote deOtro = lote(samai(), tag, "3", "200", EstadoLote.APARTADO_A_PLAZO);
        deOtro.setApartadoPorClave("U-" + otro.getId());
        Lote porNombre = lote(samai(), tag, "4", "200", EstadoLote.APARTADO);
        movimientoDeApartado(porNombre, usuario(Role.ADMIN), "  JOSE   PEREZ ");
        Lote nombreAjeno = lote(samai(), tag, "5", "200", EstadoLote.APARTADO);
        movimientoDeApartado(nombreAjeno, usuario(Role.ADMIN), "Otra Persona");
        Lote delPanel = lote(samai(), tag, "6", "200", EstadoLote.APARTADO_CON_DINERO);
        movimientos.saveAndFlush(new MovimientoLote(delPanel, EstadoLote.DISPONIBLE, EstadoLote.APARTADO_CON_DINERO, yo, null,
                "Cliente", null, null));
        lote(samai(), tag, "7", "200", EstadoLote.RESERVADO_WE);
        lote(samai(), tag, "8", "200", EstadoLote.VENDIDO);

        iniciarSesionComo(yo);
        List<LoteDto> delAsesor = apartadoService.listarCotizables(samai().getId());
        assertEquals(List.of("1", "2", "4", "6"), numeros(delAsesor, tag));
        assertEquals(EstadoLote.APARTADO, delAsesor.stream().filter(l -> "2".equals(l.numeroLote()) && tag.equals(l.manzana()))
                .findFirst().orElseThrow().estado());

        for (Role rol : List.of(Role.LIDER_AREA, Role.ADMIN)) {
            iniciarSesionComo(usuario(rol));
            assertEquals(List.of("1", "2", "3", "4", "5", "6"), numeros(apartadoService.listarCotizables(samai().getId()), tag),
                    rol.name());
        }
    }

    // ---------------------------------------------------------------- cotizador público

    @Test
    void elEndpointPublicoDaSoloLosApartadosDelDuenioDelPinPorClaveOPorNombre() {
        sinSesion();
        Usuario interno = asesorConPin("Ana Núñez", pinNuevo());
        AsesorExterno externo = externoConPin("Beto Externo", pinNuevo());
        String tag = etiqueta();
        Lote suyoPorClave = lote(samai(), tag, "1", "200", EstadoLote.APARTADO);
        suyoPorClave.setApartadoPorClave("U-" + interno.getId());
        Lote deExterno = lote(samai(), tag, "2", "200", EstadoLote.APARTADO);
        deExterno.setApartadoPorClave("E-" + externo.getId());
        Lote suyoPorNombre = lote(samai(), tag, "3", "200", EstadoLote.APARTADO);
        movimientoDeApartado(suyoPorNombre, null, "ana nunez");
        Lote nombreAjeno = lote(samai(), tag, "4", "200", EstadoLote.APARTADO);
        movimientoDeApartado(nombreAjeno, null, "Beto Externo");
        lote(samai(), tag, "5", "200", EstadoLote.DISPONIBLE);
        lote(samai(), tag, "6", "200", EstadoLote.RESERVADO_WE);

        List<LoteDto> deAna = controller.misApartadosPublico("samai", interno.getPin(), peticion());
        assertEquals(List.of("1", "3"), numeros(deAna, tag));
        assertTrue(deAna.stream().allMatch(l -> l.estado() == EstadoLote.APARTADO));
        // dato interno que el público no debe recibir
        assertTrue(deAna.stream().allMatch(l -> l.cambiadoPor() == null && l.montoApartado() == null));

        assertEquals(List.of("2", "4"), numeros(controller.misApartadosPublico("samai", externo.getPin(), peticion()), tag));
        // en el otro plano no hay lotes de esta prueba
        assertEquals(List.of(), numeros(controller.misApartadosPublico("nanuu", interno.getPin(), peticion()), tag));
    }

    @Test
    void elEndpointPublicoSinPinEsErrorDeValidacionYConPinMaloNoEncontrado() {
        sinSesion();
        assertThrows(ValidationException.class, () -> controller.misApartadosPublico("samai", null, peticion()));
        assertThrows(ValidationException.class, () -> controller.misApartadosPublico("samai", "  ", peticion()));
        assertThrows(ResourceNotFoundException.class, () -> controller.misApartadosPublico("samai", "QQQQ", peticion()));
    }
}
