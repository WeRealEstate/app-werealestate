import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { LeadsService } from '../../../../core/services/leads.service';
import { ESTADO_LEAD_LABELS, EstadoLead, Lead } from '../../../../core/models/lead.model';

interface BarDatum {
  label: string;
  value: number;
  pct: number;
  colorClass: string;
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

// Progresión del embudo: rampa ordinal de un solo hue. Los dos estados
// terminales usan los colores de estado fijos (good/critical), no la rampa.
const ESTADO_COLOR_CLASS: Record<EstadoLead, string> = {
  NUEVO: 'bg-[#86b6ef] dark:bg-[#cde2fb]',
  CONTACTADO: 'bg-[#5598e7] dark:bg-[#86b6ef]',
  INTERESADO: 'bg-[#2a78d6] dark:bg-[#5598e7]',
  CITA_AGENDADA: 'bg-[#1c5cab] dark:bg-[#2a78d6]',
  NEGOCIACION: 'bg-[#104281] dark:bg-[#1c5cab]',
  CERRADO_GANADO: 'bg-[#0ca30c]',
  CERRADO_PERDIDO: 'bg-[#d03b3b]',
};

const DESARROLLO_COLOR_CLASSES = ['bg-[#243fb8] dark:bg-[#3987e5]', 'bg-[#1baf7a] dark:bg-[#199e70]'];

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

  readonly valorPipelineFormateado = computed(() =>
    this.currencyFormatter.format(
      this.leads()
        .filter((l) => l.estado !== 'CERRADO_GANADO' && l.estado !== 'CERRADO_PERDIDO')
        .reduce((suma, l) => suma + (l.valorEstimado ?? 0), 0),
    ),
  );

  readonly valorCerradoGanadoFormateado = computed(() =>
    this.currencyFormatter.format(
      this.leads()
        .filter((l) => l.estado === 'CERRADO_GANADO')
        .reduce((suma, l) => suma + (l.valorEstimado ?? 0), 0),
    ),
  );

  readonly leadsPorEstado = computed<BarDatum[]>(() => {
    const conteo = {} as Record<EstadoLead, number>;
    for (const l of this.leads()) conteo[l.estado] = (conteo[l.estado] ?? 0) + 1;
    const max = Math.max(1, ...ESTADO_ORDEN.map((estado) => conteo[estado] ?? 0));
    return ESTADO_ORDEN.map((estado) => {
      const value = conteo[estado] ?? 0;
      return {
        label: ESTADO_LEAD_LABELS[estado],
        value,
        pct: (value / max) * 100,
        colorClass: ESTADO_COLOR_CLASS[estado],
      };
    });
  });

  readonly leadsPorAsesor = computed<BarDatum[]>(() => {
    const conteo = new Map<string, number>();
    for (const l of this.leads()) {
      conteo.set(l.asesor.nombre, (conteo.get(l.asesor.nombre) ?? 0) + 1);
    }
    const entradas = [...conteo.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
    const max = Math.max(1, ...entradas.map(([, valor]) => valor));
    return entradas.map(([nombre, value]) => ({
      label: nombre,
      value,
      pct: (value / max) * 100,
      colorClass: 'bg-we-primary',
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
      colorClass: DESARROLLO_COLOR_CLASSES[i % DESARROLLO_COLOR_CLASSES.length],
    }));
  });

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      this.leads.set(await this.leadsService.listar());
    } catch {
      // El dashboard es informativo: si falla, el resto del panel sigue usable.
    } finally {
      this.isLoading.set(false);
    }
  }
}
