package com.werealestate.backend.dto;

import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** (De)serializa la lista de vértices de un polígono de lote a/desde el JSON que se guarda en
 * Lote.mapaPoligonoJson; usado por LoteService al guardar y por LoteDto al leer. */
public final class PoligonoMapaJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PoligonoMapaJson() {
    }

    public static String serializar(List<PuntoMapaDto> puntos) {
        if (puntos == null || puntos.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(puntos);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el polígono del lote", e);
        }
    }

    public static List<PuntoMapaDto> deserializar(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<PuntoMapaDto>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
