import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideChevronDown, LucideSearch } from '@lucide/angular';
import { Comision, ComisionDetalle } from '../../../../core/models/finanzas.model';
import { Amortizacion, fechaCorta } from '../../../../core/models/amortizacion.model';
import { FinanzasService } from '../../../../core/services/finanzas.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { dinero, mensajeDe } from '../../../../core/utils/formato-pantalla';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

/** Cómo va una comisión, en palabras del panel (no son los estados internos del servidor). */
type Situacion = 'CANCELADA' | 'PAGADA' | 'SIN_FIRMAR' | 'POR_PAGAR' | 'AL_CORRIENTE';

const SITUACION: Record<Situacion, { texto: string; clase: string }> = {
  CANCELADA: { texto: 'Cancelada', clase: 'bg-surface-2 text-ink-muted' },
  PAGADA: { texto: 'Pagada', clase: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300' },
  SIN_FIRMAR: { texto: 'Sin firmar', clase: 'bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300' },
  POR_PAGAR: { texto: 'Por pagar', clase: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300' },
  AL_CORRIENTE: { texto: 'Al corriente', clase: 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300' },
};

interface GrupoAsesor {
  clave: string;
  nombre: string;
  externo: boolean;
  comisiones: Comision[];
  total: number;
  pagado: number;
  porPagar: number;
}

/** Un abono del cliente con la parte que le toca al asesor (la mitad, hasta completar la comisión). */
interface FilaCliente {
  concepto: string;
  fecha: string;
  monto: number;
  saldoTerreno: number;
  aComision: number;
}

/** Comisiones (12.30.0): por asesor → sus ventas → la ficha de cada venta, como la hoja del Excel. La regla es la del
 * servidor (ComisionService): la mitad de cada abono hasta completar la comisión; sin contrato firmado se acumula
 * pero no se paga, y lo liberado se paga el sábado siguiente al abono o a la firma. */
@Component({
  selector: 'app-comisiones-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, LucideArrowLeft, LucideChevronDown, LucideSearch, WeLoaderComponent],
  templateUrl: './comisiones-panel.component.html',
})
export class ComisionesPanelComponent {
  private readonly finanzas = inject(FinanzasService);
  private readonly ventas = inject(VentasService);

  readonly dinero = dinero;
  readonly fecha = fechaCorta;
  readonly situaciones = SITUACION;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly comisiones = signal<Comision[]>([]);
  readonly busqueda = signal('');
  readonly abiertos = signal<ReadonlySet<string>>(new Set());

  /** Ficha abierta (una comisión) y lo que se carga para ella. */
  readonly ficha = signal<Comision | null>(null);
  readonly detalle = signal<ComisionDetalle | null>(null);
  readonly amortizacion = signal<Amortizacion | null>(null);
  readonly cargandoFicha = signal(false);
  readonly errorFicha = signal<string | null>(null);

  /** Sin canceladas ni las de ventas eliminadas (12.30.2, decisión del usuario: no aparecen ni cuentan en los totales). */
  readonly vigentes = computed(() => this.comisiones().filter((c) => !c.cancelada && c.ventaId !== null && !c.ventaEliminada));

  readonly totales = computed(() => {
    let total = 0;
    let pagado = 0;
    for (const c of this.vigentes()) {
      total += c.monto;
      pagado += c.entregado;
    }
    return { total, pagado, saldo: Math.max(0, total - pagado) };
  });

  readonly grupos = computed<GrupoAsesor[]>(() => {
    const q = normalizar(this.busqueda());
    const mapa = new Map<string, GrupoAsesor>();
    for (const c of this.vigentes()) {
      if (q && !normalizar(`${c.asesorNombre} ${c.cliente} ${c.ventaNumero ?? ''}`).includes(q)) continue;
      let g = mapa.get(c.asesorClave);
      if (!g) {
        g = { clave: c.asesorClave, nombre: c.asesorNombre, externo: c.asesorExterno, comisiones: [], total: 0, pagado: 0, porPagar: 0 };
        mapa.set(c.asesorClave, g);
      }
      g.comisiones.push(c);
      g.total += c.monto;
      g.pagado += c.entregado;
      g.porPagar += c.porEntregar;
    }
    const orden = new Intl.Collator('es-MX', { sensitivity: 'base' });
    for (const g of mapa.values()) g.comisiones.sort((a, b) => b.fechaCreacion.localeCompare(a.fechaCreacion));
    return [...mapa.values()].sort((a, b) => orden.compare(a.nombre, b.nombre));
  });

  /** Abonos del cliente en orden, con lo que cada uno aporta a la comisión (mismo cálculo que el servidor). */
  readonly filasCliente = computed<FilaCliente[]>(() => {
    const a = this.amortizacion();
    const c = this.ficha();
    if (!a || !c) return [];
    const pagos: { concepto: string; fecha: string; monto: number }[] = [];
    for (const fila of a.detalleMensual) {
      for (const p of fila.pagos) pagos.push({ concepto: fila.concepto, fecha: p.fecha, monto: p.monto });
    }
    pagos.sort((x, y) => x.fecha.localeCompare(y.fecha));
    let saldo = a.precio;
    let falta = c.monto;
    return pagos.map((p) => {
      saldo -= p.monto;
      const aporte = Math.max(0, Math.min(Math.round(p.monto * 50) / 100, falta));
      falta -= aporte;
      return { ...p, saldoTerreno: Math.max(0, saldo), aComision: aporte };
    });
  });

  /** Pagos al asesor, del más viejo al más nuevo, con el saldo de la comisión después de cada uno. */
  readonly pagosAsesor = computed(() => {
    const d = this.detalle();
    if (!d) return [];
    let pagado = 0;
    return [...d.entregas]
      .sort((a, b) => a.fecha.localeCompare(b.fecha) || a.id - b.id)
      .map((e, i) => {
        pagado += e.monto;
        return { numero: i + 1, entrega: e, saldo: Math.max(0, d.comision.monto - pagado) };
      });
  });

  constructor() {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.comisiones.set(await this.finanzas.listarComisiones());
    } catch (e) {
      this.error.set(mensajeDe(e, 'No se pudieron cargar las comisiones.'));
    } finally {
      this.cargando.set(false);
    }
  }

  situacion(c: Comision): Situacion {
    if (c.cancelada) return 'CANCELADA';
    if (c.entregado >= c.monto - 0.005 && c.monto > 0) return 'PAGADA';
    if (c.bloqueadaPorContrato) return 'SIN_FIRMAR';
    if (c.porEntregar > 0) return 'POR_PAGAR';
    return 'AL_CORRIENTE';
  }

  /** Lo que el cliente ya hizo ganar al asesor y se puede pagar (con firma); sin firma, va como retenido. */
  liberada(c: Comision): number {
    return c.bloqueadaPorContrato ? 0 : c.devengado;
  }

  alternar(clave: string): void {
    const s = new Set(this.abiertos());
    if (!s.delete(clave)) s.add(clave);
    this.abiertos.set(s);
  }

  async abrirFicha(c: Comision): Promise<void> {
    this.ficha.set(c);
    this.detalle.set(null);
    this.amortizacion.set(null);
    this.errorFicha.set(null);
    this.cargandoFicha.set(true);
    window.scrollTo({ top: 0 });
    try {
      const [detalle, amortizacion] = await Promise.all([
        this.finanzas.detalleComision(c.id),
        c.ventaId !== null ? this.ventas.amortizacion(c.ventaId) : Promise.resolve(null),
      ]);
      if (this.ficha()?.id !== c.id) return;
      this.detalle.set(detalle);
      this.ficha.set(detalle.comision);
      this.amortizacion.set(amortizacion);
    } catch (e) {
      this.errorFicha.set(mensajeDe(e, 'No se pudo cargar la ficha de la comisión.'));
    } finally {
      this.cargandoFicha.set(false);
    }
  }

  cerrarFicha(): void {
    this.ficha.set(null);
  }
}

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
