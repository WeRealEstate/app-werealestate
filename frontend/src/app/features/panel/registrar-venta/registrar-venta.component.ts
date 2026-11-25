import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RegistroVentaPanelService, WeFormPanelLista } from '../../../core/services/registro-venta-panel.service';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';

/** /panel/registrar-venta: los formularios activos de SAMAI y Nanuu que el asesor puede llenar desde su panel. */
@Component({
  selector: 'app-registrar-venta',
  standalone: true,
  imports: [RouterLink, WeLoaderComponent],
  template: `
    <div class="page-enter max-w-2xl">
      <h1 class="font-heading text-2xl font-semibold text-ink">Registrar venta</h1>
      <p class="mt-1 text-sm text-ink-muted">Elige el formulario que vas a llenar. La venta queda a tu nombre.</p>

      @if (cargando()) {
        <app-we-loader texto="Cargando formularios…" />
      } @else if (error()) {
        <p class="mt-6 text-sm text-red-600">{{ error() }}</p>
      } @else if (grupos().length === 0) {
        <div class="mt-6 rounded-xl border border-dashed border-border p-8 text-center text-sm text-ink-muted">
          No hay formularios activos por ahora.
        </div>
      } @else {
        @for (g of grupos(); track g.proyecto) {
          <h2 class="mt-6 font-heading text-sm font-semibold uppercase tracking-wide text-ink">{{ g.nombre }}</h2>
          <div class="mt-3 space-y-3">
            @for (f of g.formularios; track f.slug) {
              <a [routerLink]="['/panel/registrar-venta', f.slug]" [queryParams]="{ proyecto: f.proyecto }" data-testid="formulario-panel"
                class="block rounded-xl border border-border bg-surface p-4 shadow-sm hover:border-we-primary active:scale-[0.99] transition-all duration-150">
                <p class="font-heading text-base font-semibold text-ink">{{ f.nombre }}</p>
                @if (f.descripcion) { <p class="mt-0.5 text-sm text-ink-muted">{{ f.descripcion }}</p> }
              </a>
            }
          </div>
        }
      }
    </div>
  `,
})
export class RegistrarVentaComponent {
  private readonly service = inject(RegistroVentaPanelService);

  readonly formularios = signal<WeFormPanelLista[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly grupos = computed(() =>
    ([['samai', 'SAMAI Campestre'], ['nanuu', 'Aldea Nanuu']] as const)
      .map(([proyecto, nombre]) => ({ proyecto, nombre, formularios: this.formularios().filter((f) => f.proyecto === proyecto) }))
      .filter((g) => g.formularios.length > 0),
  );

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.formularios.set(await this.service.listar());
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudieron cargar los formularios.',
      );
    } finally {
      this.cargando.set(false);
    }
  }
}
