package com.werealestate.backend.controller;

import com.werealestate.backend.dto.SeguimientoCreateRequest;
import com.werealestate.backend.dto.SeguimientoDto;
import com.werealestate.backend.service.SeguimientoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads/{leadId}/seguimientos")
public class SeguimientoController {

    private final SeguimientoService seguimientoService;

    public SeguimientoController(SeguimientoService seguimientoService) {
        this.seguimientoService = seguimientoService;
    }

    @GetMapping
    public List<SeguimientoDto> listar(@PathVariable Long leadId) {
        return seguimientoService.listarPorLead(leadId);
    }

    @PostMapping
    public SeguimientoDto crear(@PathVariable Long leadId, @Valid @RequestBody SeguimientoCreateRequest request) {
        return seguimientoService.crear(leadId, request);
    }
}
