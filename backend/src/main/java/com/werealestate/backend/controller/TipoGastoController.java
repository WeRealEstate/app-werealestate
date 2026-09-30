package com.werealestate.backend.controller;

import com.werealestate.backend.dto.TipoGastoCreateRequest;
import com.werealestate.backend.dto.TipoGastoDto;
import com.werealestate.backend.dto.TipoGastoUpdateRequest;
import com.werealestate.backend.service.TipoGastoService;
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
@RequestMapping("/api/tipos-gasto")
public class TipoGastoController {

    private final TipoGastoService tipoGastoService;

    public TipoGastoController(TipoGastoService tipoGastoService) {
        this.tipoGastoService = tipoGastoService;
    }

    @GetMapping
    public List<TipoGastoDto> listar() {
        return tipoGastoService.listar();
    }

    @GetMapping("/activos")
    public List<TipoGastoDto> listarActivos() {
        return tipoGastoService.listarActivos();
    }

    @PostMapping
    public TipoGastoDto crear(@Valid @RequestBody TipoGastoCreateRequest request) {
        return tipoGastoService.crear(request);
    }

    @PutMapping("/{id}")
    public TipoGastoDto actualizar(@PathVariable Long id, @Valid @RequestBody TipoGastoUpdateRequest request) {
        return tipoGastoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tipoGastoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
