package com.werealestate.backend.controller;

import com.werealestate.backend.dto.MaterialDtos;
import com.werealestate.backend.service.LimitadorPinPublico;
import com.werealestate.backend.service.MaterialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Material de venta para los asesores del plano público (sin sesión, ver SecurityConfig). Igual que We Forms:
 * cada petición exige el PIN del asesor en la cabecera X-Asesor-Pin (nunca en la URL) y los intentos fallidos
 * se limitan por IP. Vive fuera de /api/material a propósito: ese prefijo exige el módulo. */
@RestController
@RequestMapping("/api/material-publico")
public class MaterialPublicoController {

    private final MaterialService service;
    private final LimitadorPinPublico limitador;

    public MaterialPublicoController(MaterialService service, LimitadorPinPublico limitador) {
        this.service = service;
        this.limitador = limitador;
    }

    @GetMapping
    public List<MaterialDtos.Item> listar(
            @RequestParam String proyecto, @RequestHeader("X-Asesor-Pin") String pin, HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return service.listarPublico(proyecto);
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> archivo(
            @PathVariable Long id,
            @RequestParam String proyecto,
            @RequestHeader("X-Asesor-Pin") String pin,
            HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return MaterialController.responder(service.descargarPublico(id, proyecto));
    }

    @GetMapping("/{id}/miniatura")
    public ResponseEntity<byte[]> miniatura(
            @PathVariable Long id,
            @RequestParam String proyecto,
            @RequestHeader("X-Asesor-Pin") String pin,
            HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return MaterialController.responder(service.miniaturaPublico(id, proyecto));
    }

    @GetMapping("/{id}/imagenes/{imagenId}")
    public ResponseEntity<byte[]> imagen(
            @PathVariable Long id,
            @PathVariable Long imagenId,
            @RequestParam String proyecto,
            @RequestHeader("X-Asesor-Pin") String pin,
            HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return MaterialController.responder(service.imagenPublico(id, imagenId, proyecto));
    }

    @GetMapping("/{id}/imagenes/{imagenId}/miniatura")
    public ResponseEntity<byte[]> miniaturaImagen(
            @PathVariable Long id,
            @PathVariable Long imagenId,
            @RequestParam String proyecto,
            @RequestHeader("X-Asesor-Pin") String pin,
            HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return MaterialController.responder(service.miniaturaImagenPublico(id, imagenId, proyecto));
    }

    /** Toda la galería en un ZIP (ver MaterialController.enviarZip). */
    @GetMapping("/{id}/zip")
    public void zip(
            @PathVariable Long id,
            @RequestParam String proyecto,
            @RequestHeader("X-Asesor-Pin") String pin,
            HttpServletRequest http,
            HttpServletResponse response) throws IOException {
        limitador.exigir(http, pin, proyecto);
        MaterialController.enviarZip(service.zipPublico(id, proyecto), response);
    }
}
