package com.werealestate.backend.controller;

import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionEstadoRequest;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.service.PromocionService;
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
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.werealestate.backend.service.PromocionVentaService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promociones")
public class PromocionController {

    private final PromocionService promocionService;
    private final PromocionVentaService promocionVentaService;

    public PromocionController(PromocionService promocionService, PromocionVentaService promocionVentaService) {
        this.promocionService = promocionService;
        this.promocionVentaService = promocionVentaService;
    }

    @GetMapping("/activas")
    public List<PromocionDto> listarActivas() {
        return promocionService.listarActivas();
    }

    /** Las promociones que se pueden elegir al registrar una venta de esa fecha. */
    @GetMapping("/para-venta")
    public List<PromocionDto> paraVenta(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "false") boolean modificar) {
        return promocionVentaService.paraVenta(fecha, modificar);
    }

    @GetMapping
    public List<PromocionDto> listar() {
        return promocionService.listar();
    }

    @PostMapping
    public PromocionDto crear(@Valid @RequestBody PromocionCreateRequest request) {
        return promocionService.crear(request);
    }

    @PutMapping("/{id}")
    public PromocionDto actualizar(@PathVariable Long id, @Valid @RequestBody PromocionUpdateRequest request) {
        return promocionService.actualizar(id, request);
    }

    @PutMapping("/{id}/estado")
    public PromocionDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody PromocionEstadoRequest request) {
        return promocionService.cambiarEstado(id, request.activa());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        promocionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
