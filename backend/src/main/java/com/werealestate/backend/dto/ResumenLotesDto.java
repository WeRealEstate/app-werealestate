package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import java.util.Map;

/** Inventario de un desarrollo contado por estado, para las cifras del inicio del admin (ver
 * LoteService.resumenPorDesarrollo). porEstado trae los 6 estados, con 0 en los que no tienen lotes. */
public record ResumenLotesDto(Long desarrolloId, String desarrollo, long total, Map<EstadoLote, Long> porEstado) {
}
