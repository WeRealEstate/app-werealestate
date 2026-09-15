import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo } from '../../../core/models/lead.model';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
  MovimientoLote,
} from '../../../core/models/lote.model';

const TAMANO_PAGINA = 20;

/** Los 4 estados los puede ver cualquiera; para elegir uno nuevo, un no-admin solo puede moverse
 * entre Disponible/Apartado (y solo si el lote no está ya en un estado exclusivo de admin). */
const ESTADOS_ASESOR: EstadoLote[] = ['DISPONIBLE', 'APARTADO'];
const ESTADOS_TODOS: EstadoLote[] = ['DISPONIBLE', 'APARTADO', 'APARTADO_CON_DINERO', 'EN_PROCESO_DE_FIRMA'];

@Component({
  selector: 'app-lotes-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './lotes-list.component.html',
})
export class LotesListComponent {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly estadosLote = ESTADOS_TODOS;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly lotes = signal<Lote[]>([]);
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hayMas = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly filtroManzana = signal('');
  readonly filtroNumeroLote = signal('');
  readonly filtroDesarrolloId = signal<number | null>(null);
  readonly filtroEstado = signal<EstadoLote | null>(null);
  readonly superficieMin = signal<number | null>(null);
  readonly superficieMax = signal<number | null>(null);

  readonly hayFiltrosActivos = computed(
    () =>
      this.filtroManzana().trim().length > 0 ||
      this.filtroNumeroLote().trim().length > 0 ||
      this.filtroDesarrolloId() !== null ||
      this.filtroEstado() !== null ||
      this.superficieMin() !== null ||
      this.superficieMax() !== null,
  );

  // Modal "Historial de movimientos": quién apartó/liberó/etc. cada lote y cuándo.
  readonly loteConHistorial = signal<Lote | null>(null);
  readonly movimientos = signal<MovimientoLote[]>([]);
  readonly isLoadingHistorial = signal(false);

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.leadsService.listarDesarrollos().then((d) => this.desarrollos.set(d));
    this.cargar();
  }

  /** El precio ya no se captura a mano: siempre es el precio por m² del desarrollo × la superficie. */
  precioEstimado(lote: Lote): number {
    return lote.desarrollo.precioM2 * lote.superficie;
  }

  /** Un lote ya comprometido en un estado exclusivo de admin no lo puede tocar nadie más. */
  estadosDisponiblesPara(lote: Lote): EstadoLote[] {
    if (this.esAdmin()) return ESTADOS_TODOS;
    return ESTADOS_LOTE_SOLO_ADMIN.has(lote.estado) ? [lote.estado] : ESTADOS_ASESOR;
  }

  onFiltroTextoInput(campo: 'manzana' | 'numeroLote', valor: string): void {
    if (campo === 'manzana') this.filtroManzana.set(valor);
    else this.filtroNumeroLote.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.recargarDesdeInicio(), 350);
  }

  onDesarrolloChange(valor: string): void {
    this.filtroDesarrolloId.set(valor === '' ? null : +valor);
    this.recargarDesdeInicio();
  }

  onEstadoChange(valor: string): void {
    this.filtroEstado.set(valor === '' ? null : (valor as EstadoLote));
    this.recargarDesdeInicio();
  }

  onSuperficieInput(campo: 'min' | 'max', valor: string): void {
    const numero = valor.trim() === '' ? null : Number(valor);
    if (campo === 'min') this.superficieMin.set(numero);
    else this.superficieMax.set(numero);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.recargarDesdeInicio(), 350);
  }

  private recargarDesdeInicio(): void {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const resultado = await this.buscarPagina(0);
      this.lotes.set(resultado.contenido);
      this.hayMas.set(resultado.hayMas);
      this.pagina = 0;
    } catch {
      this.errorMessage.set('No se pudieron cargar los lotes. Intenta de nuevo.');
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
      this.lotes.update((actuales) => [...actuales, ...resultado.contenido]);
      this.hayMas.set(resultado.hayMas);
      this.pagina = siguiente;
    } catch {
      this.errorMessage.set('No se pudieron cargar más lotes.');
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  private buscarPagina(pagina: number) {
    return this.lotesService.buscarPaginado({
      manzana: this.filtroManzana().trim() || undefined,
      numeroLote: this.filtroNumeroLote().trim() || undefined,
      desarrolloId: this.filtroDesarrolloId(),
      estado: this.filtroEstado(),
      superficieMin: this.superficieMin(),
      superficieMax: this.superficieMax(),
      pagina,
      tamano: TAMANO_PAGINA,
    });
  }

  async cambiarEstado(lote: Lote, nuevoEstado: string): Promise<void> {
    if (nuevoEstado === lote.estado) return;
    try {
      const actualizado = await this.lotesService.cambiarEstado(lote.id, nuevoEstado);
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
    } catch {
      this.toast.error('No se pudo cambiar el estado del lote.');
    }
  }

  async verHistorial(lote: Lote): Promise<void> {
    this.loteConHistorial.set(lote);
    this.isLoadingHistorial.set(true);
    this.movimientos.set([]);
    try {
      this.movimientos.set(await this.lotesService.listarMovimientos(lote.id));
    } catch {
      this.toast.error('No se pudo cargar el historial de este lote.');
    } finally {
      this.isLoadingHistorial.set(false);
    }
  }

  cerrarHistorial(): void {
    this.loteConHistorial.set(null);
    this.movimientos.set([]);
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

  async eliminar(lote: Lote): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar lote',
      mensaje: `¿Eliminar el lote Manzana ${lote.manzana}, Lote ${lote.numeroLote}? Esto no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;
    try {
      await this.lotesService.eliminar(lote.id);
      this.lotes.update((lista) => lista.filter((l) => l.id !== lote.id));
      this.toast.success('Lote eliminado.');
    } catch {
      this.toast.error('No se pudo eliminar el lote.');
    }
  }
}
