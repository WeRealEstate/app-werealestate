package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import java.util.List;

/** Vértices que delimitan un lote en el plano, en el orden en que se dibujaron; nula o vacía para
 * borrar la delimitación. Ver LoteService.actualizarPoligonoMapa. */
public record ActualizarPoligonoMapaRequest(@Valid List<PuntoMapaDto> puntos) {
}
