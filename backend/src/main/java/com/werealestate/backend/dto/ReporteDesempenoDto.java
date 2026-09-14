package com.werealestate.backend.dto;

import java.util.List;

/**
 * Dashboard de desempeño para el admin. Dos criterios de fecha conviven aquí a propósito:
 * - "creados": leads cuya fechaCreacion cae en el periodo elegido (el "cohorte" de ese periodo).
 * - "cerrados"/ventas: leads cuyo estado es CERRADO_* y cuya fechaUltimoContacto cae en el
 *   periodo (se usa como aproximación de fecha de cierre, igual criterio que ya usaba el
 *   dashboard anterior, porque el modelo no tiene una fecha de cierre dedicada).
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
