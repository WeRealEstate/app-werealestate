package com.werealestate.backend.controller;

import com.werealestate.backend.dto.EtiquetaCreateRequest;
import com.werealestate.backend.dto.EtiquetaDto;
import com.werealestate.backend.dto.EtiquetaUpdateRequest;
import com.werealestate.backend.service.EtiquetaService;
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
@RequestMapping("/api/etiquetas")
public class EtiquetaController {

    private final EtiquetaService etiquetaService;

    public EtiquetaController(EtiquetaService etiquetaService) {
        this.etiquetaService = etiquetaService;
    }

    @GetMapping
    public List<EtiquetaDto> listar(@RequestParam(required = false) Long asesorId) {
        return etiquetaService.listar(asesorId);
    }

    @PostMapping
    public EtiquetaDto crear(@Valid @RequestBody EtiquetaCreateRequest request) {
        return etiquetaService.crear(request);
    }

    @PutMapping("/{id}")
    public EtiquetaDto actualizar(@PathVariable Long id, @Valid @RequestBody EtiquetaUpdateRequest request) {
        return etiquetaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        etiquetaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
