import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MEDIO_FIRMA_LABELS, MedioFirma, Venta } from '../../core/models/venta.model';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

/** Contrato de la venta: si ya está firmado, desde cuándo y cómo (en físico o por DocuSign), más un
 * enlace opcional a su carpeta de Drive. Con la regla de comisión por tipo de abono (ventas nuevas),
 * la comisión se acumula siempre pero solo se puede entregar con el contrato firmado; al firmar, lo
 * acumulado queda disponible el sábado siguiente a la firma. */
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
            Al firmarlo, los lotes de la venta pasan a Vendido; si se le quita la firma, regresan a En proceso de firma.
          </p>
        </div>
        @if (venta().contratoFirmado) {
          <span class="rounded-full bg-green-100 px-2.5 py-1 text-xs font-medium text-green-700 dark:bg-green-500/15 dark:text-green-300">
            Firmado{{ venta().contratoFechaFirma ? ' el ' + fecha(venta().contratoFechaFirma!) : '' }}{{ venta().contratoMedio ? ' · ' + medioLabels[venta().contratoMedio!] : '' }}
          </span>
        } @else {
          <span class="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">Sin firmar</span>
        }
      </div>

      @if (venta().contratoDriveUrl) {
        <a
          [href]="venta().contratoDriveUrl" target="_blank" rel="noopener noreferrer"
          class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-we-primary hover:underline"
        >
          Abrir carpeta del contrato en Drive ↗
        </a>
      }

      <form (ngSubmit)="guardarNumero()" class="mt-4 flex flex-wrap items-end gap-3" data-testid="numero-contrato-form">
        <div class="min-w-[14rem] flex-1">
          <label for="contrato-numero" class="mb-1 block text-xs font-medium text-ink-muted">Número de contrato (opcional)</label>
          <input
            id="contrato-numero" name="numeroContrato" type="text" maxlength="50" autocomplete="off" placeholder="Ej. SAM-2026-014"
            data-testid="numero-contrato-input"
            [ngModel]="numeroContrato()" (ngModelChange)="numeroContrato.set($event); errorNumero.set(null)"
            class="block w-full rounded-lg border border-border px-3 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50"
          />
        </div>
        <button
          type="submit" [disabled]="guardandoNumero() || !cambioNumero()" data-testid="guardar-numero-contrato"
          class="boton-secundario rounded-lg px-4 py-2.5 text-sm font-semibold text-ink disabled:opacity-50 disabled:cursor-not-allowed min-h-[44px]"
        >
          {{ guardandoNumero() ? 'Guardando…' : 'Guardar número' }}
        </button>
      </form>
      @if (errorNumero()) {
        <p class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="error-numero-contrato">{{ errorNumero() }}</p>
      }

      <form (ngSubmit)="guardar()" class="mt-4 space-y-4">
        <div class="flex flex-wrap items-end gap-4">
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
            <div>
              <label for="contrato-medio" class="mb-1 block text-xs font-medium text-ink-muted">Cómo se firmó *</label>
              <select
                id="contrato-medio" name="medio"
                [ngModel]="medio()" (ngModelChange)="medio.set($event)"
                class="rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50"
              >
                <option value="">Selecciona…</option>
                <option value="FISICO">En físico</option>
                <option value="DOCUSIGN">DocuSign</option>
                <option value="AMBOS">Ambos (físico y DocuSign)</option>
              </select>
            </div>
          }
        </div>
        <div>
          <label for="contrato-drive" class="mb-1 block text-xs font-medium text-ink-muted">Carpeta del contrato en Drive (opcional)</label>
          <input
            id="contrato-drive" name="driveUrl" type="url" maxlength="500" placeholder="https://drive.google.com/…"
            [ngModel]="driveUrl()" (ngModelChange)="driveUrl.set($event)"
            class="block w-full rounded-lg border border-border px-3 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50"
          />
        </div>
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

  readonly medioLabels = MEDIO_FIRMA_LABELS;
  readonly hoy = new Date().toISOString().slice(0, 10);
  readonly firmado = signal(false);
  readonly fechaFirma = signal('');
  readonly medio = signal<MedioFirma | ''>('');
  readonly driveUrl = signal('');
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly numeroContrato = signal('');
  readonly guardandoNumero = signal(false);
  readonly errorNumero = signal<string | null>(null);

  constructor() {
    // Se reinicia con lo guardado cada vez que la venta se vuelve a cargar.
    effect(() => {
      const v = this.venta();
      this.firmado.set(v.contratoFirmado);
      this.fechaFirma.set(v.contratoFechaFirma ?? '');
      this.medio.set(v.contratoMedio ?? '');
      this.driveUrl.set(v.contratoDriveUrl ?? '');
      this.numeroContrato.set(v.numeroContrato ?? '');
    });
  }

  cambio(): boolean {
    const v = this.venta();
    if (this.driveUrl().trim() !== (v.contratoDriveUrl ?? '')) return true;
    if (this.firmado() !== v.contratoFirmado) return true;
    return this.firmado() && (this.fechaFirma() !== (v.contratoFechaFirma ?? '') || this.medio() !== (v.contratoMedio ?? ''));
  }

  cambioNumero(): boolean {
    return this.numeroContrato().trim() !== (this.venta().numeroContrato ?? '');
  }

  /** El número de contrato se guarda aparte de la firma: es opcional y no depende de que ya esté firmado. */
  async guardarNumero(): Promise<void> {
    if (this.guardandoNumero() || !this.cambioNumero()) return;
    this.guardandoNumero.set(true);
    this.errorNumero.set(null);
    try {
      await this.ventasService.guardarNumeroContrato(this.venta().id, this.numeroContrato().trim() || null);
      this.toast.success('Número de contrato guardado.');
      this.guardado.emit();
    } catch (e) {
      this.errorNumero.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : 'No se pudo guardar el número de contrato.',
      );
    } finally {
      this.guardandoNumero.set(false);
    }
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
    if (this.firmado() && !this.medio()) {
      this.error.set('Indica si el contrato se firmó en físico o en DocuSign.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    // La firma manda el estado de los lotes (backend: VentaService.registrarContrato).
    const cambioFirma = this.firmado() !== this.venta().contratoFirmado;
    try {
      await this.ventasService.registrarContrato(this.venta().id, {
        firmado: this.firmado(),
        fechaFirma: this.firmado() ? this.fechaFirma() : null,
        medio: this.firmado() ? (this.medio() as MedioFirma) : null,
        driveUrl: this.driveUrl().trim() || null,
      });
      this.toast.success(
        !cambioFirma
          ? 'Contrato guardado.'
          : this.firmado()
            ? 'Contrato guardado: los lotes pasaron a Vendido.'
            : 'Contrato guardado: los lotes regresaron a En proceso de firma.',
      );
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
