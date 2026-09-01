package com.werealestate.backend.controller;

import com.werealestate.backend.dto.SeguimientoProximoDto;
import com.werealestate.backend.service.SeguimientoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seguimientos")
public class SeguimientoProximosController {

    private final SeguimientoService seguimientoService;

    public SeguimientoProximosController(SeguimientoService seguimientoService) {
        this.seguimientoService = seguimientoService;
    }

    @GetMapping("/proximos")
    public List<SeguimientoProximoDto> proximos() {
        return seguimientoService.proximos();
    }
}
