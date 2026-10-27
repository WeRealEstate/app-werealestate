package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ScoreDtos;
import com.werealestate.backend.service.LimitadorPinPublico;
import com.werealestate.backend.service.ScoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Score de ventas del cotizador público (/cotizador-publico/score): sin sesión ni PIN, para la competencia entre
 * asesores. Si el dispositivo ya tiene guardado el PIN de un asesor lo manda en X-Asesor-Pin (nunca en la URL) y la
 * respuesta dice cuál fila es la suya; un PIN que no sirve cuenta contra el tope de intentos por IP. */
@RestController
@RequestMapping("/api/score-publico")
public class ScorePublicoController {

    private final ScoreService service;
    private final LimitadorPinPublico limitador;

    public ScorePublicoController(ScoreService service, LimitadorPinPublico limitador) {
        this.service = service;
        this.limitador = limitador;
    }

    @GetMapping
    public ScoreDtos.Publico ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestHeader(value = "X-Asesor-Pin", required = false) String pin,
            HttpServletRequest http) {
        ScoreDtos.Ranking ranking = service.rankingPublico(desde, hasta);
        LimitadorPinPublico.Intento intento = limitador.identificarSiViene(http, pin);
        String miClave = intento.asesor() == null
                ? null
                : ScoreService.claveDe(intento.asesor().usuario(), intento.asesor().externo());
        return new ScoreDtos.Publico(ranking, miClave, intento.rechazado());
    }
}
