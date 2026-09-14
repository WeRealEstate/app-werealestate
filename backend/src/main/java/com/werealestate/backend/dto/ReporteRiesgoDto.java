package com.werealestate.backend.dto;

import java.util.List;

/** Leads en riesgo (fríos o sin contactar) AHORA MISMO: a diferencia del resto del reporte, no
 * depende del rango de fechas elegido, es siempre una foto del estado actual. */
public record ReporteRiesgoDto(long total, List<ReporteConteoDto> porAsesor) {
}
