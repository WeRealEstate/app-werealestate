package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.service.ComercializadoraPublicoService;
import com.werealestate.backend.service.IpCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Alta del equipo de una comercializadora por su enlace (sin sesión, ver SecurityConfig). Vive fuera de
 * /api/comercializadoras a propósito: ese prefijo exige el módulo Asesores externos. */
@RestController
@RequestMapping("/api/comercializadoras-publico")
public class ComercializadoraPublicoController {

    private final ComercializadoraPublicoService service;

    public ComercializadoraPublicoController(ComercializadoraPublicoService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public ComercializadoraDtos.PublicoInfo info(@PathVariable String token) {
        return service.info(token);
    }

    @PostMapping("/{token}/unirse")
    public ComercializadoraDtos.UnirseResultado unirse(
            @PathVariable String token, @Valid @RequestBody ComercializadoraDtos.UnirseRequest request, HttpServletRequest http) {
        return service.unirse(token, request, IpCliente.de(http));
    }
}
