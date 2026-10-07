package com.werealestate.backend.dto;

import java.util.List;

/**
 * Dashboard de desempeño para el admin. Dos criterios de fecha conviven aquí a propósito:
 * - "creados": leads cuya fechaCreacion cae en el periodo elegido (el "cohorte" de ese periodo).
 * - ventas: las registradas en el módulo Ventas cuya fecha de venta cae en el periodo. El estado
 *   del lead (cerrado ganado/perdido) es solo informativo y no cuenta como venta.
 * - perdidosCerrados: leads CERRADO_PERDIDO con fechaUltimoContacto en el periodo.
 * tasaConversion se calcula sobre el propio cohorte de creados (por eso en periodos recientes se
 * ve más baja: muchos de esos leads aún no han tenido tiempo de cerrar).
 * riesgo es la excepción: siempre es una foto del estado actual, no depende del periodo.
 */
public record ReporteDesempenoDto(
        long totalLeadsCreados,
        double tasaConversion,
        long ventasCerradas,
        long perdidosCerrados,
        ReporteAsesorEstrellaDto asesorEstrella,
        List<ReporteConteoDto> leadsPorEstado,
        List<ReporteConteoDto> leadsPorDesarrollo,
        List<ReporteConteoDto> leadsPorAsesor,
        List<ReporteConteoDto> actividadPorAsesor,
        List<ReporteTendenciaPuntoDto> tendencia,
        ReporteRiesgoDto riesgo,
        ReporteCotizacionesDto cotizaciones) {
}
