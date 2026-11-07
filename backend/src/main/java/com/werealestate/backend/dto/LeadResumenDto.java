package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLead;
import java.util.Map;

/** Conteos de los leads activos de quien pregunta, para el inicio del asesor: total, fríos y cuántos hay en cada
 * estado (los estados sin leads no vienen). Antes ese inicio descargaba todos los leads solo para contarlos. */
public record LeadResumenDto(long total, long frios, Map<EstadoLead, Long> porEstado) {
}
