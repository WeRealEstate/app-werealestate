import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { LeadsService } from '../../../../core/services/leads.service';
import { ESTADO_LEAD_LABELS, EstadoLead, Lead } from '../../../../core/models/lead.model';

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

@Component({
  selector: 'app-desempeno-general',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './desempeno-general.component.html',
})
export class DesempenoGeneralComponent {
  private readonly leadsService = inject(LeadsService);

  private readonly currencyFormatter = new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency: 'MXN',
    notation: 'compact',
    maximumFractionDigits: 1,
  });

  readonly leads = signal<Lead[]>([]);
  readonly isLoading = signal(true);

  readonly totalLeads = computed(() => this.leads().length);

  private readonly totalCerradosGanados = computed(
    () => this.leads().filter((l) => l.estado === 'CERRADO_GANADO').length,
  );

  readonly tasaConversion = computed(() => {
    const total = this.totalLeads();
    return total === 0 ? 0 : (this.totalCerradosGanados() / total) * 100;
  });

  // Asesor con más ventas cerradas (CERRADO_GANADO). Sin desempate especial:
  // el primero que alcanza el máximo conteo se queda con el lugar.
  readonly asesorEstrella = computed(() => {
    const conteo = new Map<string, number>();
    for (const l of this.leads()) {
      if (l.estado !== 'CERRADO_GANADO') continue;
      conteo.set(l.asesor.nombre, (conteo.get(l.asesor.nombre) ?? 0) + 1);
    }
    let mejor: { nombre: string; ventas: number } | null = null;
    for (const [nombre, ventas] of conteo) {
      if (!mejor || ventas > mejor.ventas) mejor = { nombre, ventas };
    }
    return mejor;
  });

  // Número de ventas (no $). No hay una fecha de cierre dedicada: se usa
  // fechaUltimoContacto, que se actualiza al cambiar el estado del lead
  // (incluido al cerrarlo ganado).
  readonly ventasDelMes = computed(() => {
    const ahora = new Date();
    return this.leads().filter((l) => {
      if (l.estado !== 'CERRADO_GANADO') return false;
      const fecha = new Date(l.fechaUltimoContacto);
      return fecha.getFullYear() === ahora.getFullYear() && fecha.getMonth() === ahora.getMonth();
    }).length;
  });

  // Dona de "leads por estado": rebanadas con % acumulado (from/to) para
  // dibujar un conic-gradient, más una leyenda con texto (nunca solo color).
  readonly leadsPorEstadoPie = computed<EstadoSliceDatum[]>(() => {
    const conteo = {} as Record<EstadoLead, number>;
    for (const l of this.leads()) conteo[l.estado] = (conteo[l.estado] ?? 0) + 1;
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

  readonly leadsPorAsesor = computed<BarDatum[]>(() => {
    const conteo = new Map<string, number>();
    for (const l of this.leads()) {
      conteo.set(l.asesor.nombre, (conteo.get(l.asesor.nombre) ?? 0) + 1);
    }
    const entradas = [...conteo.entries()].sort((a, b) => b[1] - a[1]).slice(0, PALETTE_BAR_CLASS.length);
    const max = Math.max(1, ...entradas.map(([, valor]) => valor));
    return entradas.map(([nombre, value], i) => ({
      label: nombre,
      value,
      pct: (value / max) * 100,
      barClass: PALETTE_BAR_CLASS[i % PALETTE_BAR_CLASS.length],
    }));
  });

  readonly leadsPorDesarrollo = computed<BarDatum[]>(() => {
    const conteo = new Map<string, number>();
    for (const l of this.leads()) {
      conteo.set(l.desarrollo.nombre, (conteo.get(l.desarrollo.nombre) ?? 0) + 1);
    }
    const entradas = [...conteo.entries()].sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...entradas.map(([, valor]) => valor));
    return entradas.map(([nombre, value], i) => ({
      label: nombre,
      value,
      pct: (value / max) * 100,
      barClass: DESARROLLO_BAR_CLASSES[i % DESARROLLO_BAR_CLASSES.length],
    }));
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

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      this.leads.set(await this.leadsService.listar());
      setTimeout(() => this.displayedRingOffset.set(this.ringOffset()), 60);
    } catch {
      // El dashboard es informativo: si falla, el resto del panel sigue usable.
    } finally {
      this.isLoading.set(false);
    }
  }
}
