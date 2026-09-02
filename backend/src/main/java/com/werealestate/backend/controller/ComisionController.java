package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ComisionConfigDto;
import com.werealestate.backend.dto.ComisionConfigRequest;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.ComisionPagadaRequest;
import com.werealestate.backend.service.ComisionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comisiones")
public class ComisionController {

    private final ComisionService comisionService;

    public ComisionController(ComisionService comisionService) {
        this.comisionService = comisionService;
    }

    @GetMapping
    public List<ComisionDto> listar() {
        return comisionService.listar();
    }

    @GetMapping("/configuracion")
    public ComisionConfigDto configuracion() {
        return comisionService.configuracion();
    }

    @PutMapping("/configuracion")
    public ComisionConfigDto actualizarConfiguracion(@Valid @RequestBody ComisionConfigRequest request) {
        return comisionService.actualizarConfiguracion(request);
    }

    @PutMapping("/{id}/pagar")
    public ComisionDto marcarPagada(@PathVariable Long id, @Valid @RequestBody ComisionPagadaRequest request) {
        return comisionService.marcarPagada(id, request.pagada());
    }
}
