package com.werealestate.backend.controller;

import com.werealestate.backend.dto.EventoCalendarioDto;
import com.werealestate.backend.dto.EventoCalendarioRequest;
import com.werealestate.backend.service.EventoCalendarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eventos-calendario")
public class EventoCalendarioController {

    private final EventoCalendarioService eventoCalendarioService;

    public EventoCalendarioController(EventoCalendarioService eventoCalendarioService) {
        this.eventoCalendarioService = eventoCalendarioService;
    }

    @GetMapping
    public List<EventoCalendarioDto> listar() {
        return eventoCalendarioService.listar();
    }

    @PostMapping
    public EventoCalendarioDto crear(@Valid @RequestBody EventoCalendarioRequest request) {
        return eventoCalendarioService.crear(request);
    }

    @PutMapping("/{id}")
    public EventoCalendarioDto actualizar(@PathVariable Long id, @Valid @RequestBody EventoCalendarioRequest request) {
        return eventoCalendarioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        eventoCalendarioService.eliminar(id);
    }
}
