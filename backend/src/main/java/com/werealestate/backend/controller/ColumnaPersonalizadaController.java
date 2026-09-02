package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ColumnaPersonalizadaCreateRequest;
import com.werealestate.backend.dto.ColumnaPersonalizadaDto;
import com.werealestate.backend.dto.ColumnaPersonalizadaUpdateRequest;
import com.werealestate.backend.service.ColumnaPersonalizadaService;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/columnas")
public class ColumnaPersonalizadaController {

    private final ColumnaPersonalizadaService columnaService;

    public ColumnaPersonalizadaController(ColumnaPersonalizadaService columnaService) {
        this.columnaService = columnaService;
    }

    @GetMapping
    public List<ColumnaPersonalizadaDto> listar(@RequestParam(required = false) Long asesorId) {
        return columnaService.listar(asesorId);
    }

    @PostMapping
    public ColumnaPersonalizadaDto crear(@Valid @RequestBody ColumnaPersonalizadaCreateRequest request) {
        return columnaService.crear(request);
    }

    @PutMapping("/{id}")
    public ColumnaPersonalizadaDto renombrar(@PathVariable Long id, @Valid @RequestBody ColumnaPersonalizadaUpdateRequest request) {
        return columnaService.renombrar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        columnaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
