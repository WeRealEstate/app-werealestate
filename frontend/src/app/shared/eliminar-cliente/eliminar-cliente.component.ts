import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { LucideTriangleAlert } from '@lucide/angular';
import { ClienteEliminacion } from '../../core/models/cliente.model';
import { ClientesService } from '../../core/services/clientes.service';

const SEGUNDOS_ESPERA = 10;

/**
 * Confirmación para eliminar un cliente (ver backend EliminacionClienteService): lista exactamente lo que se borra
 * (sus ventas como titular con sus lotes, recibos y comisiones; las copropiedades de las que solo se le quita; sus
 * registros de We Forms y números de cliente), pide la contraseña de quien lo hace y, como al eliminar una venta, el
 * botón se habilita hasta que termina una cuenta regresiva de 10 s. Se muestra con @if desde el padre.
 */
@Component({
  selector: 'app-eliminar-cliente',
  standalone: true,
  imports: [LucideTriangleAlert],
  template: `
    <button type="button" (click)="cerrar()" aria-label="Cerrar" class="modal-backdrop-enter fixed inset-0 z-50 bg-black/50"></button>
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4" role="alertdialog" aria-modal="true" aria-labelledby="eliminar-cliente-titulo">
      <form (submit)="$event.preventDefault(); confirmar()" class="modal-panel-enter max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-border bg-surface p-5 shadow-xl" data-testid="eliminar-cliente">
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
          </div>
          <div class="min-w-0">
            <h2 id="eliminar-cliente-titulo" class="font-heading text-lg font-semibold text-ink">Eliminar cliente</h2>
            <p class="mt-0.5 break-words text-sm text-ink-muted">{{ previa()?.nombre ?? nombre() }} · No se puede deshacer.</p>
          </div>
        </div>

        @if (cargando()) {
          <p class="mt-4 text-sm text-ink-muted">Revisando lo que se va a eliminar…</p>
        } @else if (previa(); as p) {
          <div class="mt-4 space-y-3 rounded-lg bg-red-50 px-3.5 py-3 text-sm text-red-900 dark:bg-red-500/10 dark:text-red-200" data-testid="eliminar-cliente-resumen">
            <p class="font-semibold">Se eliminará:</p>
            <ul class="list-disc space-y-1.5 pl-5">
              <li>El cliente{{ p.numeros.length ? ' (' + p.numeros.join(', ') + ')' : '' }}.@if (p.numeros.length) { Los números mayores de su desarrollo bajan 1.}</li>
              @if (p.ventas.length) {
                <li>
                  {{ p.ventas.length === 1 ? 'Su venta' : 'Sus ' + p.ventas.length + ' ventas' }} como titular:
                  <ul class="mt-1 space-y-1 pl-1">
                    @for (v of p.ventas; track v.etiqueta) {
                      <li>
                        <strong>Venta {{ v.etiqueta }}</strong> · {{ v.lotes.join(', ') || 'sin lotes' }} ·
                        {{ v.recibos }} {{ v.recibos === 1 ? 'recibo' : 'recibos' }}
                      </li>
                    }
                  </ul>
                  <span class="mt-1 block text-xs text-red-800/80 dark:text-red-200/80">
                    Sus lotes vuelven a Disponible y las demás ventas se renumeran. Sus comisiones se conservan en Finanzas como «Venta eliminada».
                  </span>
                </li>
              }
              @if (p.registrosWeForms) {
                <li>
                  {{ (p.registrosWeForms === 1 ? 'Su registro' : 'Sus ' + p.registrosWeForms + ' registros') + ' de We Forms'
                     + (p.archivosEnServidor ? ' y ' + p.archivosEnServidor + (p.archivosEnServidor === 1 ? ' archivo' : ' archivos') + ' del servidor' : '') + '.' }}
                  <span class="block text-xs text-red-800/80 dark:text-red-200/80">Lo que ya está en Google Drive no se borra.</span>
                </li>
              }
            </ul>
            @if (p.copropiedades.length) {
              <p class="border-t border-red-200 pt-2 text-xs dark:border-red-500/20">
                Se le quita como copropietario de {{ p.copropiedades.length === 1 ? 'la venta' : 'las ventas' }}
                <strong>{{ p.copropiedades.join(', ') }}</strong>; esas ventas siguen con su titular.
              </p>
            }
          </div>

          <label for="eliminar-cliente-password" class="mt-4 block text-sm font-medium text-ink">Tu contraseña</label>
          <input
            id="eliminar-cliente-password" name="password" type="password" autocomplete="current-password" data-testid="eliminar-cliente-password"
            [value]="password()" (input)="password.set($any($event.target).value)"
            class="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50 focus:border-we-primary"
          />
        }
        @if (error(); as e) {
          <p class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="eliminar-cliente-error">{{ e }}</p>
        }

        <div class="mt-5 flex flex-wrap items-center justify-end gap-2.5">
          <button type="button" (click)="cerrar()" [disabled]="eliminando()" class="boton-secundario rounded-lg px-4 py-2.5 text-sm font-medium text-ink-muted disabled:opacity-60">
            Cancelar
          </button>
          <button
            type="submit" data-testid="eliminar-cliente-confirmar"
            [disabled]="!previa() || !cuentaTerminada() || !password() || eliminando()"
            class="rounded-lg bg-red-600 px-4 py-2.5 text-sm font-semibold text-white transition-opacity duration-150
                   enabled:hover:bg-red-700 enabled:active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            @if (eliminando()) {
              Eliminando…
            } @else if (!cuentaTerminada()) {
              Eliminar cliente ({{ segundos() }})
            } @else {
              Eliminar cliente
            }
          </button>
        </div>
        @if (!cuentaTerminada()) {
          <div class="mt-3 h-1 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
            <div class="h-full rounded-full bg-red-600 transition-[width] duration-1000 ease-linear" [style.width.%]="((espera - segundos()) / espera) * 100"></div>
          </div>
        }
      </form>
    </div>
  `,
})
export class EliminarClienteComponent {
  private readonly clientes = inject(ClientesService);

  readonly clienteId = input.required<number>();
  /** Nombre mientras carga el resumen. */
  readonly nombre = input('');
  readonly eliminado = output<void>();
  readonly cancelado = output<void>();

  readonly espera = SEGUNDOS_ESPERA;
  readonly segundos = signal(SEGUNDOS_ESPERA);
  readonly cuentaTerminada = computed(() => this.segundos() <= 0);
  readonly password = signal('');
  readonly previa = signal<ClienteEliminacion | null>(null);
  readonly cargando = signal(true);
  readonly eliminando = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    const temporizador = setInterval(() => {
      this.segundos.update((s) => Math.max(0, s - 1));
      if (this.segundos() <= 0) clearInterval(temporizador);
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(temporizador));
    // El id llega como input: se pide el resumen en el siguiente tick, ya con el valor puesto.
    queueMicrotask(() => void this.cargar());
  }

  private async cargar(): Promise<void> {
    try {
      this.previa.set(await this.clientes.vistaPreviaEliminacion(this.clienteId()));
    } catch (e) {
      this.error.set(mensaje(e, 'No se pudo revisar lo que se va a eliminar. Cierra e intenta de nuevo.'));
    } finally {
      this.cargando.set(false);
    }
  }

  async confirmar(): Promise<void> {
    if (!this.previa() || !this.cuentaTerminada() || !this.password() || this.eliminando()) return;
    this.eliminando.set(true);
    this.error.set(null);
    try {
      await this.clientes.eliminar(this.clienteId(), this.password());
      this.eliminado.emit();
    } catch (e) {
      this.error.set(mensaje(e, 'No se pudo eliminar el cliente. Intenta de nuevo.'));
    } finally {
      this.eliminando.set(false);
    }
  }

  cerrar(): void {
    if (!this.eliminando()) this.cancelado.emit();
  }
}

function mensaje(e: unknown, porDefecto: string): string {
  return e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : porDefecto;
}
