package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.DestinoPregunta;
import com.werealestate.backend.model.EstadoWeForm;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPreguntaForm;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.WeForm;
import com.werealestate.backend.model.WeFormPregunta;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.WeFormPreguntaRepository;
import com.werealestate.backend.repository.WeFormRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Duplicar un formulario: mismas preguntas activas, otro desarrollo, nuevo link y sin respuestas. */
class WeFormServiceDuplicarTest {

    private WeFormRepository formRepo;
    private WeFormPreguntaRepository preguntaRepo;
    private DesarrolloRepository desarrolloRepo;
    private WeFormService service;
    private WeForm original;
    private Desarrollo samai;
    private Desarrollo nanuu;

    @BeforeEach
    void preparar() {
        formRepo = mock(WeFormRepository.class);
        preguntaRepo = mock(WeFormPreguntaRepository.class);
        desarrolloRepo = mock(DesarrolloRepository.class);
        CurrentUserProvider actual = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(actual.getUsuarioActual()).thenReturn(admin);
        service = new WeFormService(
                formRepo, preguntaRepo, mock(WeFormRespuestaRepository.class), mock(WeFormRespuestaItemRepository.class),
                desarrolloRepo, actual, mock(DriveService.class), "uploads-privados");

        samai = desarrollo(1L, "SAMAI Campestre");
        nanuu = desarrollo(2L, "Aldea Nanuu");
        original = new WeForm("Registra tu venta!", "Descripción", "registra-tu-venta-abcde", samai, admin);
        ReflectionTestUtils.setField(original, "id", 5L);
        when(formRepo.findById(5L)).thenReturn(Optional.of(original));
        when(formRepo.save(any(WeForm.class))).thenAnswer(i -> i.getArgument(0));
        when(preguntaRepo.save(any(WeFormPregunta.class))).thenAnswer(i -> i.getArgument(0));
        when(desarrolloRepo.findById(1L)).thenReturn(Optional.of(samai));
        when(desarrolloRepo.findById(2L)).thenReturn(Optional.of(nanuu));
    }

    private static Desarrollo desarrollo(Long id, String nombre) {
        Desarrollo d = mock(Desarrollo.class);
        when(d.getId()).thenReturn(id);
        when(d.getNombre()).thenReturn(nombre);
        return d;
    }

    private List<WeFormPregunta> preguntasDelOriginal() {
        List<WeFormPregunta> l = new ArrayList<>();
        l.add(new WeFormPregunta(original, 0, TipoPreguntaForm.TITULO, "Datos del cliente", null, false, null, null, true));
        l.add(new WeFormPregunta(original, 1, TipoPreguntaForm.CORTA, "Nombre(s)", null, true, null, DestinoPregunta.NOMBRE, true));
        l.add(new WeFormPregunta(original, 2, TipoPreguntaForm.DESPLEGABLE, "¿Cómo nos conoció?", "Dato extra", false, "Redes\nReferido", null, false));
        return l;
    }

    @Test
    void copiaLasPreguntasActivasAlOtroDesarrolloConSuPropioLink() {
        when(preguntaRepo.findByFormIdAndArchivadaFalseOrderByOrdenAsc(5L)).thenReturn(preguntasDelOriginal());

        var detalle = service.duplicar(5L, new WeFormDtos.DuplicarRequest("  Registra tu venta!  ", "Para Nanuu", 2L));

        assertEquals("Registra tu venta!", detalle.nombre());
        assertEquals("Para Nanuu", detalle.descripcion());
        assertEquals(2L, detalle.desarrolloId());
        assertEquals("nanuu", detalle.proyecto());
        assertEquals(EstadoWeForm.ACTIVO, detalle.estado());
        assertNotEquals(original.getSlug(), detalle.slug());

        ArgumentCaptor<WeFormPregunta> guardadas = ArgumentCaptor.forClass(WeFormPregunta.class);
        verify(preguntaRepo, times(3)).save(guardadas.capture());
        List<WeFormPregunta> copias = guardadas.getAllValues();
        for (int i = 0; i < copias.size(); i++) {
            WeFormPregunta o = preguntasDelOriginal().get(i);
            WeFormPregunta c = copias.get(i);
            assertEquals(i, c.getOrden());
            assertEquals(o.getTipo(), c.getTipo());
            assertEquals(o.getTitulo(), c.getTitulo());
            assertEquals(o.getDescripcion(), c.getDescripcion());
            assertEquals(o.isObligatoria(), c.isObligatoria());
            assertEquals(o.getOpciones(), c.getOpciones());
            assertEquals(o.getDestino(), c.getDestino());
            assertEquals(o.isVisible(), c.isVisible());
            assertTrue(c.getForm() != original, "la pregunta copiada debe colgar del formulario nuevo");
        }
    }

    @Test
    void soloSePuedeDuplicarHaciaSamaiONanuu() {
        Desarrollo otro = desarrollo(3L, "Otro desarrollo");
        when(desarrolloRepo.findById(3L)).thenReturn(Optional.of(otro));
        assertThrows(ValidationException.class, () -> service.duplicar(5L, new WeFormDtos.DuplicarRequest("X", null, 3L)));
        verify(formRepo, never()).save(any(WeForm.class));
    }
}
