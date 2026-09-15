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
  EstadoLote,
  Lote,
} from '../../../core/models/lote.model';

type ProyectoPublico = 'samai' | 'nanuu';

/** Disponibilidad de lotes para /cotizador-publico/lotes: cualquiera con el link puede ver el
 * inventario y apartar o liberar un lote, sin sesión — con las mismas restricciones que un
 * asesor (nunca puede tocar un lote exclusivo de admin), nunca las de un admin (crear, editar,
 * eliminar o importar lotes, que solo existen en /panel/lotes). */
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
  readonly busqueda = signal('');
  readonly idEnProceso = signal<number | null>(null);

  readonly lotesFiltrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    if (!texto) return this.lotes();
    return this.lotes().filter(
      (l) => l.manzana.toLowerCase().includes(texto) || l.numeroLote.toLowerCase().includes(texto),
    );
  });

  constructor() {
    this.cargar();
  }

  elegirProyecto(proyecto: ProyectoPublico): void {
    if (proyecto === this.proyecto()) return;
    this.proyecto.set(proyecto);
    this.busqueda.set('');
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

  async apartar(lote: Lote): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Apartar lote',
      mensaje: `¿Apartar el lote Manzana ${lote.manzana}, Lote ${lote.numeroLote}? Un asesor se pondrá en contacto contigo para continuar con el proceso.`,
      textoConfirmar: 'Apartar',
    });
    if (!confirmado) return;
    await this.cambiarEstado(lote, 'APARTADO');
  }

  async liberar(lote: Lote): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Liberar lote',
      mensaje: `¿Marcar de nuevo como disponible el lote Manzana ${lote.manzana}, Lote ${lote.numeroLote}?`,
      textoConfirmar: 'Liberar',
    });
    if (!confirmado) return;
    await this.cambiarEstado(lote, 'DISPONIBLE');
  }

  private async cambiarEstado(lote: Lote, estado: EstadoLote): Promise<void> {
    this.idEnProceso.set(lote.id);
    try {
      const actualizado = await this.lotesService.cambiarEstadoPublico(lote.id, estado);
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
      this.toast.success(estado === 'APARTADO' ? 'Lote apartado.' : 'Lote liberado.');
    } catch {
      this.toast.error('No se pudo actualizar el lote. Intenta de nuevo.');
    } finally {
      this.idEnProceso.set(null);
    }
  }
}
