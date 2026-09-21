import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../../core/services/toast.service';
import { LotesService } from '../../../core/services/lotes.service';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  ESTADOS_LOTE_SOLO_ADMIN,
  Lote,
} from '../../../core/models/lote.model';

type ProyectoPublico = 'samai' | 'nanuu';

const NOTA_MAX_LENGTH = 500;

/** Disponibilidad de lotes para /cotizador-publico/lotes: pensada para que la use el ASESOR
 * EXTERNO (no el cliente final) mientras atiende a su cliente — por eso se piden el nombre de
 * quien está operando la página y el del cliente que aparta, y opcionalmente una nota, antes de
 * dejar un lote como apartado. Mismas restricciones que un asesor interno (nunca puede tocar un
 * lote exclusivo de admin), nunca las de un admin (crear, editar, eliminar o importar lotes, que
 * solo existen en /panel/lotes) — y, a diferencia de un asesor interno, tampoco puede liberar un
 * lote ya apartado: eso solo se hace desde /panel/lotes o /panel/plano. */
@Component({
  selector: 'app-lotes-publico',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './lotes-publico.component.html',
})
export class LotesPublicoComponent {
  private readonly lotesService = inject(LotesService);
  private readonly toast = inject(ToastService);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly estadosSoloAdmin = ESTADOS_LOTE_SOLO_ADMIN;

  readonly proyecto = signal<ProyectoPublico>('samai');
  readonly lotes = signal<Lote[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filtroManzana = signal('');
  readonly filtroNumeroLote = signal('');

  // Modal "Apartar lote": pide el nombre del asesor y del cliente, y opcionalmente una nota, antes
  // de confirmar. Un lote ya apartado no se puede liberar desde aquí (ver LoteService).
  readonly loteAApartar = signal<Lote | null>(null);
  readonly nombreAsesorInput = signal('');
  readonly nombreClienteInput = signal('');
  readonly notaInput = signal('');
  readonly isApartando = signal(false);
  readonly notaMaxLength = NOTA_MAX_LENGTH;

  readonly lotesFiltrados = computed(() => {
    const manzana = this.filtroManzana().trim().toLowerCase();
    const numeroLote = this.filtroNumeroLote().trim().toLowerCase();
    return this.lotes().filter(
      (l) =>
        (!manzana || l.manzana.toLowerCase().includes(manzana)) &&
        (!numeroLote || l.numeroLote.toLowerCase().includes(numeroLote)),
    );
  });

  readonly nombreAsesorInvalido = computed(() => this.nombreAsesorInput().trim().length === 0);
  readonly nombreClienteInvalido = computed(() => this.nombreClienteInput().trim().length === 0);

  constructor() {
    this.cargar();
  }

  elegirProyecto(proyecto: ProyectoPublico): void {
    if (proyecto === this.proyecto()) return;
    this.proyecto.set(proyecto);
    this.filtroManzana.set('');
    this.filtroNumeroLote.set('');
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.lotes.set(await this.lotesService.listarPublicoPorProyecto(this.proyecto()));
    } catch {
      this.errorMessage.set('No se pudo cargar la disponibilidad de lotes. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Un lote exclusivo de admin (apartado con dinero o en proceso de firma) no se puede tocar
   * desde aquí, igual que un asesor tampoco puede en /panel/lotes. */
  puedeCambiarEstado(lote: Lote): boolean {
    return !this.estadosSoloAdmin.has(lote.estado);
  }

  /** Abre el modal que pide el nombre del asesor y del cliente antes de apartar — no hay sesión
   * real detrás, así que es la única forma de saber a quién le corresponde este apartado en el
   * historial y quién es el cliente que se quedó con el lote. */
  abrirApartar(lote: Lote): void {
    this.loteAApartar.set(lote);
    this.nombreAsesorInput.set('');
    this.nombreClienteInput.set('');
    this.notaInput.set('');
  }

  cancelarApartar(): void {
    this.loteAApartar.set(null);
    this.nombreAsesorInput.set('');
    this.nombreClienteInput.set('');
    this.notaInput.set('');
  }

  async confirmarApartar(): Promise<void> {
    const lote = this.loteAApartar();
    if (!lote || this.nombreAsesorInvalido() || this.nombreClienteInvalido()) return;

    this.isApartando.set(true);
    try {
      const actualizado = await this.lotesService.apartarLotePublico(
        lote.id,
        this.nombreAsesorInput().trim(),
        this.nombreClienteInput().trim(),
        this.notaInput().trim() || undefined,
      );
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
      this.toast.success('Lote apartado.');
      this.cancelarApartar();
    } catch {
      this.toast.error('No se pudo apartar el lote. Intenta de nuevo.');
    } finally {
      this.isApartando.set(false);
    }
  }
}
