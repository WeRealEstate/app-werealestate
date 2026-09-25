package com.werealestate.backend.controller;

import com.werealestate.backend.dto.AsesorExternoCreateRequest;
import com.werealestate.backend.dto.AsesorExternoDto;
import com.werealestate.backend.dto.AsesorExternoUpdateRequest;
import com.werealestate.backend.service.AsesorExternoService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asesores-externos")
public class AsesorExternoController {

    private final AsesorExternoService asesorExternoService;

    public AsesorExternoController(AsesorExternoService asesorExternoService) {
        this.asesorExternoService = asesorExternoService;
    }

    @GetMapping
    public List<AsesorExternoDto> listar() {
        return asesorExternoService.listar();
    }

    @GetMapping("/activos")
    public List<AsesorExternoDto> listarActivos() {
        return asesorExternoService.listarActivos();
    }

    @PostMapping
    public AsesorExternoDto crear(@Valid @RequestBody AsesorExternoCreateRequest request) {
        return asesorExternoService.crear(request);
    }

    @PutMapping("/{id}")
    public AsesorExternoDto actualizar(@PathVariable Long id, @Valid @RequestBody AsesorExternoUpdateRequest request) {
        return asesorExternoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        asesorExternoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
