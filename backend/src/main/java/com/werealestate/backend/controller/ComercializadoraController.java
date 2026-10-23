package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.service.ComercializadoraService;
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

/** Comercializadoras en el panel (módulo Asesores externos). */
@RestController
@RequestMapping("/api/comercializadoras")
public class ComercializadoraController {

    private final ComercializadoraService service;

    public ComercializadoraController(ComercializadoraService service) {
        this.service = service;
    }

    @GetMapping
    public List<ComercializadoraDtos.Item> listar() {
        return service.listar();
    }

    @GetMapping("/{id}/integrantes")
    public List<ComercializadoraDtos.Integrante> integrantes(@PathVariable Long id) {
        return service.integrantes(id);
    }

    @PostMapping
    public ComercializadoraDtos.Item crear(@Valid @RequestBody ComercializadoraDtos.CrearRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public ComercializadoraDtos.Item actualizar(
            @PathVariable Long id, @Valid @RequestBody ComercializadoraDtos.ActualizarRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/{id}/regenerar-enlace")
    public ComercializadoraDtos.Item regenerarEnlace(@PathVariable Long id) {
        return service.regenerarEnlace(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
