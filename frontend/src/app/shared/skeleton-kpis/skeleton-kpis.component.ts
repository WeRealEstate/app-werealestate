import { Component, computed, input } from '@angular/core';

/** Tarjetas "esqueleto" de KPI mientras cargan los datos: barras con brillo y la "e" girando en la esquina. */
@Component({
  selector: 'app-skeleton-kpis',
  templateUrl: './skeleton-kpis.component.html',
})
export class SkeletonKpisComponent {
  readonly cantidad = input(4);
  readonly conGrafica = input(true);
  readonly columnas = input('mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4');

  readonly tarjetas = computed(() => Array.from({ length: this.cantidad() }, (_, i) => i));
  readonly barras = [40, 65, 50, 80, 60, 90, 70];
}
