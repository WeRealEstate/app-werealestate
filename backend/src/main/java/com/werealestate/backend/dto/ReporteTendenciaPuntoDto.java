package com.werealestate.backend.dto;

/** Un punto de la gráfica de tendencia: leads creados y ventas cerradas en ese sub-periodo
 * (semana o mes, según el rango total, ver ReporteService). */
public record ReporteTendenciaPuntoDto(String etiqueta, long leadsCreados, long ventasCerradas) {
}
