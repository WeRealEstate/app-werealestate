export interface ReporteConteo {
  etiqueta: string;
  total: number;
}

export interface ReporteTendenciaPunto {
  etiqueta: string;
  leadsCreados: number;
  ventasCerradas: number;
}

export interface ReporteAsesorEstrella {
  nombre: string;
  ventas: number;
}

export interface ReporteRiesgo {
  total: number;
  porAsesor: ReporteConteo[];
}

export interface ReporteCotizaciones {
  total: number;
  montoTotal: number;
  porProyecto: ReporteConteo[];
}

export interface ReporteDesempeno {
  totalLeadsCreados: number;
  tasaConversion: number;
  ventasCerradas: number;
  perdidosCerrados: number;
  asesorEstrella: ReporteAsesorEstrella | null;
  leadsPorEstado: ReporteConteo[];
  leadsPorDesarrollo: ReporteConteo[];
  leadsPorAsesor: ReporteConteo[];
  actividadPorAsesor: ReporteConteo[];
  tendencia: ReporteTendenciaPunto[];
  riesgo: ReporteRiesgo;
  cotizaciones: ReporteCotizaciones;
}
