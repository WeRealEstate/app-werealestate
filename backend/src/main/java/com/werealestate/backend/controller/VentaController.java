package com.werealestate.backend.controller;

import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public VentaDto crear(@Valid @RequestBody VentaCreateRequest request) {
        return ventaService.crear(request);
    }

    @GetMapping("/buscar")
    public PaginaDto<VentaDto> buscar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return ventaService.buscarPaginado(busqueda, pagina, tamano);
    }
}
