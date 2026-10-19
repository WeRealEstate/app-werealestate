import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideChevronRight } from '@lucide/angular';
import { AuthService } from '../../../core/services/auth.service';
import { FinanzasService } from '../../../core/services/finanzas.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { ComisionesPorEntregar, IngresoMes } from '../../../core/models/finanzas.model';
import { ESTADO_LOTE_LABELS, ESTADOS_LOTE_ORDEN, EstadoLote, ResumenLotes } from '../../../core/models/lote.model';
import { AsignarTareasComponent } from '../../../shared/asignar-tareas/asignar-tareas.component';
import { DesempenoGeneralComponent } from './desempeno-general/desempeno-general.component';
import { AdminTareasComponent } from './admin-tareas/admin-tareas.component';

/** Color de cada estado en la barra de inventario: los mismos tonos del plano (verde disponible, ámbar
 * apartado, morado a plazo, gris con dinero, azul We en firma, rojo vendido), ajustados y validados con el
 * validador de paletas de la skill de dataviz para que verde y ámbar se distingan también con daltonismo
 * (claro: fondo blanco; oscuro: tarjeta #131d3b). El gris queda poco saturado a propósito, como en el
 * plano; por eso cada estado va siempre con su nombre y su número en la lista de abajo. */
const ESTADO_BARRA_CLASES: Record<EstadoLote, string> = {
  DISPONIBLE: 'bg-[#15803d] dark:bg-[#4ade80]',
  APARTADO: 'bg-[#f59e0b]',
  APARTADO_A_PLAZO: 'bg-[#a855f7] dark:bg-[#c084fc]',
  APARTADO_CON_DINERO: 'bg-[#94a3b8] dark:bg-[#cbd5e1]',
  EN_PROCESO_DE_FIRMA: 'bg-[#243fb8] dark:bg-[#5b7cf0]',
  VENDIDO: 'bg-[#ef4444] dark:bg-[#f87171]',
};

const MONEDA = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN', maximumFractionDigits: 0 });

function mesActual(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** Inicio del admin: lo primero que se ve es cómo va el negocio (inventario de cada desarrollo, cobranza del
 * mes, comisiones del sábado y leads fríos); debajo, el desempeño de leads y las tareas. Cada cifra lleva a
 * su pantalla con el filtro ya puesto. Si una consulta falla, esa cifra muestra un guion y las demás siguen. */
@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [RouterLink, DecimalPipe, LucideChevronRight, AsignarTareasComponent, DesempenoGeneralComponent, AdminTareasComponent],
  templateUrl: './admin-panel.component.html',
})
export class AdminPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly finanzasService = inject(FinanzasService);
  readonly user = this.auth.currentUser;

  readonly estados = ESTADOS_LOTE_ORDEN;
  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly estadoClases = ESTADO_BARRA_CLASES;

  readonly inventario = signal<ResumenLotes[] | null>(null);
  readonly inventarioError = signal(false);
  readonly cobranza = signal<IngresoMes | null>(null);
  readonly cobranzaError = signal(false);
  readonly comisiones = signal<ComisionesPorEntregar | null>(null);
  readonly comisionesError = signal(false);
  readonly totalFrios = signal<number | null>(null);

  readonly nombreMes = new Intl.DateTimeFormat('es-MX', { month: 'long' }).format(new Date());

  /** Cuánto de lo esperado este mes ya se cobró, en % (tope 100) para la barra de avance. */
  readonly avanceCobranza = computed(() => {
    const c = this.cobranza();
    if (!c || c.esperado <= 0) return 0;
    return Math.min(100, Math.round((c.recibido / c.esperado) * 100));
  });

  /** Asesores distintos que cobran este sábado (internos o externos). */
  readonly asesoresSabado = computed(() => new Set(this.comisiones()?.items.map((i) => i.comision.asesorNombre) ?? []).size);

  readonly fechaSabado = computed(() => {
    const iso = this.comisiones()?.sabado;
    if (!iso) return '';
    const [a, m, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('es-MX', { day: 'numeric', month: 'long' }).format(new Date(a, m - 1, d));
  });

  constructor() {
    this.lotesService
      .resumen()
      .then((r) => this.inventario.set(r))
      .catch(() => this.inventarioError.set(true));
    const mes = mesActual();
    this.finanzasService
      .ingresos(mes, mes, null)
      .then((r) => this.cobranza.set(r.mesActual))
      .catch(() => this.cobranzaError.set(true));
    this.finanzasService
      .comisionesPorEntregar()
      .then((r) => this.comisiones.set(r))
      .catch(() => this.comisionesError.set(true));
    this.leadsService
      .listarFrios()
      .then((frios) => this.totalFrios.set(frios.length))
      .catch(() => this.totalFrios.set(null));
  }

  money(valor: number): string {
    return MONEDA.format(valor);
  }

  /** Ancho de un estado en la barra, en % del total del desarrollo. */
  porcentaje(r: ResumenLotes, estado: EstadoLote): number {
    return r.total > 0 ? (r.porEstado[estado] / r.total) * 100 : 0;
  }

  /** Descripción completa de la barra para lectores de pantalla (la barra sola es solo color). */
  descripcionBarra(r: ResumenLotes): string {
    return `${r.desarrollo}: ` + this.estados.map((e) => `${r.porEstado[e]} ${this.estadoLabels[e].toLowerCase()}`).join(', ');
  }
}
