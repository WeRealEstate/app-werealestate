package com.werealestate.backend.controller;

import com.werealestate.backend.dto.CotizacionCreateRequest;
import com.werealestate.backend.dto.CotizacionDto;
import com.werealestate.backend.service.CotizacionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @PostMapping
    public CotizacionDto registrar(@Valid @RequestBody CotizacionCreateRequest request) {
        return cotizacionService.registrar(request);
    }

    @GetMapping
    public List<CotizacionDto> listar() {
        return cotizacionService.listar();
    }
}
