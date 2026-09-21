import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo } from '../../../core/models/lead.model';
import { ESTADO_LOTE_BADGE_CLASSES, ESTADO_LOTE_LABELS, MovimientoLote } from '../../../core/models/lote.model';

const TAMANO_PAGINA = 20;

/** Historial de movimientos de TODOS los lotes (no uno por uno): quién apartó/liberó/etc. cada
 * lote y cuándo, en un solo lugar para auditar la actividad completa. */
@Component({
  selector: 'app-lotes-historial',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './lotes-historial.component.html',
})
export class LotesHistorialComponent {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly confirmService = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly movimientos = signal<MovimientoLote[]>([]);
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hayMas = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly filtroManzana = signal('');
  readonly filtroNumeroLote = signal('');
  readonly filtroDesarrolloId = signal<number | null>(null);

  readonly hayFiltrosActivos = computed(
    () =>
      this.filtroManzana().trim().length > 0 ||
      this.filtroNumeroLote().trim().length > 0 ||
      this.filtroDesarrolloId() !== null,
  );

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    this.cargar();
  }

  onFiltroTextoInput(campo: 'manzana' | 'numeroLote', valor: string): void {
    if (campo === 'manzana') this.filtroManzana.set(valor);
    else this.filtroNumeroLote.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.cargar(), 350);
  }

  onDesarrolloChange(valor: string): void {
    this.filtroDesarrolloId.set(valor === '' ? null : +valor);
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const resultado = await this.buscarPagina(0);
      this.movimientos.set(resultado.contenido);
      this.hayMas.set(resultado.hayMas);
      this.pagina = 0;
    } catch {
      this.errorMessage.set('No se pudo cargar el historial. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cargarMas(): Promise<void> {
    if (this.isLoadingMore() || !this.hayMas()) return;
    this.isLoadingMore.set(true);
    try {
      const siguiente = this.pagina + 1;
      const resultado = await this.buscarPagina(siguiente);
      this.movimientos.update((actuales) => [...actuales, ...resultado.contenido]);
      this.hayMas.set(resultado.hayMas);
      this.pagina = siguiente;
    } catch {
      this.errorMessage.set('No se pudieron cargar más movimientos.');
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  private buscarPagina(pagina: number) {
    return this.lotesService.buscarMovimientos({
      manzana: this.filtroManzana().trim() || undefined,
      numeroLote: this.filtroNumeroLote().trim() || undefined,
      desarrolloId: this.filtroDesarrolloId(),
      pagina,
      tamano: TAMANO_PAGINA,
    });
  }

  /** Quién hizo el movimiento: el usuario autenticado, el nombre de asesor capturado desde
   * /cotizador-publico/lotes, o nadie (reversión automática por vencimiento). */
  autorMovimiento(movimiento: MovimientoLote): string {
    if (movimiento.usuario) {
      return movimiento.nombreAsesor
        ? `${movimiento.nombreAsesor} (vía disponibilidad pública)`
        : movimiento.usuario.nombre;
    }
    return 'Sistema (reversión automática)';
  }

  async eliminar(movimiento: MovimientoLote): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar registro del historial',
      mensaje: `¿Eliminar este movimiento de Manzana ${movimiento.manzana}, Lote ${movimiento.numeroLote}? Esto no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;
    try {
      await this.lotesService.eliminarMovimiento(movimiento.id);
      this.movimientos.update((lista) => lista.filter((m) => m.id !== movimiento.id));
      this.toast.success('Registro eliminado del historial.');
    } catch {
      this.toast.error('No se pudo eliminar el registro.');
    }
  }
}
