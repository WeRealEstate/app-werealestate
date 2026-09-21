import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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
  ESTADOS_LOTE_ADMIN_O_LIDER,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
} from '../../../core/models/lote.model';
import {
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';

const TAMANO_PAGINA = 20;

/** Los 6 estados los puede ver cualquiera; para elegir uno nuevo, un no-admin/líder solo puede
 * moverse entre Disponible/Apartado (y solo si el lote no está ya en un estado exclusivo de
 * admin, o de admin/líder). */
const ESTADOS_ASESOR: EstadoLote[] = ['DISPONIBLE', 'APARTADO'];
const ESTADOS_TODOS: EstadoLote[] = [
  'DISPONIBLE',
  'APARTADO',
  'APARTADO_A_PLAZO',
  'APARTADO_CON_DINERO',
  'EN_PROCESO_DE_FIRMA',
  'VENDIDO',
];

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
  readonly esLider = computed(() => this.auth.currentUser()?.rol === 'LIDER_AREA');

  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;

  /** Cambio de estado que un admin o líder de área está por confirmar: les pide siempre una nota
   * (y, solo para "Apartado a plazo", también la fecha/hora de vencimiento). null cuando el modal
   * está cerrado. Un asesor nunca pasa por aquí — su cambio se aplica directo. */
  readonly cambioEstadoPendiente = signal<{ lote: Lote; nuevoEstado: EstadoLote } | null>(null);
  readonly notaCambioEstado = signal('');
  readonly fechaPlazo = signal('');
  readonly horaPlazo = signal(HORA_POR_DEFECTO);
  readonly minutoPlazo = signal(MINUTO_POR_DEFECTO);
  readonly guardandoCambioEstado = signal(false);

  readonly cambioEstadoInvalido = computed(() => {
    const pendiente = this.cambioEstadoPendiente();
    if (!pendiente || !this.notaCambioEstado().trim()) return true;
    return pendiente.nuevoEstado === 'APARTADO_A_PLAZO' && !this.fechaPlazo();
  });

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

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    this.cargar();
  }

  /** El precio ya no se captura a mano: siempre es el precio por m² del desarrollo × la superficie. */
  precioEstimado(lote: Lote): number {
    return lote.desarrollo.precioM2 * lote.superficie;
  }

  /** Un admin y un líder de área tienen control total sobre el estado de cualquier lote; un
   * asesor solo se mueve entre Disponible/Apartado, y ni siquiera puede sacar un lote ya
   * comprometido (apartado a plazo, con dinero, en firma o vendido) de ese estado. */
  estadosDisponiblesPara(lote: Lote): EstadoLote[] {
    if (this.esAdmin() || this.esLider()) return ESTADOS_TODOS;
    const bloqueadoParaAsesor =
      ESTADOS_LOTE_SOLO_ADMIN.has(lote.estado) || ESTADOS_LOTE_ADMIN_O_LIDER.has(lote.estado);
    return bloqueadoParaAsesor ? [lote.estado] : ESTADOS_ASESOR;
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

  /** Un asesor solo se mueve entre Disponible/Apartado y el cambio se aplica directo; un admin o
   * líder de área puede mover un lote a cualquier estado, así que se le pide justificar el cambio
   * con una nota (y, solo para "Apartado a plazo", también la fecha de vencimiento) en un modal
   * antes de llamar a la API. El <select> ya quedó visualmente en la nueva opción, pero como no
   * tocamos `lote.estado` (el array `lotes()` no cambia) vuelve solo a mostrar el estado real si
   * se cancela el modal. */
  async cambiarEstado(lote: Lote, nuevoEstado: string): Promise<void> {
    if (nuevoEstado === lote.estado) return;

    if (this.esAdmin() || this.esLider()) {
      this.abrirModalCambioEstado(lote, nuevoEstado as EstadoLote);
      return;
    }

    try {
      const actualizado = await this.lotesService.cambiarEstado(lote.id, nuevoEstado);
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
    } catch (error) {
      this.toast.error(this.mensajeError(error, 'No se pudo cambiar el estado del lote.'));
    }
  }

  abrirModalCambioEstado(lote: Lote, nuevoEstado: EstadoLote): void {
    this.cambioEstadoPendiente.set({ lote, nuevoEstado });
    this.notaCambioEstado.set('');
    this.fechaPlazo.set('');
    this.horaPlazo.set(HORA_POR_DEFECTO);
    this.minutoPlazo.set(MINUTO_POR_DEFECTO);
  }

  cancelarCambioEstado(): void {
    this.cambioEstadoPendiente.set(null);
  }

  async confirmarCambioEstado(): Promise<void> {
    const pendiente = this.cambioEstadoPendiente();
    if (!pendiente || this.cambioEstadoInvalido()) return;

    this.guardandoCambioEstado.set(true);
    try {
      const fechaExpira =
        pendiente.nuevoEstado === 'APARTADO_A_PLAZO'
          ? combinarFechaHora(this.fechaPlazo(), this.horaPlazo(), this.minutoPlazo())
          : undefined;
      const actualizado = await this.lotesService.cambiarEstado(
        pendiente.lote.id,
        pendiente.nuevoEstado,
        fechaExpira,
        this.notaCambioEstado().trim(),
      );
      this.lotes.update((lista) => lista.map((l) => (l.id === pendiente.lote.id ? actualizado : l)));
      this.cambioEstadoPendiente.set(null);
      this.toast.success('Estado del lote actualizado.');
    } catch (error) {
      this.toast.error(this.mensajeError(error, 'No se pudo cambiar el estado del lote.'));
    } finally {
      this.guardandoCambioEstado.set(false);
    }
  }

  private mensajeError(error: unknown, porDefecto: string): string {
    return error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
      ? error.error.message
      : porDefecto;
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
