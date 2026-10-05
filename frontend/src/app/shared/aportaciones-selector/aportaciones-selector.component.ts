import { Component, computed, effect, input, model, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Aportacion,
  MESES_ABREVIADO,
  MESES_NOMBRE,
  aniosElegibles,
  claveAportacion,
  esElegible,
  ordenarAportaciones,
  totalAportaciones,
} from '../../core/utils/aportaciones';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

/** Elige las aportaciones de un plan: por cada año calendario, 12 botones de mes (hasta 12 por año) y,
 * por cada mes elegido, su monto.
 *
 * Autocompletado: al escribir el monto de una aportación, las demás que no se hayan editado a mano
 * toman ese mismo monto; todas siguen editables. Con `montoSugerido` (Promoción) las que no se
 * editaron a mano siguen ese monto sugerido (reparto igual) y editar una no cambia las otras. */
@Component({
  selector: 'app-aportaciones-selector',
  standalone: true,
  imports: [FormsModule, MonedaInputDirective],
  templateUrl: './aportaciones-selector.component.html',
})
export class AportacionesSelectorComponent {
  /** Mes del primer pago del plan. */
  readonly inicio = input.required<Date>();
  readonly meses = input.required<number>();
  readonly montoSugerido = input<number | null>(null);
  readonly valor = model<Aportacion[]>([]);

  readonly mesesAbreviados = MESES_ABREVIADO;
  readonly mesesNombre = MESES_NOMBRE;

  private readonly ultimoMonto = signal<number | null>(null);

  readonly anios = computed(() => aniosElegibles(this.inicio(), this.meses()));
  readonly total = computed(() => totalAportaciones(this.valor()));

  constructor() {
    // Si cambia el plazo o la fecha de inicio, se descartan las aportaciones que quedaron fuera.
    effect(() => {
      const anios = this.anios();
      const actuales = untracked(() => this.valor());
      const vigentes = actuales.filter((a) => esElegible(a, anios));
      if (vigentes.length !== actuales.length) this.valor.set(vigentes);
    });

    // Promoción: las aportaciones no editadas a mano siguen el reparto igual sugerido.
    effect(() => {
      const sugerido = this.montoSugerido();
      if (sugerido === null) return;
      const actuales = untracked(() => this.valor());
      if (actuales.every((a) => a.manual || a.monto === sugerido)) return;
      this.valor.set(actuales.map((a) => (a.manual ? a : { ...a, monto: sugerido })));
    });
  }

  elegida(anio: number, mes: number): boolean {
    return this.valor().some((a) => a.anio === anio && a.mes === mes);
  }

  aportacionesDe(anio: number): Aportacion[] {
    return this.valor().filter((a) => a.anio === anio);
  }

  alternar(anio: number, mes: number): void {
    const actuales = this.valor();
    if (this.elegida(anio, mes)) {
      this.valor.set(actuales.filter((a) => !(a.anio === anio && a.mes === mes)));
      return;
    }
    const inicial = this.montoSugerido() ?? this.ultimoMonto();
    this.valor.set(ordenarAportaciones([...actuales, { anio, mes, monto: inicial, manual: false }]));
  }

  cambiarMonto(anio: number, mes: number, monto: number | null): void {
    const sugerido = this.montoSugerido() !== null;
    const propagar = !sugerido && monto !== null && monto > 0;
    if (propagar) this.ultimoMonto.set(monto);

    this.valor.set(
      this.valor().map((a) => {
        if (a.anio === anio && a.mes === mes) return { ...a, monto, manual: true };
        return propagar && !a.manual ? { ...a, monto } : a;
      }),
    );
  }

  /** Todas al mismo monto otra vez (el sugerido, o el último escrito), también las editadas a mano. */
  igualar(): void {
    const base = this.montoSugerido() ?? this.ultimoMonto() ?? this.valor().find((a) => a.monto)?.monto ?? null;
    this.valor.set(this.valor().map((a) => ({ ...a, monto: base, manual: false })));
  }

  trackClave(a: Aportacion): string {
    return claveAportacion(a.anio, a.mes);
  }
}
