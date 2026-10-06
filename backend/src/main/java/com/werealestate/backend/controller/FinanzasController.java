package com.werealestate.backend.controller;

import com.werealestate.backend.dto.FinanzasIngresosDto;
import com.werealestate.backend.dto.FinanzasValorDto;
import com.werealestate.backend.service.FinanzasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finanzas")
public class FinanzasController {

    private final FinanzasService finanzasService;

    public FinanzasController(FinanzasService finanzasService) {
        this.finanzasService = finanzasService;
    }

    @GetMapping("/valor-vendido")
    public FinanzasValorDto valorVendido() {
        return finanzasService.valorVendido();
    }

    @GetMapping("/ingresos")
    public FinanzasIngresosDto ingresos(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Long desarrolloId) {
        return finanzasService.ingresos(desde, hasta, desarrolloId);
    }

    @GetMapping("/ingresos/{mes}")
    public FinanzasIngresosDto.Detalle ingresosDelMes(
            @PathVariable String mes, @RequestParam(required = false) Long desarrolloId) {
        return finanzasService.ingresosDelMes(mes, desarrolloId);
    }
}
