package com.werealestate.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.werealestate.backend.model.WeForm;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.WeFormPreguntaRepository;
import com.werealestate.backend.repository.WeFormRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** El correo a finanzas solo se publica si el formulario tiene encendido "Avisar a finanzas por correo". */
class WeFormPublicoAvisoFinanzasTest {

    private ApplicationEventPublisher eventos;
    private WeFormPublicoService service;
    private final AvisoRegistroVentaCorreo.RegistroDeVentaRecibido aviso =
            new AvisoRegistroVentaCorreo.RegistroDeVentaRecibido("Ana", "Luis", "SAMAI Campestre", "Form", "S-1", List.of());

    @BeforeEach
    void preparar() {
        eventos = mock(ApplicationEventPublisher.class);
        service = new WeFormPublicoService(
                mock(WeFormService.class), mock(WeFormRepository.class), mock(WeFormPreguntaRepository.class),
                mock(WeFormRespuestaRepository.class), mock(WeFormRespuestaItemRepository.class), mock(LoteRepository.class),
                mock(LoteService.class), mock(ClienteService.class), mock(VentaService.class), mock(UsuarioRepository.class),
                eventos);
    }

    @Test
    void conElAvisoApagadoNoSePublicaElEvento() {
        WeForm form = mock(WeForm.class);
        service.avisarAFinanzas(form, aviso);
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void conElAvisoEncendidoSePublicaElEvento() {
        WeForm form = mock(WeForm.class);
        org.mockito.Mockito.when(form.isAvisoFinanzas()).thenReturn(true);
        service.avisarAFinanzas(form, aviso);
        verify(eventos).publishEvent(aviso);
    }
}
