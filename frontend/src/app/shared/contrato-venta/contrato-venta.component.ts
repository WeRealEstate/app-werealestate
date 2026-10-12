import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Venta } from '../../core/models/venta.model';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

/** Contrato de la venta: si ya está firmado y desde cuándo. Con la regla de comisión por tipo de
 * abono (ventas nuevas), la comisión se acumula siempre pero solo se puede entregar con el contrato
 * firmado; al firmar, lo acumulado queda disponible el sábado siguiente a la firma. */
@Component({
  selector: 'app-contrato-venta',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="mt-6 rounded-2xl border border-border bg-surface p-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="font-heading text-base font-semibold text-ink">Contrato</h2>
          <p class="mt-0.5 text-xs text-ink-muted">
            La comisión del asesor solo se puede entregar con el contrato firmado; mientras tanto se va acumulando.
          </p>
        </div>
        @if (venta().contratoFirmado) {
          <span class="rounded-full bg-green-100 px-2.5 py-1 text-xs font-medium text-green-700 dark:bg-green-500/15 dark:text-green-300">
            Firmado{{ venta().contratoFechaFirma ? ' el ' + fecha(venta().contratoFechaFirma!) : '' }}
          </span>
        } @else {
          <span class="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">Sin firmar</span>
        }
      </div>

      <form (ngSubmit)="guardar()" class="mt-4 flex flex-wrap items-end gap-4">
        <label class="flex items-center gap-2.5 text-sm text-ink cursor-pointer">
          <input
            type="checkbox" name="firmado"
            [ngModel]="firmado()" (ngModelChange)="alternar($event)"
            class="h-4 w-4 rounded border-border text-we-primary focus:ring-we-primary/50"
          />
          Contrato firmado
        </label>
        @if (firmado()) {
          <div>
            <label for="contrato-fecha" class="mb-1 block text-xs font-medium text-ink-muted">Fecha de firma *</label>
            <input
              id="contrato-fecha" name="fechaFirma" type="date" [max]="hoy"
              [ngModel]="fechaFirma()" (ngModelChange)="fechaFirma.set($event)"
              class="rounded-lg border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50"
            />
          </div>
        }
        <button
          type="submit" [disabled]="guardando() || !cambio()"
          class="rounded-lg bg-we-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-we-primary/90 disabled:opacity-50 disabled:cursor-not-allowed min-h-[44px]"
        >
          {{ guardando() ? 'Guardando…' : 'Guardar contrato' }}
        </button>
      </form>
      @if (error()) {
        <p class="mt-3 text-xs text-red-600 dark:text-red-400" role="alert">{{ error() }}</p>
      }
    </div>
  `,
})
export class ContratoVentaComponent {
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly venta = input.required<Venta>();
  /** Tras guardar, el padre vuelve a cargar la venta. */
  readonly guardado = output<void>();

  readonly hoy = new Date().toISOString().slice(0, 10);
  readonly firmado = signal(false);
  readonly fechaFirma = signal('');
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Se reinicia con lo guardado cada vez que la venta se vuelve a cargar.
    effect(() => {
      const v = this.venta();
      this.firmado.set(v.contratoFirmado);
      this.fechaFirma.set(v.contratoFechaFirma ?? '');
    });
  }

  cambio(): boolean {
    const v = this.venta();
    return this.firmado() !== v.contratoFirmado || (this.firmado() && this.fechaFirma() !== (v.contratoFechaFirma ?? ''));
  }

  alternar(valor: boolean): void {
    this.firmado.set(valor);
    if (valor && !this.fechaFirma()) this.fechaFirma.set(this.hoy);
  }

  fecha(iso: string): string {
    return iso.split('-').reverse().join('/');
  }

  async guardar(): Promise<void> {
    if (this.guardando() || !this.cambio()) return;
    if (this.firmado() && !this.fechaFirma()) {
      this.error.set('Indica la fecha de firma del contrato.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    try {
      await this.ventasService.registrarContrato(this.venta().id, this.firmado(), this.firmado() ? this.fechaFirma() : null);
      this.toast.success(this.firmado() ? 'Contrato registrado como firmado.' : 'Contrato registrado como sin firmar.');
      this.guardado.emit();
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo guardar el contrato.',
      );
    } finally {
      this.guardando.set(false);
    }
  }
}
