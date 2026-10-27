package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** El PIN opcional del Score público: no estorba si no viene, marca al asesor si sirve y no permite probar PINs sin
 * límite (los rechazados cuentan contra el mismo tope por IP que el resto de las páginas públicas). */
class LimitadorPinPublicoTest {

    private AsesorPublicoService asesores;
    private LimitadorPinPublico limitador;
    private MockHttpServletRequest http;

    @BeforeEach
    void preparar() {
        asesores = mock(AsesorPublicoService.class);
        limitador = new LimitadorPinPublico(asesores);
        http = new MockHttpServletRequest();
        http.setRemoteAddr("203.0.113.7");
        when(asesores.identificar(anyString(), any()))
                .thenReturn(new AsesorPublicoService.Identificado(AsesorPublicoService.Motivo.NO_ENCONTRADO, null, null));
    }

    @Test
    void sinPinNoSeRevisaNada() {
        var intento = limitador.identificarSiViene(http, null);
        assertNull(intento.asesor());
        assertFalse(intento.rechazado());
        assertFalse(limitador.identificarSiViene(http, "  ").rechazado());
        verify(asesores, never()).identificar(anyString(), any());
    }

    @Test
    void unPinValidoDevuelveAlAsesorSinPlano() {
        var ok = new AsesorPublicoService.Identificado(AsesorPublicoService.Motivo.OK, mock(Usuario.class), null);
        when(asesores.identificar("AB12", null)).thenReturn(ok);
        var intento = limitador.identificarSiViene(http, "AB12");
        assertSame(ok, intento.asesor());
        assertFalse(intento.rechazado());
    }

    @Test
    void losPinesRechazadosCuentanYAlTopeYaNoSeRevisan() {
        for (int i = 0; i < 5; i++) {
            var intento = limitador.identificarSiViene(http, "ZZZZ");
            assertNull(intento.asesor());
            assertTrue(intento.rechazado());
        }
        // Sexto intento en la misma IP: ni se revisa (no sirve para seguir probando) ni se marca como rechazado.
        var bloqueado = limitador.identificarSiViene(http, "ZZZZ");
        assertNull(bloqueado.asesor());
        assertFalse(bloqueado.rechazado());
        verify(asesores, times(5)).identificar("ZZZZ", null);
        assertEquals(5, org.mockito.Mockito.mockingDetails(asesores).getInvocations().size());
    }
}
