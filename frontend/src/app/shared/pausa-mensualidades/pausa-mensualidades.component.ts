import { Component, computed, input, output, signal } from '@angular/core';
import { LucideCalendarClock } from '@lucide/angular';
import { fechaCorta } from '../../core/models/amortizacion.model';

/** Lo que el padre manda al servidor al confirmar. fecha solo al mover (AAAA-MM-DD). */
export interface ConfirmacionPausa {
  fecha: string | null;
  motivo: string;
  password: string;
}

/**
 * Ventana para mover la fecha de una mensualidad (pausa de mensualidades, ver backend PausaMensualidadService) o para
 * deshacer la pausa más reciente. Al mover: fecha nueva (posterior a la actual), con una vista previa de cómo quedan
 * la siguiente y la última; pide motivo y la contraseña de quien lo hace (la valida el servidor). Se muestra con @if
 * desde el padre.
 */
@Component({
  selector: 'app-pausa-mensualidades',
  standalone: true,
  imports: [LucideCalendarClock],
  template: `
    <button type="button" (click)="cerrar()" aria-label="Cerrar" class="modal-backdrop-enter fixed inset-0 z-50 bg-black/50"></button>
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="pausa-titulo">
      <form (submit)="$event.preventDefault(); confirmar()" class="modal-panel-enter max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl border border-border bg-surface p-5 shadow-xl" data-testid="pausa-mensualidades">
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-we-primary/10 text-we-primary dark:text-we-blue-light">
            <svg lucideCalendarClock [size]="20" aria-hidden="true"></svg>
          </div>
          <div>
            <h2 id="pausa-titulo" class="font-heading text-lg font-semibold text-ink">
              {{ modo() === 'mover' ? 'Cambiar la fecha de la mensualidad N.º ' + numero() : 'Deshacer la pausa' }}
            </h2>
            <p class="mt-0.5 text-sm text-ink-muted">
              @if (modo() === 'mover') {
                Hoy cae el {{ fecha(fechaActual()) }}. Las siguientes se recorren mes a mes desde la nueva fecha y el plazo se respeta completo.
              } @else {
                La N.º {{ numero() }} regresa al {{ fecha(fechaActual()) }} y las siguientes vuelven a recorrerse desde ahí.
              }
            </p>
          </div>
        </div>

        @if (modo() === 'mover') {
          <label for="pausa-fecha" class="mt-4 block text-sm font-medium text-ink">Nueva fecha</label>
          <input
            id="pausa-fecha" name="fecha" type="date" data-testid="pausa-fecha" required
            [min]="minimo()" [value]="fechaNueva()" (input)="fechaNueva.set($any($event.target).value)"
            class="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50 focus:border-we-primary"
          />
          @if (vistaPrevia(); as v) {
            <ul class="mt-2 space-y-0.5 rounded-lg bg-surface-2 px-3.5 py-2.5 text-xs text-ink-muted" data-testid="pausa-vista-previa">
              <li>N.º {{ numero() }}: <strong class="text-ink">{{ v.primera }}</strong></li>
              @if (v.siguiente) {
                <li>N.º {{ numero() + 1 }}: <strong class="text-ink">{{ v.siguiente }}</strong></li>
              }
              @if (v.ultima && ultimoNumero() > numero() + 1) {
                <li>N.º {{ ultimoNumero() }} (la última): <strong class="text-ink">{{ v.ultima }}</strong></li>
              }
            </ul>
          }
        }

        <label for="pausa-motivo" class="mt-4 block text-sm font-medium text-ink">Motivo</label>
        <textarea
          id="pausa-motivo" name="motivo" rows="2" maxlength="500" data-testid="pausa-motivo" required
          [value]="motivo()" (input)="motivo.set($any($event.target).value)"
          [placeholder]="modo() === 'mover' ? 'Ej.: el cliente pidió pausar sus pagos hasta enero' : 'Ej.: se capturó la fecha equivocada'"
          class="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50 focus:border-we-primary"
        ></textarea>

        <label for="pausa-password" class="mt-4 block text-sm font-medium text-ink">Tu contraseña</label>
        <input
          id="pausa-password" name="password" type="password" autocomplete="current-password" data-testid="pausa-password"
          [value]="password()" (input)="password.set($any($event.target).value)"
          class="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50 focus:border-we-primary"
        />
        @if (error(); as e) {
          <p class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="pausa-error">{{ e }}</p>
        }

        <div class="mt-5 flex items-center justify-end gap-2.5">
          <button type="button" (click)="cerrar()" [disabled]="guardando()" class="boton-secundario rounded-lg px-4 py-2.5 text-sm font-medium text-ink-muted disabled:opacity-60">
            Cancelar
          </button>
          <button
            type="submit" data-testid="pausa-confirmar"
            [disabled]="!listo() || guardando()"
            class="rounded-lg bg-we-primary px-4 py-2.5 text-sm font-semibold text-white transition-opacity duration-150
                   enabled:hover:bg-we-primary/90 enabled:active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {{ guardando() ? 'Guardando…' : modo() === 'mover' ? 'Cambiar fecha' : 'Deshacer pausa' }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class PausaMensualidadesComponent {
  readonly modo = input.required<'mover' | 'deshacer'>();
  /** La mensualidad que se mueve (o desde la que empezó la pausa que se deshace). */
  readonly numero = input.required<number>();
  /** Al mover: la fecha que tiene hoy; al deshacer: la que tenía antes de la pausa (AAAA-MM-DD). */
  readonly fechaActual = input.required<string>();
  /** Número de la última mensualidad del plan, para la vista previa. */
  readonly ultimoNumero = input(0);
  readonly guardando = input(false);
  readonly error = input<string | null>(null);
  readonly confirmado = output<ConfirmacionPausa>();
  readonly cancelado = output<void>();

  readonly fecha = fechaCorta;
  readonly fechaNueva = signal('');
  readonly motivo = signal('');
  readonly password = signal('');

  /** Un día después de la fecha actual: solo se mueve hacia adelante. */
  readonly minimo = computed(() => {
    const [y, m, d] = this.fechaActual().split('-').map(Number);
    const siguiente = new Date(Date.UTC(y, m - 1, d + 1));
    return siguiente.toISOString().slice(0, 10);
  });

  readonly fechaValida = computed(() => /^\d{4}-\d{2}-\d{2}$/.test(this.fechaNueva()) && this.fechaNueva() >= this.minimo());

  readonly listo = computed(
    () => !!this.motivo().trim() && !!this.password() && (this.modo() === 'deshacer' || this.fechaValida()),
  );

  /** Cómo quedan la mensualidad movida, la siguiente y la última (mismo cálculo que el servidor: mes a mes desde la
   * fecha nueva, con su día; si el mes es corto, su último día). */
  readonly vistaPrevia = computed(() => {
    if (!this.fechaValida()) return null;
    const [y, m, d] = this.fechaNueva().split('-').map(Number);
    const en = (meses: number): string => {
      const total = m - 1 + meses;
      const anio = y + Math.floor(total / 12);
      const mes = (total % 12) + 1;
      const dia = Math.min(d, new Date(Date.UTC(anio, mes, 0)).getUTCDate());
      return `${String(dia).padStart(2, '0')}/${String(mes).padStart(2, '0')}/${anio}`;
    };
    const resto = this.ultimoNumero() - this.numero();
    return { primera: en(0), siguiente: resto >= 1 ? en(1) : null, ultima: resto >= 2 ? en(resto) : null };
  });

  confirmar(): void {
    if (!this.listo() || this.guardando()) return;
    this.confirmado.emit({
      fecha: this.modo() === 'mover' ? this.fechaNueva() : null,
      motivo: this.motivo().trim(),
      password: this.password(),
    });
  }

  cerrar(): void {
    if (!this.guardando()) this.cancelado.emit();
  }
}
