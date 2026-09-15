import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmService } from '../../../core/services/confirm.service';
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
 * EXTERNO (no el cliente final) mientras atiende a su cliente — por eso se pide el nombre de quien
 * está operando la página y, opcionalmente, una nota, antes de dejar un lote como apartado. Mismas
 * restricciones que un asesor interno (nunca puede tocar un lote exclusivo de admin), nunca las de
 * un admin (crear, editar, eliminar o importar lotes, que solo existen en /panel/lotes). */
@Component({
  selector: 'app-lotes-publico',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './lotes-publico.component.html',
})
export class LotesPublicoComponent {
  private readonly lotesService = inject(LotesService);
  private readonly confirmService = inject(ConfirmService);
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
  readonly idEnProceso = signal<number | null>(null);

  // Modal "Apartar lote": pide el nombre del asesor y, opcionalmente, una nota antes de confirmar.
  readonly loteAApartar = signal<Lote | null>(null);
  readonly nombreAsesorInput = signal('');
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

  /** Abre el modal que pide el nombre del asesor antes de apartar — no hay sesión real detrás, así
   * que es la única forma de saber a quién le corresponde este apartado en el historial. */
  abrirApartar(lote: Lote): void {
    this.loteAApartar.set(lote);
    this.nombreAsesorInput.set('');
    this.notaInput.set('');
  }

  cancelarApartar(): void {
    this.loteAApartar.set(null);
    this.nombreAsesorInput.set('');
    this.notaInput.set('');
  }

  async confirmarApartar(): Promise<void> {
    const lote = this.loteAApartar();
    if (!lote || this.nombreAsesorInvalido()) return;

    this.isApartando.set(true);
    try {
      const actualizado = await this.lotesService.cambiarEstadoPublico(
        lote.id,
        'APARTADO',
        this.nombreAsesorInput().trim(),
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

  async liberar(lote: Lote): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Liberar lote',
      mensaje: `¿Marcar de nuevo como disponible el lote Manzana ${lote.manzana}, Lote ${lote.numeroLote}?`,
      textoConfirmar: 'Liberar',
    });
    if (!confirmado) return;

    this.idEnProceso.set(lote.id);
    try {
      const actualizado = await this.lotesService.cambiarEstadoPublico(lote.id, 'DISPONIBLE');
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
      this.toast.success('Lote liberado.');
    } catch {
      this.toast.error('No se pudo actualizar el lote. Intenta de nuevo.');
    } finally {
      this.idEnProceso.set(null);
    }
  }
}
