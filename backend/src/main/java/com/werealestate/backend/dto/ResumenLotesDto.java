package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import java.util.Map;

/** Inventario de un desarrollo contado por estado, para las cifras del inicio del admin (ver
 * LoteService.resumenPorDesarrollo). porEstado trae los 6 estados, con 0 en los que no tienen lotes.
 * reservadosWe: lotes que la empresa se guardó y se muestran como vendidos (VENDIDO sin venta registrada);
 * solo se separan para admin y administración — para ellos porEstado[VENDIDO] ya no los incluye. Para
 * cualquier otro rol viene en 0 y siguen sumados en VENDIDO. */
public record ResumenLotesDto(
        Long desarrolloId, String desarrollo, long total, Map<EstadoLote, Long> porEstado, long reservadosWe) {
}
