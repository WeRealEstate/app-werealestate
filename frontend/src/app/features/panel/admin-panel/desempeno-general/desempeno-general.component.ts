import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ReportesService } from '../../../../core/services/reportes.service';
import { ReporteDesempeno } from '../../../../core/models/reporte.model';
import { ESTADO_LEAD_LABELS, EstadoLead } from '../../../../core/models/lead.model';

type Periodo = 'mes' | '90d' | 'todo';

interface BarDatum {
  label: string;
  value: number;
  pct: number;
  barClass: string;
}

interface EstadoSliceDatum {
  estado: EstadoLead;
  label: string;
  value: number;
  pct: number;
  from: number;
  to: number;
  dotClass: string;
  isStatus: boolean;
}

const ESTADO_ORDEN: EstadoLead[] = [
  'NUEVO',
  'CONTACTADO',
  'INTERESADO',
  'CITA_AGENDADA',
  'NEGOCIACION',
  'CERRADO_GANADO',
  'CERRADO_PERDIDO',
];

// Paleta categórica pastel, validada con el validador de la skill de dataviz
// (contraste, separación CVD y piso de visión normal) en el orden exacto en
// que aparece cada estado — el orden es lo que hace segura la paleta, no
// los hex sueltos. Ganado/perdido usan los colores de estado reservados
// (verde/rojo); esos dos se acompañan siempre de texto (nunca solo color).
const ESTADO_HEX: Record<EstadoLead, { light: string; dark: string }> = {
  NUEVO: { light: '#a78bfa', dark: '#8b5cf6' },
  CONTACTADO: { light: '#14b8a6', dark: '#0d9488' },
  INTERESADO: { light: '#f59e0b', dark: '#d97706' },
  CITA_AGENDADA: { light: '#38bdf8', dark: '#0284c7' },
  NEGOCIACION: { light: '#f472b6', dark: '#ec4899' },
  CERRADO_GANADO: { light: '#22c55e', dark: '#16a34a' },
  CERRADO_PERDIDO: { light: '#f87171', dark: '#ef4444' },
};

const ESTADO_DOT_CLASS: Record<EstadoLead, string> = {
  NUEVO: 'bg-[#a78bfa] dark:bg-[#8b5cf6]',
  CONTACTADO: 'bg-[#14b8a6] dark:bg-[#0d9488]',
  INTERESADO: 'bg-[#f59e0b] dark:bg-[#d97706]',
  CITA_AGENDADA: 'bg-[#38bdf8] dark:bg-[#0284c7]',
  NEGOCIACION: 'bg-[#f472b6] dark:bg-[#ec4899]',
  CERRADO_GANADO: 'bg-[#22c55e] dark:bg-[#16a34a]',
  CERRADO_PERDIDO: 'bg-[#f87171] dark:bg-[#ef4444]',
};

// Barras con relieve: degradado de un tono claro al color base de la paleta
// (mismo hue, la identidad no cambia) + un glow del color base + puntas
// redondeadas completas. Mismo orden de 7 slots ya validado para uso
// categórico en lista (ranking de asesores).
const PALETTE_BAR_CLASS = [
  'bg-gradient-to-r from-[#c4b5fd] to-[#a78bfa] shadow-[0_2px_12px_-2px_#a78bfa] dark:from-[#a78bfa] dark:to-[#8b5cf6] dark:shadow-[0_2px_12px_-2px_#8b5cf6]',
  'bg-gradient-to-r from-[#fcd34d] to-[#f59e0b] shadow-[0_2px_12px_-2px_#f59e0b] dark:from-[#fbbf24] dark:to-[#d97706] dark:shadow-[0_2px_12px_-2px_#d97706]',
  'bg-gradient-to-r from-[#5eead4] to-[#14b8a6] shadow-[0_2px_12px_-2px_#14b8a6] dark:from-[#2dd4bf] dark:to-[#0d9488] dark:shadow-[0_2px_12px_-2px_#0d9488]',
  'bg-gradient-to-r from-[#fca5a5] to-[#f87171] shadow-[0_2px_12px_-2px_#f87171] dark:from-[#f87171] dark:to-[#ef4444] dark:shadow-[0_2px_12px_-2px_#ef4444]',
  'bg-gradient-to-r from-[#7dd3fc] to-[#38bdf8] shadow-[0_2px_12px_-2px_#38bdf8] dark:from-[#38bdf8] dark:to-[#0284c7] dark:shadow-[0_2px_12px_-2px_#0284c7]',
  'bg-gradient-to-r from-[#f9a8d4] to-[#f472b6] shadow-[0_2px_12px_-2px_#f472b6] dark:from-[#f472b6] dark:to-[#ec4899] dark:shadow-[0_2px_12px_-2px_#ec4899]',
  'bg-gradient-to-r from-[#86efac] to-[#22c55e] shadow-[0_2px_12px_-2px_#22c55e] dark:from-[#4ade80] dark:to-[#16a34a] dark:shadow-[0_2px_12px_-2px_#16a34a]',
] as const;

const DESARROLLO_BAR_CLASSES = [PALETTE_BAR_CLASS[0], PALETTE_BAR_CLASS[2]];
const RIESGO_BAR_CLASS =
  'bg-gradient-to-r from-[#fca5a5] to-[#f87171] shadow-[0_2px_12px_-2px_#f87171] dark:from-[#f87171] dark:to-[#ef4444] dark:shadow-[0_2px_12px_-2px_#ef4444]';

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function bars(entradas: { etiqueta: string; total: number }[], clases: readonly string[]): BarDatum[] {
  const max = Math.max(1, ...entradas.map((e) => e.total));
  return entradas.map((e, i) => ({
    label: e.etiqueta,
    value: e.total,
    pct: (e.total / max) * 100,
    barClass: clases[i % clases.length],
  }));
}

@Component({
  selector: 'app-desempeno-general',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './desempeno-general.component.html',
})
export class DesempenoGeneralComponent {
  private readonly reportesService = inject(ReportesService);

  readonly periodo = signal<Periodo>('mes');
  readonly isLoading = signal(true);
  readonly reporte = signal<ReporteDesempeno | null>(null);
  /** true cuando la última carga falló: distinto de "reporte sin datos" (ver cargar()), para que
   * un backend caído no se vea igual que un mes genuinamente sin actividad. */
  readonly hayError = signal(false);
  /** El reporte completo (tendencia, donas, barras por asesor/desarrollo/riesgo, cotizaciones)
   * arranca oculto: un admin que revisa esto a diario solo necesita los 4 KPI del resumen la
   * mayoría de las veces. Se queda como el admin lo dejó al cambiar de periodo, a propósito. */
  readonly mostrarReporteCompleto = signal(false);

  readonly periodoLabel = computed(() => {
    switch (this.periodo()) {
      case 'mes':
        return 'este mes';
      case '90d':
        return 'los últimos 90 días';
      case 'todo':
        return 'todo el historial';
    }
  });

  readonly totalLeads = computed(() => this.reporte()?.totalLeadsCreados ?? 0);
  readonly tasaConversion = computed(() => this.reporte()?.tasaConversion ?? 0);
  readonly ventasCerradas = computed(() => this.reporte()?.ventasCerradas ?? 0);
  readonly asesorEstrella = computed(() => this.reporte()?.asesorEstrella ?? null);
  // leadsPorAsesor ya viene ordenado descendente desde el backend (ver ReporteService.agrupar),
  // así que el primero es directamente el asesor con más leads del periodo.
  readonly asesorConMasLeads = computed(() => this.reporte()?.leadsPorAsesor[0] ?? null);
  readonly riesgo = computed(() => this.reporte()?.riesgo ?? { total: 0, porAsesor: [] });
  readonly cotizaciones = computed(() => this.reporte()?.cotizaciones ?? { total: 0, montoTotal: 0, porProyecto: [] });

  // Dona de "leads por estado": rebanadas con % acumulado (from/to) para
  // dibujar un conic-gradient, más una leyenda con texto (nunca solo color).
  readonly leadsPorEstadoPie = computed<EstadoSliceDatum[]>(() => {
    const conteo = {} as Record<EstadoLead, number>;
    for (const c of this.reporte()?.leadsPorEstado ?? []) conteo[c.etiqueta as EstadoLead] = c.total;
    const total = this.totalLeads();
    let acumulado = 0;
    const slices: EstadoSliceDatum[] = [];
    for (const estado of ESTADO_ORDEN) {
      const value = conteo[estado] ?? 0;
      if (value === 0) continue;
      const pct = total === 0 ? 0 : (value / total) * 100;
      const from = acumulado;
      acumulado += pct;
      slices.push({
        estado,
        label: ESTADO_LEAD_LABELS[estado],
        value,
        pct,
        from,
        to: acumulado,
        dotClass: ESTADO_DOT_CLASS[estado],
        isStatus: estado === 'CERRADO_GANADO' || estado === 'CERRADO_PERDIDO',
      });
    }
    return slices;
  });

  private buildConicGradient(theme: 'light' | 'dark'): string {
    const slices = this.leadsPorEstadoPie();
    if (slices.length === 0) return 'transparent';
    const stops = slices.map((s) => `${ESTADO_HEX[s.estado][theme]} ${s.from}% ${s.to}%`).join(', ');
    return `conic-gradient(${stops})`;
  }

  readonly estadoConicLight = computed(() => this.buildConicGradient('light'));
  readonly estadoConicDark = computed(() => this.buildConicGradient('dark'));

  readonly leadsPorAsesor = computed<BarDatum[]>(() =>
    bars((this.reporte()?.leadsPorAsesor ?? []).slice(0, PALETTE_BAR_CLASS.length), PALETTE_BAR_CLASS),
  );

  readonly leadsPorDesarrollo = computed<BarDatum[]>(() =>
    bars(this.reporte()?.leadsPorDesarrollo ?? [], DESARROLLO_BAR_CLASSES),
  );

  readonly actividadPorAsesor = computed<BarDatum[]>(() =>
    bars((this.reporte()?.actividadPorAsesor ?? []).slice(0, PALETTE_BAR_CLASS.length), PALETTE_BAR_CLASS),
  );

  readonly riesgoPorAsesor = computed<BarDatum[]>(() =>
    bars(this.riesgo().porAsesor, [RIESGO_BAR_CLASS]),
  );

  readonly cotizacionesPorProyecto = computed<BarDatum[]>(() =>
    bars(this.cotizaciones().porProyecto, DESARROLLO_BAR_CLASSES),
  );

  // Gráfica de tendencia: dos polylines (leads creados / ventas cerradas) en un
  // viewBox de 100x100, más las etiquetas del eje X (se muestran salteadas si
  // hay muchos puntos, para no amontonar texto).
  readonly tendencia = computed(() => {
    const puntos = this.reporte()?.tendencia ?? [];
    if (puntos.length === 0) return null;

    const maxValor = Math.max(1, ...puntos.map((p) => Math.max(p.leadsCreados, p.ventasCerradas)));
    const pasoX = puntos.length > 1 ? 100 / (puntos.length - 1) : 0;
    const y = (v: number) => 96 - (v / maxValor) * 92;

    const leadsPuntos = puntos.map((p, i) => `${i * pasoX},${y(p.leadsCreados)}`).join(' ');
    const ventasPuntos = puntos.map((p, i) => `${i * pasoX},${y(p.ventasCerradas)}`).join(' ');

    // Como máximo ~6 etiquetas visibles en el eje, para que no se amontonen.
    const saltar = Math.max(1, Math.ceil(puntos.length / 6));
    const etiquetas = puntos.map((p, i) => ({
      texto: p.etiqueta,
      x: i * pasoX,
      visible: i % saltar === 0 || i === puntos.length - 1,
    }));

    return { leadsPuntos, ventasPuntos, etiquetas, maxValor };
  });

  // Anillo de "tasa de conversión": circunferencia fija del SVG (r=30) y el
  // offset que se anima de "vacío" a la posición real al cargar los datos.
  readonly ringRadius = 30;
  readonly ringCircumference = 2 * Math.PI * this.ringRadius;

  readonly ringOffset = computed(() => {
    const pct = Math.max(0, Math.min(100, this.tasaConversion()));
    return this.ringCircumference * (1 - pct / 100);
  });

  // Arranca "vacío" (offset = circunferencia completa) y se anima a su valor
  // real tras el primer pintado, para que el anillo se sienta como que se
  // llena en vez de aparecer ya resuelto.
  readonly displayedRingOffset = signal(this.ringCircumference);

  constructor() {
    this.cargar();
  }

  cambiarPeriodo(periodo: Periodo): void {
    if (periodo === this.periodo()) return;
    this.periodo.set(periodo);
    this.cargar();
  }

  reintentar(): void {
    this.cargar();
  }

  alternarReporteCompleto(): void {
    this.mostrarReporteCompleto.update((v) => !v);
  }

  private rangoPeriodo(): { desde?: string; hasta?: string } {
    const hoy = new Date();
    if (this.periodo() === 'todo') return {};
    if (this.periodo() === 'mes') {
      return { desde: toIsoDate(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), hasta: toIsoDate(hoy) };
    }
    const hace90 = new Date(hoy);
    hace90.setDate(hace90.getDate() - 90);
    return { desde: toIsoDate(hace90), hasta: toIsoDate(hoy) };
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.hayError.set(false);
    try {
      const { desde, hasta } = this.rangoPeriodo();
      this.reporte.set(await this.reportesService.obtenerDesempeno(desde, hasta));
      setTimeout(() => this.displayedRingOffset.set(this.ringOffset()), 60);
    } catch {
      // Distinto de "sin datos": el reporte no se toca, así que un reintento no parpadea a vacío.
      this.hayError.set(true);
    } finally {
      this.isLoading.set(false);
    }
  }
}
