import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { LucidePlus, LucideRefreshCw, LucideX } from '@lucide/angular';
import { Venta } from '../../core/models/venta.model';
import { VentasService } from '../../core/services/ventas.service';
import { ToastService } from '../../core/services/toast.service';
import { MESES_NOMBRE } from '../../core/utils/aportaciones';

interface FilaAportacion {
  /** "2027-06" */
  mes: string;
  monto: number | null;
}

/**
 * Cambia el plan de pagos de una venta (p. ej. de mensualidades a anualidades). Rehace TODO el plan desde el primer mes
 * de la venta (decisión del usuario): reparte el precio menos el enganche y los abonos ya registrados se vuelven a
 * aplicar en orden; los meses vencidos sin cubrir salen atrasados. La mensualidad que se muestra es una vista previa:
 * la calcula el servidor con la misma regla (monto menos aportaciones, entre los meses sin aportación).
 */
@Component({
  selector: 'app-reestructurar-venta',
  standalone: true,
  imports: [LucidePlus, LucideRefreshCw, LucideX],
  templateUrl: './reestructurar-venta.component.html',
})
export class ReestructurarVentaComponent {
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly venta = input.required<Venta>();
  /** Lo que reparte el plan rehecho: precio menos enganche. */
  readonly saldo = input.required<number>();
  /** Primer mes del plan original ("2026-06"): ahí arranca el plan rehecho. */
  readonly primerMes = input.required<string>();
  readonly guardado = output<void>();

  readonly abierto = signal(false);
  /** Forma de pago del plan nuevo: mensualidades iguales, con enganche / pago inicial o con anualidades/aportaciones. */
  readonly forma = signal<'MSI' | 'ENGANCHE' | 'ANUALIDADES'>('MSI');
  /** Enganche que la venta ya tiene: se conserva tal cual (decisión del usuario). */
  readonly engancheActual = computed(() => {
    const v = this.venta();
    return v.enganche && v.enganche > 0 && (v.engancheLabel === 'Enganche' || v.engancheLabel === 'Pago inicial')
      ? { label: v.engancheLabel, monto: v.enganche }
      : null;
  });
  readonly engancheNuevoLabel = signal<'Enganche' | 'Pago inicial'>('Enganche');
  readonly engancheNuevo = signal<number | null>(null);
  /** Lo que reparten mensualidades y aportaciones: el monto de la venta menos el enganche nuevo, si se agrega. */
  readonly aRepartir = computed(() =>
    this.saldo() - (this.forma() === 'ENGANCHE' && !this.engancheActual() ? this.engancheNuevo() ?? 0 : 0),
  );
  readonly plazo = signal(12);
  readonly diaPago = signal(15);
  readonly primeraEnMesActual = signal(false);
  readonly aportaciones = signal<FilaAportacion[]>([]);
  readonly motivo = signal('');
  readonly password = signal('');
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  /** Los meses del plan nuevo, del primero al último ("2026-12"). */
  readonly meses = computed(() => {
    const [anioInicio, mesInicio] = this.primerMes().split('-').map(Number);
    const inicio = new Date(anioInicio, mesInicio - 1, 1);
    const plazo = Math.max(0, Math.min(600, Math.floor(this.plazo() || 0)));
    return Array.from({ length: plazo }, (_, k) => {
      const d = new Date(inicio.getFullYear(), inicio.getMonth() + k, 1);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    });
  });

  readonly sumaAportaciones = computed(() =>
    this.aportaciones().reduce((s, a) => s + (this.meses().includes(a.mes) && a.monto ? a.monto : 0), 0),
  );

  /** Vista previa de la mensualidad; null si no se puede repartir (el mensaje lo dice). */
  readonly mensualidad = computed<number | null>(() => {
    const conAportacion = new Set(this.aportaciones().filter((a) => this.meses().includes(a.mes)).map((a) => a.mes));
    const regulares = this.meses().length - conAportacion.size;
    const resto = this.aRepartir() - this.sumaAportaciones();
    if (regulares === 0) return Math.abs(resto) <= 0.01 ? 0 : null;
    if (resto <= 0.01) return null;
    return Math.floor((resto / regulares) * 100 + 1e-6) / 100;
  });

  readonly problema = computed<string | null>(() => {
    const plazo = this.plazo();
    if (!plazo || plazo < 1 || plazo > 600) return 'El plazo debe ser de 1 a 600 meses.';
    const dia = this.diaPago();
    if (!dia || dia < 1 || dia > 31) return 'El día de pago debe ser del 1 al 31.';
    if (this.forma() === 'ENGANCHE' && !this.engancheActual()) {
      const e = this.engancheNuevo();
      if (!e || e <= 0) return 'Escribe el monto del enganche o pago inicial.';
      if (e >= this.saldo()) return 'El enganche no puede cubrir todo el monto.';
    }
    if (this.forma() === 'ANUALIDADES' && this.aportaciones().length === 0) return 'Agrega al menos una aportación o anualidad.';
    const meses = this.aportaciones().map((a) => a.mes);
    if (new Set(meses).size !== meses.length) return 'Hay dos aportaciones en el mismo mes.';
    if (this.aportaciones().some((a) => !this.meses().includes(a.mes))) return 'Hay una aportación fuera del plazo nuevo.';
    if (this.aportaciones().some((a) => !a.monto || a.monto <= 0)) return 'Escribe el monto de cada aportación.';
    if (this.mensualidad() === null) {
      return this.meses().length === this.aportaciones().length
        ? 'Si todos los meses son aportaciones, deben sumar el saldo.'
        : 'Las aportaciones cubren todo el saldo: reduce alguna o el plazo.';
    }
    return null;
  });

  readonly puedeGuardar = computed(
    () => !this.problema() && this.motivo().trim().length > 0 && this.password().length > 0 && !this.guardando(),
  );

  abrir(): void {
    // Arranca con el plan vigente de la venta (plazo, día de pago y sus aportaciones/anualidades) para solo ajustarlo.
    const v = this.venta();
    this.plazo.set(v.plazoMeses || 12);
    this.diaPago.set(v.diaPago || 15);
    this.primeraEnMesActual.set(false);
    this.aportaciones.set(
      (v.aportaciones ?? []).map((a) => ({ mes: `${a.anio}-${String(a.mes).padStart(2, '0')}`, monto: a.monto })),
    );
    this.engancheNuevo.set(null);
    this.engancheNuevoLabel.set('Enganche');
    this.forma.set(this.aportaciones().length > 0 ? 'ANUALIDADES' : this.engancheActual() ? 'ENGANCHE' : 'MSI');
    this.motivo.set('');
    this.password.set('');
    this.error.set(null);
    this.abierto.set(true);
  }

  cerrar(): void {
    if (!this.guardando()) this.abierto.set(false);
  }

  elegirForma(f: 'MSI' | 'ENGANCHE' | 'ANUALIDADES'): void {
    this.forma.set(f);
    // Solo «anualidades» lleva aportaciones; al cambiar a otra forma se quitan.
    if (f !== 'ANUALIDADES') this.aportaciones.set([]);
    else if (this.aportaciones().length === 0) this.agregarAportacion();
  }

  agregarAportacion(): void {
    const usados = new Set(this.aportaciones().map((a) => a.mes));
    // Por defecto, cada 12 meses (una anualidad): el siguiente mes 12, 24… libre del plan.
    const meses = this.meses();
    const sugerido = meses.find((m, i) => (i + 1) % 12 === 0 && !usados.has(m)) ?? meses.find((m) => !usados.has(m));
    if (!sugerido) return;
    this.aportaciones.update((l) => [...l, { mes: sugerido, monto: null }]);
  }

  quitarAportacion(i: number): void {
    this.aportaciones.update((l) => l.filter((_, k) => k !== i));
  }

  cambiarAportacion(i: number, cambio: Partial<FilaAportacion>): void {
    this.aportaciones.update((l) => l.map((a, k) => (k === i ? { ...a, ...cambio } : a)));
  }

  numero(valor: string): number | null {
    const n = Number(valor);
    return valor.trim() === '' || Number.isNaN(n) ? null : n;
  }

  etiquetaMes(ym: string): string {
    const [anio, mes] = ym.split('-').map(Number);
    return `${MESES_NOMBRE[mes - 1]} ${anio}`;
  }

  money(valor: number): string {
    return valor.toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  async guardar(): Promise<void> {
    if (!this.puedeGuardar()) return;
    this.guardando.set(true);
    this.error.set(null);
    try {
      await this.ventasService.reestructurar(this.venta().id, {
        password: this.password(),
        motivo: this.motivo().trim(),
        plazoMeses: this.plazo(),
        diaPago: this.diaPago(),
        primeraEnMesActual: this.primeraEnMesActual(),
        engancheLabel: this.forma() === 'ENGANCHE' && !this.engancheActual() ? this.engancheNuevoLabel() : null,
        enganche: this.forma() === 'ENGANCHE' && !this.engancheActual() ? this.engancheNuevo() : null,
        aportaciones: this.aportaciones().map((a) => {
          const [anio, mes] = a.mes.split('-').map(Number);
          return { anio, mes, monto: a.monto ?? 0 };
        }),
      });
      this.abierto.set(false);
      this.toast.success('Plan de pagos reestructurado.');
      this.guardado.emit();
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : 'No se pudo reestructurar la venta. Intenta de nuevo.',
      );
    } finally {
      this.guardando.set(false);
    }
  }
}
