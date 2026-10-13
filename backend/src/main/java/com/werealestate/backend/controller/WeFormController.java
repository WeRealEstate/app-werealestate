package com.werealestate.backend.controller;

import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.service.WeFormService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** We Forms en el panel (módulo We Forms): formularios, preguntas y respuestas. */
@RestController
@RequestMapping("/api/we-forms")
public class WeFormController {

    private final WeFormService service;

    public WeFormController(WeFormService service) {
        this.service = service;
    }

    @GetMapping
    public List<WeFormDtos.Lista> listar() {
        return service.listar();
    }

    @PostMapping
    public WeFormDtos.Detalle crear(@Valid @RequestBody WeFormDtos.CrearRequest request) {
        return service.crear(request);
    }

    /** Desarrollos en los que se puede crear un formulario. */
    @GetMapping("/desarrollos")
    public List<com.werealestate.backend.dto.DesarrolloDto> desarrollos() {
        return service.desarrollosDisponibles().stream().map(com.werealestate.backend.dto.DesarrolloDto::from).toList();
    }

    @GetMapping("/{id}")
    public WeFormDtos.Detalle obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public WeFormDtos.Detalle actualizar(@PathVariable Long id, @Valid @RequestBody WeFormDtos.ActualizarRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/{id}/preguntas")
    public WeFormDtos.Pregunta crearPregunta(@PathVariable Long id, @Valid @RequestBody WeFormDtos.PreguntaRequest request) {
        return service.crearPregunta(id, request);
    }

    @PutMapping("/{id}/preguntas/orden")
    public List<WeFormDtos.Pregunta> reordenar(@PathVariable Long id, @Valid @RequestBody WeFormDtos.OrdenRequest request) {
        return service.reordenar(id, request);
    }

    @PutMapping("/{id}/preguntas/{preguntaId}")
    public WeFormDtos.Pregunta editarPregunta(
            @PathVariable Long id, @PathVariable Long preguntaId, @Valid @RequestBody WeFormDtos.PreguntaRequest request) {
        return service.editarPregunta(id, preguntaId, request);
    }

    @DeleteMapping("/{id}/preguntas/{preguntaId}")
    public ResponseEntity<Void> archivarPregunta(@PathVariable Long id, @PathVariable Long preguntaId) {
        service.archivarPregunta(id, preguntaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/respuestas")
    public PaginaDto<WeFormDtos.Respuesta> respuestas(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String asesor) {
        return service.listarRespuestas(id, pagina, tamano, desde, hasta, asesor);
    }

    @GetMapping("/{id}/respuestas/exportar")
    public List<WeFormDtos.Respuesta> exportar(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String asesor) {
        return service.exportarRespuestas(id, desde, hasta, asesor);
    }

    @GetMapping("/{id}/estadisticas")
    public WeFormDtos.Estadisticas estadisticas(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String asesor) {
        return service.estadisticas(id, desde, hasta, asesor);
    }

    @GetMapping("/resumen")
    public WeFormDtos.ResumenGeneral resumen() {
        return service.resumenGeneral();
    }

    @GetMapping("/respuestas/{respuestaId}/archivos/{itemId}")
    public ResponseEntity<byte[]> archivo(@PathVariable Long respuestaId, @PathVariable Long itemId) {
        return service.archivoDeRespuesta(respuestaId, itemId);
    }
}
