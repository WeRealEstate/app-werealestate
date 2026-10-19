package com.werealestate.backend.controller;

import com.werealestate.backend.dto.MaterialDtos;
import com.werealestate.backend.service.MaterialService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // ---------------------------------------------------------------- galería

    /** Una imagen por petición (multipart, campo "archivo"); devuelve el material con su galería actualizada. */
    @PostMapping("/{id}/imagenes")
    public MaterialDtos.Item agregarImagen(@PathVariable Long id, @RequestParam MultipartFile archivo) {
        return service.agregarImagen(id, archivo);
    }

    @DeleteMapping("/{id}/imagenes/{imagenId}")
    public MaterialDtos.Item quitarImagen(@PathVariable Long id, @PathVariable Long imagenId) {
        return service.quitarImagen(id, imagenId);
    }

    @PutMapping("/{id}/imagenes/orden")
    public MaterialDtos.Item ordenarImagenes(@PathVariable Long id, @RequestBody MaterialDtos.OrdenImagenes orden) {
        return service.ordenarImagenes(id, orden.ids());
    }

    @GetMapping("/{id}/imagenes/{imagenId}")
    public ResponseEntity<byte[]> imagen(@PathVariable Long id, @PathVariable Long imagenId) {
        return responder(service.imagenDelPanel(id, imagenId));
    }

    @GetMapping("/{id}/imagenes/{imagenId}/miniatura")
    public ResponseEntity<byte[]> miniaturaImagen(@PathVariable Long id, @PathVariable Long imagenId) {
        return responder(service.miniaturaImagenDelPanel(id, imagenId));
    }

    /** Toda la galería en un ZIP. */
    @GetMapping("/{id}/zip")
    public void zip(@PathVariable Long id, HttpServletResponse response) throws IOException {
        enviarZip(service.zipDelPanel(id), response);
    }

    /** Escribe el ZIP directo en la respuesta, una imagen tras otra, sin armarlo en memoria (puede pesar hasta
     * 250 MB). Sin compresión: las imágenes ya vienen comprimidas y así sale más rápido. Es síncrono a propósito,
     * para no depender del tiempo límite de las respuestas asíncronas en descargas lentas desde el celular. */
    static void enviarZip(MaterialService.Zip zip, HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(zip.nombre(), java.nio.charset.StandardCharsets.UTF_8).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        try (OutputStream salida = response.getOutputStream(); ZipOutputStream zipSalida = new ZipOutputStream(salida)) {
            zipSalida.setLevel(Deflater.NO_COMPRESSION);
            for (MaterialService.EntradaZip entrada : zip.archivos()) {
                if (!Files.exists(entrada.ruta())) continue;
                zipSalida.putNextEntry(new ZipEntry(entrada.nombre()));
                Files.copy(entrada.ruta(), zipSalida);
                zipSalida.closeEntry();
            }
        }
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
