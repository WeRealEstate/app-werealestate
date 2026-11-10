import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { LucideCircleAlert } from '@lucide/angular';

const SEGUNDOS_ESPERA = 10;

/** Confirmación para cambiar o quitar el número de contrato de una venta: pide la contraseña de quien lo hace (la valida
 * el servidor) y, como al eliminar una venta, el botón de confirmar se habilita hasta que termina una cuenta regresiva
 * de 10 s. Se muestra con @if desde el padre: la cuenta arranca cada vez que se crea. */
@Component({
  selector: 'app-confirmar-cambio-contrato',
  standalone: true,
  imports: [LucideCircleAlert],
  template: `
    <button type="button" (click)="cerrar()" aria-label="Cerrar" class="modal-backdrop-enter fixed inset-0 z-50 bg-black/50"></button>
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4" role="alertdialog" aria-modal="true" aria-labelledby="cambio-contrato-titulo">
      <form (submit)="$event.preventDefault(); confirmar()" class="modal-panel-enter w-full max-w-md rounded-2xl border border-border bg-surface p-5 shadow-xl" data-testid="confirmar-cambio-contrato">
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
            <svg lucideCircleAlert [size]="20" aria-hidden="true"></svg>
          </div>
          <div>
            <h2 id="cambio-contrato-titulo" class="font-heading text-lg font-semibold text-ink">
              {{ nuevo() ? 'Cambiar el número de contrato' : 'Quitar el número de contrato' }}
            </h2>
            <p class="mt-0.5 text-sm text-ink-muted">Esta venta ya tiene un número de contrato registrado.</p>
          </div>
        </div>

        <div class="mt-4 rounded-lg bg-amber-50 px-3.5 py-3 text-sm text-amber-900 dark:bg-amber-500/10 dark:text-amber-200">
          <p>
            <span class="text-xs">Actual:</span> <strong>{{ anterior() }}</strong>
          </p>
          <p class="mt-1">
            <span class="text-xs">Nuevo:</span> <strong>{{ nuevo() || 'Sin número (se quita)' }}</strong>
          </p>
        </div>

        <label for="cambio-contrato-password" class="mt-4 block text-sm font-medium text-ink">Tu contraseña</label>
        <input
          id="cambio-contrato-password" name="password" type="password" autocomplete="current-password" data-testid="cambio-contrato-password"
          [value]="password()" (input)="password.set($any($event.target).value)"
          class="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50 focus:border-we-primary"
        />
        @if (error(); as e) {
          <p class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="cambio-contrato-error">{{ e }}</p>
        }

        <div class="mt-5 flex items-center justify-end gap-2.5">
          <button type="button" (click)="cerrar()" [disabled]="guardando()" class="boton-secundario rounded-lg px-4 py-2.5 text-sm font-medium text-ink-muted disabled:opacity-60">
            Cancelar
          </button>
          <button
            type="submit" data-testid="cambio-contrato-confirmar"
            [disabled]="!cuentaTerminada() || !password() || guardando()"
            class="rounded-lg bg-we-primary px-4 py-2.5 text-sm font-semibold text-white transition-opacity duration-150
                   enabled:hover:bg-we-primary/90 enabled:active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            @if (guardando()) {
              Guardando…
            } @else if (!cuentaTerminada()) {
              Confirmar cambio ({{ segundos() }})
            } @else {
              Confirmar cambio
            }
          </button>
        </div>
        @if (!cuentaTerminada()) {
          <div class="mt-3 h-1 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
            <div class="h-full rounded-full bg-we-primary transition-[width] duration-1000 ease-linear" [style.width.%]="((espera - segundos()) / espera) * 100"></div>
          </div>
        }
      </form>
    </div>
  `,
})
export class ConfirmarCambioContratoComponent {
  /** Número que tiene hoy la venta y el que se quiere dejar (vacío = quitarlo). */
  readonly anterior = input.required<string>();
  readonly nuevo = input.required<string>();
  /** Mientras el padre guarda; y el error que devolvió el servidor (por ejemplo, contraseña incorrecta). */
  readonly guardando = input(false);
  readonly error = input<string | null>(null);
  /** La contraseña escrita, para que el padre la mande al servidor. */
  readonly confirmado = output<string>();
  readonly cancelado = output<void>();

  readonly espera = SEGUNDOS_ESPERA;
  readonly segundos = signal(SEGUNDOS_ESPERA);
  readonly password = signal('');
  readonly cuentaTerminada = computed(() => this.segundos() <= 0);

  constructor() {
    const temporizador = setInterval(() => {
      this.segundos.update((s) => Math.max(0, s - 1));
      if (this.segundos() <= 0) clearInterval(temporizador);
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(temporizador));
  }

  confirmar(): void {
    if (!this.cuentaTerminada() || !this.password() || this.guardando()) return;
    this.confirmado.emit(this.password());
  }

  cerrar(): void {
    if (!this.guardando()) this.cancelado.emit();
  }
}
