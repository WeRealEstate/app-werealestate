import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import { FinanzasIngresos, IngresoDetalle, IngresoMes } from '../../../core/models/finanzas.model';
import { FinanzasService } from '../../../core/services/finanzas.service';
import { LeadsService } from '../../../core/services/leads.service';
import { ToastService } from '../../../core/services/toast.service';

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Pestaña "Ingresos": lo esperado cada mes (enganche, mensualidades y aportaciones en su día de
 * pago) contra lo realmente recibido, con el atraso acumulado. Ver backend FinanzasService. */
@Component({
  selector: 'app-finanzas-ingresos',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './finanzas-ingresos.component.html',
})
export class FinanzasIngresosComponent {
  private readonly finanzasService = inject(FinanzasService);
  private readonly leadsService = inject(LeadsService);
  private readonly toast = inject(ToastService);

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly datos = signal<FinanzasIngresos | null>(null);
  readonly desarrollos = signal<Desarrollo[]>([]);

  // Por omisión: dos meses atrás hasta un año adelante del mes actual.
  readonly desde = signal(sumarMeses(mesActual(), -2));
  readonly hasta = signal(sumarMeses(mesActual(), 11));
  readonly desarrolloId = signal<number | null>(null);

  readonly expandido = signal<string | null>(null);
  readonly detalle = signal<IngresoDetalle | null>(null);

  /** Cuánto de lo esperado este mes ya se recibió (para la barra del resumen). */
  readonly avanceMes = computed(() => {
    const m = this.datos()?.mesActual;
    if (!m || m.esperado <= 0) return 0;
    return Math.min(100, Math.round((m.recibido / m.esperado) * 100));
  });

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    void this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.expandido.set(null);
    this.detalle.set(null);
    try {
      this.datos.set(await this.finanzasService.ingresos(this.desde() || null, this.hasta() || null, this.desarrolloId()));
    } catch {
      this.errorMessage.set('No se pudieron cargar los ingresos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  cambiarDesarrollo(valor: string): void {
    this.desarrolloId.set(valor ? Number(valor) : null);
    void this.cargar();
  }

  cambiarDesde(valor: string): void {
    this.desde.set(valor);
    void this.cargar();
  }

  cambiarHasta(valor: string): void {
    this.hasta.set(valor);
    void this.cargar();
  }

  async alternarMes(m: IngresoMes): Promise<void> {
    if (this.expandido() === m.mes) {
      this.expandido.set(null);
      this.detalle.set(null);
      return;
    }
    this.expandido.set(m.mes);
    this.detalle.set(null);
    try {
      this.detalle.set(await this.finanzasService.ingresosDelMes(m.mes, this.desarrolloId()));
    } catch {
      this.toast.error('No se pudo cargar el detalle del mes.');
    }
  }

  nombreMes(mes: string): string {
    const [anio, m] = mes.split('-').map(Number);
    return `${MESES[m - 1]} ${anio}`;
  }

  fecha(iso: string): string {
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

function mesActual(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function sumarMeses(mes: string, delta: number): string {
  const [anio, m] = mes.split('-').map(Number);
  const d = new Date(anio, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
