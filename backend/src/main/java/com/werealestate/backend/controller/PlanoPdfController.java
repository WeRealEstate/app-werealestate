package com.werealestate.backend.controller;

import com.werealestate.backend.service.LimitadorPinPublico;
import com.werealestate.backend.service.PlanoPdfService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PDF vectorial del plano (ver PlanoPdfService). Dos entradas: el plano interno (sesión + módulo PLANO, ver
 * ModuloAccesoInterceptor) y el público (sin sesión, PIN del asesor en X-Asesor-Pin, igual que material-publico).
 * Sin PDF original guardado responden 404 y el frontend arma el PDF con la imagen, como antes. */
@RestController
@RequestMapping("/api")
public class PlanoPdfController {

    private final PlanoPdfService service;
    private final LimitadorPinPublico limitador;

    public PlanoPdfController(PlanoPdfService service, LimitadorPinPublico limitador) {
        this.service = service;
        this.limitador = limitador;
    }

    @GetMapping("/plano/{desarrolloId}/pdf")
    public ResponseEntity<byte[]> interno(@PathVariable Long desarrolloId) {
        return responder(service.armarInterno(desarrolloId));
    }

    @GetMapping("/plano-publico/{proyecto}/pdf")
    public ResponseEntity<byte[]> publico(
            @PathVariable String proyecto, @RequestHeader("X-Asesor-Pin") String pin, HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return responder(service.armarPublico(proyecto));
    }

    private static ResponseEntity<byte[]> responder(PlanoPdfService.Archivo archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archivo.bytes());
    }
}
