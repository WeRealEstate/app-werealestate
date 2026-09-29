import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  ESTADO_LOTE_POLIGONO_CLASSES,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
  PuntoMapa,
} from '../../../core/models/lote.model';
import { LotesService } from '../../../core/services/lotes.service';
import { ToastService } from '../../../core/services/toast.service';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';

type ProyectoPublico = 'samai' | 'nanuu';

const NOTA_MAX_LENGTH = 500;

/** Mismos límites/pasos de zoom que /panel/plano (ver ese componente para el porqué de cada
 * valor); no se comparte el código porque esta vista no tiene nada de edición. */
const ZOOM_MIN = 1;
const ZOOM_MAX = 15;
const ZOOM_PASO_BOTON = 1.5;
const ZOOM_PASO_RUEDA = 1.15;
const UMBRAL_ARRASTRE_VISTA_PX = 6;

/** Plano interactivo público, en pantalla completa y sin sesión — para compartir el link de un
 * desarrollo directamente (/samai, /aldea-nanuu, ver app.routes.ts). Mismas restricciones que
 * /cotizador-publico/lotes: cualquiera puede ver el estado de cada lote y apartar uno disponible
 * (pidiendo nombre de asesor y de cliente, ver LoteService.cambiarEstadoPublico), pero no editar el
 * plano, delimitar lotes ni liberar uno ya apartado — eso sigue siendo exclusivo de /panel/plano. */
@Component({
  selector: 'app-plano-publico',
  standalone: true,
  imports: [FormsModule, DecimalPipe, ToastContainerComponent],
  templateUrl: './plano-publico.component.html',
})
export class PlanoPublicoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly lotesService = inject(LotesService);
  private readonly toast = inject(ToastService);

  @ViewChild('contenedorPlano') private readonly contenedorPlano?: ElementRef<HTMLElement>;

  private readonly proyecto: ProyectoPublico = this.route.snapshot.data['proyecto'] === 'nanuu' ? 'nanuu' : 'samai';

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly poligonoClases = ESTADO_LOTE_POLIGONO_CLASSES;
  readonly estadosLote = Object.keys(ESTADO_LOTE_LABELS) as EstadoLote[];

  readonly lotes = signal<Lote[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  /** El plano/nombre del desarrollo vienen anidados en cualquiera de sus lotes (todos comparten el
   * mismo desarrollo) — no hace falta un endpoint aparte. */
  readonly planoUrl = computed(() => this.lotes()[0]?.desarrollo.planoUrl ?? null);
  readonly desarrolloNombre = computed(() => this.lotes()[0]?.desarrollo.nombre ?? '');

  readonly loteActivo = signal<Lote | null>(null);

  // Modal "Apartar lote": mismos campos y restricciones que /cotizador-publico/lotes — pide nombre
  // de asesor y de cliente (nota opcional) porque no hay una sesión real detrás de este apartado.
  readonly mostrarApartar = signal(false);
  readonly nombreAsesorInput = signal('');
  readonly nombreClienteInput = signal('');
  readonly notaInput = signal('');
  readonly isApartando = signal(false);
  readonly notaMaxLength = NOTA_MAX_LENGTH;
  readonly nombreAsesorInvalido = computed(() => this.nombreAsesorInput().trim().length === 0);
  readonly nombreClienteInvalido = computed(() => this.nombreClienteInput().trim().length === 0);

  /** Zoom y desplazamiento del plano — idéntico mecanismo que /panel/plano, sin nada de edición. */
  readonly zoom = signal(1);
  readonly zoomMax = ZOOM_MAX;
  readonly panX = signal(0);
  readonly panY = signal(0);
  private arrastreVista: { inicioX: number; inicioY: number; panXInicial: number; panYInicial: number; movioSuficiente: boolean } | null =
    null;
  /** true si el gesto que se acaba de soltar fue un arrastre para desplazar la vista (no un clic
   * real); onClickPoligono lo consume para no abrir el detalle de un lote sin querer. */
  private ultimoGestoFuePan = false;

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.lotes.set(await this.lotesService.listarPublicoPorProyecto(this.proyecto));
    } catch {
      this.errorMessage.set('No se pudo cargar el plano. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  tienePoligono(lote: Lote): boolean {
    return !!lote.mapaPoligono && lote.mapaPoligono.length >= 3;
  }

  /** Convierte los vértices en el atributo "points" que espera <polygon>. */
  puntosSvg(puntos: PuntoMapa[]): string {
    return puntos.map((p) => `${p.x},${p.y}`).join(' ');
  }

  /** El precio siempre es el precio por m² del desarrollo × la superficie, igual que en el panel. */
  precioEstimado(lote: Lote): number {
    return lote.desarrollo.precioM2 * lote.superficie;
  }

  /** Igual que LotesPublicoComponent.puedeCambiarEstado combinado con el filtro de la fila
   * "Apartar": un lote exclusivo de admin no se toca desde aquí, y solo tiene sentido ofrecer
   * apartar uno que esté disponible (liberar uno ya apartado no está permitido públicamente). */
  puedeApartar(lote: Lote): boolean {
    return !ESTADOS_LOTE_SOLO_ADMIN.has(lote.estado) && lote.estado === 'DISPONIBLE';
  }

  onClickPoligono(lote: Lote, event: MouseEvent): void {
    event.stopPropagation();
    if (this.ultimoGestoFuePan) {
      this.ultimoGestoFuePan = false;
      return;
    }
    this.loteActivo.set(this.loteActivo()?.id === lote.id ? null : lote);
  }

  cerrarDetalle(): void {
    this.loteActivo.set(null);
    this.mostrarApartar.set(false);
  }

  abrirApartar(): void {
    this.mostrarApartar.set(true);
    this.nombreAsesorInput.set('');
    this.nombreClienteInput.set('');
    this.notaInput.set('');
  }

  cancelarApartar(): void {
    this.mostrarApartar.set(false);
  }

  async confirmarApartar(): Promise<void> {
    const lote = this.loteActivo();
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
      this.loteActivo.set(actualizado);
      this.toast.success('Lote apartado.');
      this.mostrarApartar.set(false);
    } catch {
      this.toast.error('No se pudo apartar el lote. Intenta de nuevo.');
    } finally {
      this.isApartando.set(false);
    }
  }

  // ---- Zoom y desplazamiento (ver PlanoComponent, mismo mecanismo exacto) ----

  private zoomEn(clientX: number, clientY: number, nuevoZoom: number): void {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return;
    const rect = contenedor.getBoundingClientRect();
    const zoomActual = this.zoom();
    const zoomFinal = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, nuevoZoom));
    if (zoomFinal === zoomActual) return;

    const puntoX = clientX - rect.left;
    const puntoY = clientY - rect.top;
    const contenidoX = (puntoX - this.panX()) / zoomActual;
    const contenidoY = (puntoY - this.panY()) / zoomActual;

    this.zoom.set(zoomFinal);
    this.panX.set(puntoX - contenidoX * zoomFinal);
    this.panY.set(puntoY - contenidoY * zoomFinal);
    this.limitarPan(rect);
  }

  private zoomEnCentro(nuevoZoom: number): void {
    const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
    if (!rect) return;
    this.zoomEn(rect.left + rect.width / 2, rect.top + rect.height / 2, nuevoZoom);
  }

  private limitarPan(rect: DOMRect): void {
    const zoom = this.zoom();
    const minPanX = rect.width - rect.width * zoom;
    const minPanY = rect.height - rect.height * zoom;
    this.panX.set(Math.min(0, Math.max(minPanX, this.panX())));
    this.panY.set(Math.min(0, Math.max(minPanY, this.panY())));
  }

  acercarZoom(): void {
    this.zoomEnCentro(this.zoom() * ZOOM_PASO_BOTON);
  }

  alejarZoom(): void {
    this.zoomEnCentro(this.zoom() / ZOOM_PASO_BOTON);
  }

  restablecerZoom(): void {
    this.zoom.set(1);
    this.panX.set(0);
    this.panY.set(0);
  }

  onWheelImagen(event: WheelEvent): void {
    event.preventDefault();
    const factor = event.deltaY < 0 ? ZOOM_PASO_RUEDA : 1 / ZOOM_PASO_RUEDA;
    this.zoomEn(event.clientX, event.clientY, this.zoom() * factor);
  }

  onPointerDownVista(event: PointerEvent): void {
    if (this.zoom() <= ZOOM_MIN) return;
    this.arrastreVista = {
      inicioX: event.clientX,
      inicioY: event.clientY,
      panXInicial: this.panX(),
      panYInicial: this.panY(),
      movioSuficiente: false,
    };
  }

  @HostListener('document:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.arrastreVista) return;
    const dx = event.clientX - this.arrastreVista.inicioX;
    const dy = event.clientY - this.arrastreVista.inicioY;
    if (!this.arrastreVista.movioSuficiente && Math.hypot(dx, dy) > UMBRAL_ARRASTRE_VISTA_PX) {
      this.arrastreVista.movioSuficiente = true;
    }
    if (this.arrastreVista.movioSuficiente) {
      this.panX.set(this.arrastreVista.panXInicial + dx);
      this.panY.set(this.arrastreVista.panYInicial + dy);
      const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
      if (rect) this.limitarPan(rect);
    }
  }

  @HostListener('document:pointerup')
  onPointerUp(): void {
    if (!this.arrastreVista) return;
    this.ultimoGestoFuePan = this.arrastreVista.movioSuficiente;
    this.arrastreVista = null;
  }
}
