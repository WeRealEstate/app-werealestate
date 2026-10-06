package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ComisionDetalleDto;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.dto.ComisionEntregaRequest;
import com.werealestate.backend.dto.ComisionResumenDto;
import com.werealestate.backend.dto.ComisionUpdateRequest;
import com.werealestate.backend.dto.ComisionesPorEntregarDto;
import com.werealestate.backend.service.ComisionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/finanzas/comisiones")
public class ComisionController {

    private final ComisionService comisionService;

    public ComisionController(ComisionService comisionService) {
        this.comisionService = comisionService;
    }

    @GetMapping
    public List<ComisionDto> listar() {
        return comisionService.listar();
    }

    @GetMapping("/resumen")
    public ComisionResumenDto resumen() {
        return comisionService.resumen();
    }

    @GetMapping("/por-entregar")
    public ComisionesPorEntregarDto porEntregar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return comisionService.porEntregar(fecha);
    }

    @GetMapping("/{id}")
    public ComisionDetalleDto obtener(@PathVariable Long id) {
        return comisionService.obtener(id);
    }

    @PutMapping("/{id}")
    public ComisionDetalleDto editar(@PathVariable Long id, @RequestBody ComisionUpdateRequest request) {
        return comisionService.editar(id, request);
    }

    @PostMapping("/{id}/cancelar")
    public ComisionDetalleDto cancelar(@PathVariable Long id) {
        return comisionService.cancelar(id);
    }

    @PostMapping("/{id}/reactivar")
    public ComisionDetalleDto reactivar(@PathVariable Long id) {
        return comisionService.reactivar(id);
    }

    @PostMapping("/{id}/entregas")
    public ComisionDetalleDto entregar(@PathVariable Long id, @Valid @RequestBody ComisionEntregaRequest request) {
        return comisionService.entregar(id, request);
    }

    @DeleteMapping("/{id}/entregas/{entregaId}")
    public ComisionDetalleDto anularEntrega(@PathVariable Long id, @PathVariable Long entregaId) {
        return comisionService.anularEntrega(id, entregaId);
    }
}
