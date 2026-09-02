package com.werealestate.backend.controller;

import com.werealestate.backend.dto.LeadCreateRequest;
import com.werealestate.backend.dto.LeadDto;
import com.werealestate.backend.dto.LeadUpdateRequest;
import com.werealestate.backend.dto.MoverColumnaRequest;
import com.werealestate.backend.dto.MoverLeadRequest;
import com.werealestate.backend.dto.ReasignarLeadRequest;
import com.werealestate.backend.service.LeadService;
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
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public List<LeadDto> listar() {
        return leadService.listar();
    }

    @GetMapping("/frios")
    public List<LeadDto> listarFrios() {
        return leadService.listarFrios();
    }

    @GetMapping("/archivados")
    public List<LeadDto> listarArchivados() {
        return leadService.listarArchivados();
    }

    @GetMapping("/{id}")
    public LeadDto obtener(@PathVariable Long id) {
        return leadService.obtener(id);
    }

    @PostMapping
    public LeadDto crear(@Valid @RequestBody LeadCreateRequest request) {
        return leadService.crear(request);
    }

    @PutMapping("/{id}")
    public LeadDto actualizar(@PathVariable Long id, @Valid @RequestBody LeadUpdateRequest request) {
        return leadService.actualizar(id, request);
    }

    @PutMapping("/{id}/reasignar")
    public LeadDto reasignar(@PathVariable Long id, @Valid @RequestBody ReasignarLeadRequest request) {
        return leadService.reasignar(id, request);
    }

    @PutMapping("/{id}/mover")
    public LeadDto mover(@PathVariable Long id, @Valid @RequestBody MoverLeadRequest request) {
        return leadService.mover(id, request.estado());
    }

    @PutMapping("/{id}/mover-columna")
    public LeadDto moverAColumnaPersonalizada(@PathVariable Long id, @Valid @RequestBody MoverColumnaRequest request) {
        return leadService.moverAColumnaPersonalizada(id, request.columnaPersonalizadaId());
    }

    @PutMapping("/{id}/archivar")
    public LeadDto archivar(@PathVariable Long id) {
        return leadService.archivar(id);
    }

    @PutMapping("/{id}/desarchivar")
    public LeadDto desarchivar(@PathVariable Long id) {
        return leadService.desarchivar(id);
    }
}
