package com.werealestate.backend.dto;

import java.util.List;

/** Todo lo que necesita /panel/plano en una sola llamada: la imagen del plano del desarrollo
 * y sus lotes (con su pin, si ya lo tienen) para pintar el mapa o editarlo. */
public record PlanoDesarrolloDto(Long desarrolloId, String desarrolloNombre, String planoUrl, List<LoteDto> lotes) {
}
