package com.werealestate.backend.controller;

import com.werealestate.backend.dto.TareaCreateRequest;
import com.werealestate.backend.dto.TareaDto;
import com.werealestate.backend.dto.TareaEstadoRequest;
import com.werealestate.backend.service.TareaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tareas")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    @GetMapping
    public List<TareaDto> misTareas() {
        return tareaService.misTareas();
    }

    @GetMapping("/creadas")
    public List<TareaDto> tareasCreadas() {
        return tareaService.tareasCreadas();
    }

    @PostMapping
    public TareaDto crear(@Valid @RequestBody TareaCreateRequest request) {
        return tareaService.crear(request);
    }

    @PutMapping("/{id}/estado")
    public TareaDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody TareaEstadoRequest request) {
        return tareaService.cambiarEstado(id, request);
    }
}
