import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import {
  LucideChevronDown,
  LucideDollarSign,
  LucideEye,
  LucideHistory,
  LucidePencil,
  LucideTrash2,
  LucideUpload,
} from '@lucide/angular';
import { FormsModule, NgModel } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { LoteCambioEstadoModalComponent } from '../../../shared/lote-cambio-estado-modal/lote-cambio-estado-modal.component';
import { LoteInfoModalComponent } from '../../../shared/lote-info-modal/lote-info-modal.component';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo } from '../../../core/models/lead.model';
import { POSICION_MENU_DERECHA } from '../../../shared/menu/posicion-menu';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  ESTADOS_DE_VENTA,
  ESTADOS_LOTE_ADMIN_O_LIDER,
  ESTADOS_LOTE_ORDEN,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
  estadosParaAdmin,
} from '../../../core/models/lote.model';
import { MonedaInputDirective } from '../../../shared/moneda-input/moneda-input.directive';

const TAMANO_PAGINA = 20;

/** Los 6 estados los puede ver cualquiera; para elegir uno nuevo, un no-admin/líder solo puede
 * moverse entre Disponible/Apartado (y solo si el lote no está ya en un estado exclusivo de
 * admin, o de admin/líder). Admin y administración: ver estadosParaAdmin. */
const ESTADOS_ASESOR: EstadoLote[] = ['DISPONIBLE', 'APARTADO'];

@Component({
  selector: 'app-lotes-list',
  standalone: true,
  imports: [MonedaInputDirective, WeLoaderComponent, 
    FormsModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    LucideChevronDown,
    LucideDollarSign,
    LucideEye,
    LucideHistory,
    LucidePencil,
    LucideTrash2,
    LucideUpload,
    LoteCambioEstadoModalComponent,
    LoteInfoModalComponent,
  ],
  templateUrl: './lotes-list.component.html',
})
export class LotesListComponent {
  readonly posicionMenu = POSICION_MENU_DERECHA;
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  /** Opciones del filtro: los 6 de siempre; "Reservado por We" se agrega aparte, solo para admin y administración. */
  readonly estadosLote = ESTADOS_LOTE_ORDEN;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly esLider = computed(() => this.auth.currentUser()?.rol === 'LIDER_AREA');
  /** Mismo permiso que el módulo de ventas (roleGuard ADMIN/LIDER_AREA): solo ellos pueden ver la
   * información de un lote comprometido (apartado, en firma o vendido). */
  readonly puedeVerInfoLote = computed(() => this.esAdmin() || this.esLider());

  /** Lote sobre el que se pidió "ver información" (ver LoteInfoModalComponent); null cierra el modal. */
  readonly loteInfo = signal<Lote | null>(null);

  /** Cambio de estado que un admin o líder de área está por confirmar (ver
   * LoteCambioEstadoModalComponent); null cuando el modal está cerrado. Un asesor nunca pasa por
   * aquí — su cambio se aplica directo. selector: el del renglón, para regresarlo si se cancela. */
  readonly cambioEstadoPendiente = signal<{ lote: Lote; nuevoEstado: EstadoLote; selector?: NgModel } | null>(null);

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
    // Filtros que llegan por enlace (p. ej. desde las cifras del inicio: ?desarrolloId=1&estado=VENDIDO).
    const params = inject(ActivatedRoute).snapshot.queryParamMap;
    const desarrolloId = Number(params.get('desarrolloId'));
    if (Number.isInteger(desarrolloId) && desarrolloId > 0) this.filtroDesarrolloId.set(desarrolloId);
    const estado = params.get('estado');
    if (estado && estado in ESTADO_LOTE_LABELS) {
      this.filtroEstado.set(estado as EstadoLote);
    }

    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    this.cargar();
  }

  /** El precio sale del lote (precio por m² propio, o el del desarrollo si no tiene) × su superficie. */
  // ---- Precio por m² de una manzana completa (ej. hectáreas) ----
  readonly mostrarPrecioManzana = signal(false);
  readonly pmDesarrolloId = signal<number | null>(null);
  readonly pmManzana = signal('');
  readonly pmPrecio = signal<number | null>(null);
  readonly pmError = signal<string | null>(null);
  readonly pmGuardando = signal(false);

  abrirPrecioManzana(): void {
    this.pmDesarrolloId.set(this.filtroDesarrolloId() ?? this.desarrollos()[0]?.id ?? null);
    this.pmManzana.set(this.filtroManzana());
    this.pmPrecio.set(null);
    this.pmError.set(null);
    this.mostrarPrecioManzana.set(true);
  }

  async guardarPrecioManzana(quitar = false): Promise<void> {
    const desarrolloId = this.pmDesarrolloId();
    const manzana = this.pmManzana().trim();
    const precio = quitar ? null : this.pmPrecio();
    if (desarrolloId === null || !manzana) {
      this.pmError.set('Elige el desarrollo y escribe la manzana.');
      return;
    }
    if (!quitar && (precio === null || precio <= 0)) {
      this.pmError.set('Escribe el precio por m² o usa "Quitar precio propio".');
      return;
    }
    this.pmGuardando.set(true);
    this.pmError.set(null);
    try {
      const r = await this.lotesService.actualizarPrecioManzana(desarrolloId, manzana, precio);
      this.mostrarPrecioManzana.set(false);
      this.toast.success(
        quitar
          ? `${r.actualizados} lote(s) de la manzana ${manzana} vuelven al precio del desarrollo.`
          : `Precio actualizado en ${r.actualizados} lote(s) de la manzana ${manzana}.`,
      );
      this.cargar();
    } catch (error) {
      this.pmError.set(this.mensajeError(error, 'No se pudo actualizar el precio.'));
    } finally {
      this.pmGuardando.set(false);
    }
  }

  precioEstimado(lote: Lote): number {
    return lote.precio;
  }

  /** Un admin y un líder de área tienen control total sobre el estado de cualquier lote; un
   * asesor solo se mueve entre Disponible/Apartado, y ni siquiera puede sacar un lote ya
   * comprometido (apartado a plazo, con dinero, en firma o vendido) de ese estado. */
  estadosDisponiblesPara(lote: Lote): EstadoLote[] {
    if (this.esAdmin() || this.esLider()) return estadosParaAdmin(lote);
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
    const limpio = valor.replace(/,/g, '').trim();
    const numero = limpio === '' ? null : Number(limpio);
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
      // Para admin y administración "Vendido" son solo los vendidos de verdad; los reservados por We tienen su opción.
      reservadoWe: this.puedeVerInfoLote() && this.filtroEstado() === 'VENDIDO' ? false : null,
      pagina,
      tamano: TAMANO_PAGINA,
    });
  }

  /** Un asesor solo se mueve entre Disponible/Apartado y el cambio se aplica directo; un admin o
   * líder de área puede mover un lote a cualquier estado, así que se le pide justificar el cambio
   * con una nota (y, solo para "Apartado a plazo", también la fecha de vencimiento) en un modal
   * antes de llamar a la API. El <select> ya quedó visualmente en la nueva opción, pero como no
   * tocamos `lote.estado` (el array `lotes()` no cambia), al cancelar se regresa el selector al estado real.
   * Vendido y En proceso de firma los pone la venta: para un lote sin venta, elegirlos lleva a registrarla. */
  async cambiarEstado(lote: Lote, nuevoEstado: string, selector?: NgModel): Promise<void> {
    if (nuevoEstado === lote.estado) return;

    if (this.esAdmin() || this.esLider()) {
      if (lote.ventaNumero == null && ESTADOS_DE_VENTA.has(nuevoEstado as EstadoLote)) {
        await this.irARegistrarVenta(lote, selector);
        return;
      }
      this.cambioEstadoPendiente.set({ lote, nuevoEstado: nuevoEstado as EstadoLote, selector });
      return;
    }

    try {
      const actualizado = await this.lotesService.cambiarEstado(lote.id, nuevoEstado);
      this.lotes.update((lista) => lista.map((l) => (l.id === lote.id ? actualizado : l)));
    } catch (error) {
      this.toast.error(this.mensajeError(error, 'No se pudo cambiar el estado del lote.'));
    }
  }

  /** Vendido y En proceso de firma solo los pone una venta: se confirma y se abre Nueva venta con el desarrollo y el
   * lote ya elegidos; el lote cambia al guardarla (según el contrato). */
  private async irARegistrarVenta(lote: Lote, selector?: NgModel): Promise<void> {
    const ir = await this.confirmService.confirm({
      titulo: 'Registrar la venta',
      mensaje:
        'Vendido y En proceso de firma los pone la venta: al registrarla, el lote queda En proceso de firma y, con el ' +
        `contrato firmado, Vendido. ¿Abrir Nueva venta con el Lote ${lote.numeroLote} de la Manzana ${lote.manzana}?`,
      textoConfirmar: 'Registrar venta',
    });
    if (!ir) {
      this.regresarSelector(lote, selector);
      return;
    }
    void this.router.navigate(['/panel/ventas/nueva'], {
      queryParams: { desarrolloId: lote.desarrollo.id, loteId: lote.id },
    });
  }

  /** El selector ya muestra la opción elegida; si el cambio no sigue, vuelve al estado real del lote. */
  private regresarSelector(lote: Lote, selector?: NgModel): void {
    selector?.control.setValue(lote.estado, { emitViewToModelChange: false });
  }

  cancelarCambioEstado(): void {
    const pendiente = this.cambioEstadoPendiente();
    if (pendiente) this.regresarSelector(pendiente.lote, pendiente.selector);
    this.cambioEstadoPendiente.set(null);
  }

  onCambioEstadoConfirmado(actualizado: Lote): void {
    this.lotes.update((lista) => lista.map((l) => (l.id === actualizado.id ? actualizado : l)));
    this.cambioEstadoPendiente.set(null);
  }

  private mensajeError(error: unknown, porDefecto: string): string {
    return error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
      ? error.error.message
      : porDefecto;
  }

  verInfoLote(lote: Lote): void {
    this.loteInfo.set(lote);
  }

  cerrarInfoLote(): void {
    this.loteInfo.set(null);
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
