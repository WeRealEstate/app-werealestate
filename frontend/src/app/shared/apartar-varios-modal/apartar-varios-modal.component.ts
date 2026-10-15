import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsesorExterno } from '../../core/models/asesor-externo.model';
import { UsuarioResumen } from '../../core/models/lead.model';
import { Lote } from '../../core/models/lote.model';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { AuthService } from '../../core/services/auth.service';
import { LotesService } from '../../core/services/lotes.service';
import { ToastService } from '../../core/services/toast.service';
import { UsuariosService } from '../../core/services/usuarios.service';

const NOTA_MAX_LENGTH = 500;

/** Apartar varios lotes a la vez desde el plano interno (estado "Apartado" simple). Todos reciben los mismos
 * datos: el cliente es obligatorio; la nota solo es obligatoria para admin y administración, y ellos además
 * eligen a qué asesor pertenece el apartado (igual que al apartar un lote con el modal de cambio de estado).
 * Es todo o nada: si algún lote ya no está disponible el servidor no aparta ninguno y el aviso dice cuál. */
@Component({
  selector: 'app-apartar-varios-modal',
  standalone: true,
  imports: [FormsModule],
  template: `
    <button type="button" (click)="cancelar()" aria-label="Cerrar" class="modal-backdrop-enter fixed inset-0 z-50 bg-black/40"></button>
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="apartar-varios-titulo">
      <div class="modal-panel-enter max-h-[90dvh] w-full max-w-sm overflow-y-auto rounded-xl border border-border bg-surface p-5 shadow-lg">
        <h2 id="apartar-varios-titulo" class="font-heading text-lg font-semibold text-ink">
          Apartar {{ lotes().length }} {{ lotes().length === 1 ? 'lote' : 'lotes' }}
        </h2>
        <ul class="mt-2 flex flex-wrap gap-1.5" data-testid="lotes-a-apartar">
          @for (l of lotes(); track l.id) {
            <li class="rounded-full bg-surface-2 px-2.5 py-1 text-xs font-medium text-ink">Mz {{ l.manzana }} · Lote {{ l.numeroLote }}</li>
          }
        </ul>
        <p class="mt-2 text-xs text-ink-muted">Todos se apartan con los mismos datos; si alguno ya no está disponible, no se aparta ninguno.</p>

        <div class="mt-4">
          <label for="apv-cliente" class="mb-1.5 block text-sm font-medium text-ink">Nombre del cliente *</label>
          <input
            id="apv-cliente" type="text" autocomplete="off" maxlength="150" placeholder="Ej. Hernesto Montesinos Alba"
            [ngModel]="cliente()" (ngModelChange)="cliente.set($event)"
            class="w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink outline-none transition-all
                   focus:border-we-primary focus:ring-2 focus:ring-we-primary/50"
          />
        </div>

        @if (esAdminOLider()) {
          <div class="mt-4">
            <label for="apv-asesor" class="mb-1.5 block text-sm font-medium text-ink">Asesor al que pertenece *</label>
            <select
              id="apv-asesor" [ngModel]="asesor()" (ngModelChange)="asesor.set($event)"
              class="w-full rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-we-primary/50"
            >
              <option value="">Selecciona un asesor…</option>
              @for (u of asesoresInternos(); track u.id) { <option [value]="'U-' + u.id">{{ u.nombre }}</option> }
              @for (a of asesoresExternos(); track a.id) { <option [value]="'E-' + a.id">{{ a.nombre }} (externo)</option> }
            </select>
            <p class="mt-1.5 text-xs text-ink-muted">Solo ese asesor podrá usar estos lotes en su registro de venta de We Forms.</p>
          </div>
        }

        <div class="mt-4">
          <div class="mb-1.5 flex items-center justify-between">
            <label for="apv-nota" class="block text-sm font-medium text-ink">Nota {{ esAdminOLider() ? '*' : '(opcional)' }}</label>
            <span class="text-xs text-ink-muted">{{ nota().length }}/{{ notaMax }}</span>
          </div>
          <textarea
            id="apv-nota" rows="3" [maxlength]="notaMax" [ngModel]="nota()" (ngModelChange)="nota.set($event)"
            [placeholder]="esAdminOLider() ? 'Motivo del apartado (obligatoria).' : 'Ej. Cliente confirma pago del enganche la próxima semana.'"
            class="w-full resize-none rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-ink outline-none transition-all
                   focus:border-we-primary focus:ring-2 focus:ring-we-primary/50"
          ></textarea>
        </div>

        <div class="flex justify-end gap-2.5 pt-5">
          <button type="button" (click)="cancelar()" [disabled]="guardando()"
            class="rounded-lg border border-border px-4 py-2.5 text-sm font-medium text-ink-muted hover:border-we-primary active:scale-[0.97] transition-transform duration-150">
            Cancelar
          </button>
          <button type="button" (click)="confirmar()" [disabled]="invalido() || guardando()" data-testid="confirmar-apartar-varios"
            class="rounded-lg bg-we-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-we-primary/90 active:scale-[0.97] transition-transform duration-150 disabled:opacity-60 disabled:cursor-not-allowed">
            {{ guardando() ? 'Apartando…' : 'Apartar ' + lotes().length + (lotes().length === 1 ? ' lote' : ' lotes') }}
          </button>
        </div>
      </div>
    </div>
  `,
})
export class ApartarVariosModalComponent implements OnInit {
  private readonly lotesService = inject(LotesService);
  private readonly auth = inject(AuthService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);

  readonly lotes = input.required<Lote[]>();
  /** Los lotes ya apartados (con su estado nuevo), para que el padre los refleje en el plano. */
  readonly apartados = output<Lote[]>();
  readonly cancelado = output<void>();

  readonly notaMax = NOTA_MAX_LENGTH;
  readonly cliente = signal('');
  readonly nota = signal('');
  /** "U-5" (interno) / "E-3" (externo), igual que el modal de cambio de estado. */
  readonly asesor = signal('');
  readonly asesoresInternos = signal<UsuarioResumen[]>([]);
  readonly asesoresExternos = signal<AsesorExterno[]>([]);
  readonly guardando = signal(false);

  readonly esAdminOLider = computed(() => {
    const rol = this.auth.currentUser()?.rol;
    return rol === 'ADMIN' || rol === 'LIDER_AREA';
  });

  readonly invalido = computed(() => {
    if (!this.cliente().trim()) return true;
    if (this.esAdminOLider() && (!this.nota().trim() || !this.asesor())) return true;
    return false;
  });

  ngOnInit(): void {
    if (this.esAdminOLider()) {
      this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
      this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
    }
  }

  cancelar(): void {
    if (!this.guardando()) this.cancelado.emit();
  }

  async confirmar(): Promise<void> {
    if (this.invalido() || this.guardando()) return;
    this.guardando.set(true);
    try {
      const v = this.asesor();
      const resultado = await this.lotesService.apartarVarios(
        this.lotes().map((l) => l.id),
        this.cliente().trim(),
        this.nota().trim() || undefined,
        this.esAdminOLider()
          ? {
              usuarioAsesorId: v.startsWith('U-') ? Number(v.slice(2)) : null,
              asesorExternoId: v.startsWith('E-') ? Number(v.slice(2)) : null,
            }
          : null,
      );
      this.toast.success(resultado.length === 1 ? 'Lote apartado.' : `${resultado.length} lotes apartados.`);
      this.apartados.emit(resultado);
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudieron apartar los lotes. Intenta de nuevo.',
      );
    } finally {
      this.guardando.set(false);
    }
  }
}
