package com.werealestate.backend.controller;

import com.werealestate.backend.dto.MaterialDtos;
import com.werealestate.backend.service.MaterialService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Material de venta en el panel (módulo Material). Crear y editar van en multipart: campos de formulario
 * más el archivo opcional. */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    public List<MaterialDtos.Item> listar() {
        return service.listar();
    }

    @PostMapping
    public MaterialDtos.Item crear(
            @RequestParam String titulo,
            @RequestParam(required = false) String descripcion,
            @RequestParam String categoria,
            @RequestParam String proyecto,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) MultipartFile archivo) {
        return service.crear(titulo, descripcion, categoria, proyecto, texto, archivo);
    }

    @PutMapping("/{id}")
    public MaterialDtos.Item actualizar(
            @PathVariable Long id,
            @RequestParam String titulo,
            @RequestParam(required = false) String descripcion,
            @RequestParam String categoria,
            @RequestParam String proyecto,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean quitarArchivo) {
        return service.actualizar(id, titulo, descripcion, categoria, proyecto, texto, archivo, quitarArchivo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable Long id) {
        return responder(service.descargarDelPanel(id));
    }

    /** Imagen reducida para las tarjetas (solo imágenes). */
    @GetMapping("/{id}/miniatura")
    public ResponseEntity<byte[]> miniatura(@PathVariable Long id) {
        return responder(service.miniaturaDelPanel(id));
    }

    static ResponseEntity<byte[]> responder(MaterialService.Descarga d) {
        return ResponseEntity.ok()
                .contentType(d.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(d.nombre(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(d.bytes());
    }
}
