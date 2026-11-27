import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { GastosListComponent } from '../gastos/gastos-list/gastos-list.component';
import { FinanzasIngresosComponent } from './finanzas-ingresos.component';
import { FinanzasValorComponent } from './finanzas-valor.component';
import { ComisionesPanelComponent } from './comisiones/comisiones-panel.component';

type Pestana = 'comisiones' | 'ingresos' | 'valor' | 'gastos';

/** Finanzas: muestra la sección que pide el menú (`?pestana=`). Comisiones se rehízo en 12.30.0
 * (comisiones/comisiones-panel). */
@Component({
  selector: 'app-finanzas',
  standalone: true,
  imports: [ComisionesPanelComponent, FinanzasIngresosComponent, FinanzasValorComponent, GastosListComponent],
  templateUrl: './finanzas.component.html',
})
export class FinanzasComponent {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly pestana = signal<Pestana>('comisiones');

  /** Comisiones, Ingresos y Valor vendido dependen del módulo Finanzas; Gastos, del módulo Gastos. */
  private readonly pestanas: { id: Pestana; label: string }[] = [
    ...(this.auth.tieneModulo('FINANZAS')
      ? [
          { id: 'comisiones' as Pestana, label: 'Comisiones' },
          { id: 'ingresos' as Pestana, label: 'Ingresos' },
          { id: 'valor' as Pestana, label: 'Valor vendido' },
        ]
      : []),
    ...(this.auth.tieneModulo('GASTOS') ? [{ id: 'gastos' as Pestana, label: 'Gastos' }] : []),
  ];
  readonly titulo = computed(() => this.pestanas.find((t) => t.id === this.pestana())?.label ?? 'Finanzas');

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const pedida = params.get('pestana');
      const seleccion = this.pestanas.find((t) => t.id === pedida) ?? this.pestanas[0];
      if (seleccion) this.pestana.set(seleccion.id);
    });
  }
}
