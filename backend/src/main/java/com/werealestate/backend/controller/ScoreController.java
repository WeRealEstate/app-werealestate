package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ScoreDtos;
import com.werealestate.backend.service.ScoreService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Score de ventas (módulo SCORE): ranking de asesores por ventas en un periodo. */
@RestController
@RequestMapping("/api/score")
public class ScoreController {

    private final ScoreService service;

    public ScoreController(ScoreService service) {
        this.service = service;
    }

    /** desde/hasta: fechas de venta, ambas inclusivas (yyyy-MM-dd); sin ellas, todo el historial. */
    @GetMapping
    public ScoreDtos.Ranking ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.ranking(desde, hasta);
    }
}
