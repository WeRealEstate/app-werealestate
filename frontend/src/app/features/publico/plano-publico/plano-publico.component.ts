import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
const UMBRAL_ARRASTRE_VISTA_PX = 6;

/** Rueda de mouse (~100 por muesca ≈ x1.16, igual que el paso fijo de antes) y pellizco de trackpad
 * (el navegador lo manda como rueda con deltas chicos, por eso necesita más sensibilidad). */
const SENSIBILIDAD_RUEDA = 0.0015;
const SENSIBILIDAD_PELLIZCO_TRACKPAD = 0.01;
/** Cuánto acerca un doble clic/toque, y qué tan rápido y cerca deben ser dos toques para contar. */
const ZOOM_DOBLE_TOQUE = 3;
const VENTANA_DOBLE_TOQUE_MS = 300;
const DISTANCIA_DOBLE_TOQUE_PX = 30;
const PASO_TECLADO_PX = 80;
const DURACION_ANIMACION_MS = 220;

/** Plano interactivo público, en pantalla completa y sin sesión — para compartir el link de un
 * desarrollo directamente (/samai, /aldea-nanuu, ver app.routes.ts). Mismas restricciones que
 * /cotizador-publico/lotes: cualquiera puede ver el estado de cada lote y apartar uno disponible
 * (pidiendo nombre de asesor y de cliente, ver LoteService.cambiarEstadoPublico), pero no editar el
 * plano, delimitar lotes ni liberar uno ya apartado — eso sigue siendo exclusivo de /panel/plano. */
@Component({
  selector: 'app-plano-publico',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink, ToastContainerComponent],
  templateUrl: './plano-publico.component.html',
})
export class PlanoPublicoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly lotesService = inject(LotesService);
  private readonly toast = inject(ToastService);

  @ViewChild('contenedorPlano') private readonly contenedorPlano?: ElementRef<HTMLElement>;

  /** Fijo por la ruta (/samai o /aldea-nanuu, ver app.routes.ts) — a diferencia del panel, esta
   * página nunca cotiza más de un desarrollo, así que el HTML lo usa directo para armar el link a
   * /cotizador-publico sin necesitar un mapeo desde lote.desarrollo.nombre. */
  readonly proyecto: ProyectoPublico = this.route.snapshot.data['proyecto'] === 'nanuu' ? 'nanuu' : 'samai';

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

  /** Zoom y desplazamiento del plano — mismo mecanismo que /panel/plano, sin nada de edición, pero
   * con un piso de zoom dinámico (ver zoomCubrir) en vez de 1: a diferencia del panel, aquí el
   * contenedor siempre es la pantalla completa (h-full), así que a zoom 1 (tamaño natural de la
   * imagen) casi nunca coincide con el alto real de la pantalla — deja una franja en blanco abajo
   * si la imagen es más ancha que alta. */
  readonly zoom = signal(1);
  readonly zoomMax = ZOOM_MAX;
  readonly zoomCubrir = signal(ZOOM_MIN);
  readonly panX = signal(0);
  readonly panY = signal(0);
  /** true solo durante los cambios discretos (botones/teclado/doble toque) para animar la transición. */
  readonly animando = signal(false);
  private naturalWidth = 0;
  private naturalHeight = 0;
  private arrastreVista: { inicioX: number; inicioY: number; panXInicial: number; panYInicial: number; movioSuficiente: boolean } | null =
    null;
  /** true si el gesto que se acaba de soltar fue un arrastre para desplazar la vista (no un clic
   * real); onClickPoligono lo consume para no abrir el detalle de un lote sin querer. */
  private ultimoGestoFuePan = false;

  // Multitouch: dedos/punteros apoyados ahora mismo, y el último estado medido del pellizco.
  private readonly punteros = new Map<number, { x: number; y: number }>();
  private ultimoPellizco: { distancia: number; cx: number; cy: number } | null = null;
  private hizoPellizco = false;
  private toqueActual: { x: number; y: number; sobreLote: boolean } | null = null;
  private ultimoToque: { x: number; y: number; t: number } | null = null;
  private ultimoDobleToque = 0;

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

  // ---- Zoom y desplazamiento (ver PlanoComponent; el piso es zoomCubrir(), no ZOOM_MIN) ----

  /** Alto que tendría la imagen en pantalla a zoom 1: como el ancho siempre es el del contenedor
   * (class="w-full" en el <img>), el alto natural es ese ancho × la proporción real de la imagen. */
  private altoNaturalEnPantalla(anchoContenedor: number): number {
    if (this.naturalWidth === 0) return anchoContenedor;
    return anchoContenedor * (this.naturalHeight / this.naturalWidth);
  }

  /** Al cargar la imagen (y si la ventana cambia de tamaño): calcula el zoom mínimo que hace que la
   * imagen cubra toda la pantalla sin dejar franjas en blanco — el equivalente de
   * "background-size: cover", pero aplicado al mismo zoom/pan del visor en vez de a CSS puro, para
   * que el overlay de polígonos (que vive dentro del mismo div escalado) se mantenga perfectamente
   * alineado con la imagen sin importar el recorte. Deja la imagen centrada a ese zoom. */
  private ajustarZoomParaCubrirPantalla(): void {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor || this.naturalWidth === 0) return;
    const rect = contenedor.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    const altoAZoom1 = this.altoNaturalEnPantalla(rect.width);
    const zoomCubrir = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, rect.height / altoAZoom1));
    this.zoomCubrir.set(zoomCubrir);

    this.zoom.set(zoomCubrir);
    this.panX.set((rect.width - rect.width * zoomCubrir) / 2);
    this.panY.set((rect.height - altoAZoom1 * zoomCubrir) / 2);
    this.limitarPan(rect);
  }

  /** Se dispara cuando el <img> termina de cargar — recién ahí se conoce su tamaño real. */
  onImagenCargada(event: Event): void {
    const img = event.target as HTMLImageElement;
    this.naturalWidth = img.naturalWidth;
    this.naturalHeight = img.naturalHeight;
    this.ajustarZoomParaCubrirPantalla();
  }

  @HostListener('window:resize')
  onResizeVentana(): void {
    this.ajustarZoomParaCubrirPantalla();
  }

  private zoomEn(clientX: number, clientY: number, nuevoZoom: number): void {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return;
    const rect = contenedor.getBoundingClientRect();
    const zoomActual = this.zoom();
    const zoomFinal = Math.min(ZOOM_MAX, Math.max(this.zoomCubrir(), nuevoZoom));
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

  /** A diferencia de PlanoComponent, el alto de la imagen a zoom 1 no siempre es rect.height (ver
   * altoNaturalEnPantalla) — así que el límite de paneo vertical se calcula sobre el alto real, no
   * sobre el del contenedor. En el ancho sí coinciden siempre (class="w-full"). */
  private limitarPan(rect: DOMRect): void {
    const zoom = this.zoom();
    const anchoContenido = rect.width * zoom;
    const altoContenido = this.altoNaturalEnPantalla(rect.width) * zoom;
    const minPanX = Math.min(0, rect.width - anchoContenido);
    const minPanY = Math.min(0, rect.height - altoContenido);
    this.panX.set(Math.min(0, Math.max(minPanX, this.panX())));
    this.panY.set(Math.min(0, Math.max(minPanY, this.panY())));
  }

  /** Los cambios discretos (botones, teclado, doble toque) se animan con una transición corta; los
   * continuos (arrastre, pellizco, rueda) no: con transición el plano se sentiría "arrastrando
   * tarde" detrás del dedo. */
  private animar(accion: () => void): void {
    this.animando.set(true);
    accion();
    setTimeout(() => this.animando.set(false), DURACION_ANIMACION_MS);
  }

  acercarZoom(): void {
    this.animar(() => this.zoomEnCentro(this.zoom() * ZOOM_PASO_BOTON));
  }

  alejarZoom(): void {
    this.animar(() => this.zoomEnCentro(this.zoom() / ZOOM_PASO_BOTON));
  }

  /** "Restablecer" vuelve al zoom que cubre toda la pantalla (centrado), no a zoom 1 — volver a
   * zoom 1 reintroduciría la franja en blanco que este mismo mecanismo evita. */
  restablecerZoom(): void {
    this.animar(() => this.ajustarZoomParaCubrirPantalla());
  }

  /** Proporcional a cuánto giró la rueda: una rueda de mouse (saltos grandes) se siente igual que
   * antes, y un trackpad (muchos eventos chicos) acerca de forma continua en vez de dar un salto
   * fijo por evento. ctrlKey = pellizco en trackpad (el navegador lo manda como rueda + ctrl). */
  onWheelImagen(event: WheelEvent): void {
    event.preventDefault();
    const sensibilidad = event.ctrlKey ? SENSIBILIDAD_PELLIZCO_TRACKPAD : SENSIBILIDAD_RUEDA;
    const factor = Math.exp(-event.deltaY * sensibilidad);
    this.zoomEn(event.clientX, event.clientY, this.zoom() * factor);
  }

  /** Doble clic (mouse): acerca justo donde se hizo, o regresa a la vista completa si ya hay zoom.
   * Se ignora sobre un lote (ahí un clic abre su detalle) y justo después de un doble toque ya
   * resuelto a mano (algunos navegadores táctiles disparan también dblclick). */
  onDobleClickImagen(event: MouseEvent): void {
    if (this.esSobreLote(event.target) || Date.now() - this.ultimoDobleToque < 600) return;
    this.alternarZoomEn(event.clientX, event.clientY);
  }

  private alternarZoomEn(clientX: number, clientY: number): void {
    this.animar(() => {
      if (this.zoom() > this.zoomCubrir() * 1.05) {
        this.ajustarZoomParaCubrirPantalla();
      } else {
        this.zoomEn(clientX, clientY, this.zoom() * ZOOM_DOBLE_TOQUE);
      }
    });
  }

  private esSobreLote(target: EventTarget | null): boolean {
    return target instanceof Element && target.closest('polygon') !== null;
  }

  /** Atajos de teclado: + / - zoom, 0 restablece, flechas desplazan (Shift = más rápido). Se
   * ignoran con un modal abierto o escribiendo en un campo (Esc para cerrar modales lo maneja
   * AccesibilidadTecladoService). */
  @HostListener('document:keydown', ['$event'])
  onTecla(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey) return;
    if (event.target instanceof Element && event.target.closest('input, textarea, select, [contenteditable]')) return;
    if (this.mostrarApartar() || this.loteActivo()) return;

    const paso = event.shiftKey ? PASO_TECLADO_PX * 3 : PASO_TECLADO_PX;
    switch (event.key) {
      case '+':
      case '=':
        this.acercarZoom();
        break;
      case '-':
      case '_':
        this.alejarZoom();
        break;
      case '0':
        this.restablecerZoom();
        break;
      case 'ArrowLeft':
        this.moverVista(paso, 0);
        break;
      case 'ArrowRight':
        this.moverVista(-paso, 0);
        break;
      case 'ArrowUp':
        this.moverVista(0, paso);
        break;
      case 'ArrowDown':
        this.moverVista(0, -paso);
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  private moverVista(dx: number, dy: number): void {
    const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
    if (!rect) return;
    this.animar(() => {
      this.panX.update((x) => x + dx);
      this.panY.update((y) => y + dy);
      this.limitarPan(rect);
    });
  }

  /** Si hay algo de la imagen fuera de la vista en cualquiera de los dos ejes — no necesariamente
   * ambos: en una pantalla mucho más ancha que el plano, el zoom mínimo (zoomCubrir) ya cubre el
   * ancho exacto (sin margen) pero puede seguir sin alcanzar a cubrir el alto completo (el piso de
   * zoom no baja de 1, ver ajustarZoomParaCubrirPantalla), recortando arriba/abajo — y es
   * justo ahí donde hace falta poder arrastrar para ver el resto, aunque el zoom no haya subido del
   * mínimo. Antes esto se decidía comparando contra zoomCubrir(), lo cual bloqueaba el arrastre
   * exactamente en ese caso. */
  hayAlgoFueraDeVista(): boolean {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return false;
    const rect = contenedor.getBoundingClientRect();
    const zoom = this.zoom();
    const anchoContenido = rect.width * zoom;
    const altoContenido = this.altoNaturalEnPantalla(rect.width) * zoom;
    const margen = 0.5;
    return anchoContenido > rect.width + margen || altoContenido > rect.height + margen;
  }

  /** Un dedo/mouse: arrastra la vista. Dos dedos: pellizco (zoom) + desplazamiento a la vez. */
  onPointerDownVista(event: PointerEvent): void {
    this.punteros.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (this.punteros.size >= 2) {
      this.arrastreVista = null;
      this.hizoPellizco = true;
      this.ultimoPellizco = this.medirPellizco();
      return;
    }

    this.hizoPellizco = false;
    if (event.pointerType === 'touch') {
      this.toqueActual = { x: event.clientX, y: event.clientY, sobreLote: this.esSobreLote(event.target) };
    }
    if (!this.hayAlgoFueraDeVista()) return;
    this.arrastreVista = {
      inicioX: event.clientX,
      inicioY: event.clientY,
      panXInicial: this.panX(),
      panYInicial: this.panY(),
      movioSuficiente: false,
    };
  }

  /** Distancia entre los dos primeros dedos y el punto medio — base de cada paso del pellizco. */
  private medirPellizco(): { distancia: number; cx: number; cy: number } | null {
    const [a, b] = [...this.punteros.values()];
    if (!a || !b) return null;
    return { distancia: Math.hypot(b.x - a.x, b.y - a.y), cx: (a.x + b.x) / 2, cy: (a.y + b.y) / 2 };
  }

  @HostListener('document:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (this.punteros.has(event.pointerId)) {
      this.punteros.set(event.pointerId, { x: event.clientX, y: event.clientY });
    }

    if (this.punteros.size >= 2 && this.ultimoPellizco) {
      const actual = this.medirPellizco();
      const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
      if (!actual || !rect || actual.distancia === 0 || this.ultimoPellizco.distancia === 0) return;
      // Primero se desplaza lo que se movió el punto medio, y luego se hace zoom alrededor del
      // nuevo punto medio: así el punto del plano bajo los dedos se queda bajo los dedos.
      this.panX.update((x) => x + actual.cx - this.ultimoPellizco!.cx);
      this.panY.update((y) => y + actual.cy - this.ultimoPellizco!.cy);
      this.limitarPan(rect);
      this.zoomEn(actual.cx, actual.cy, this.zoom() * (actual.distancia / this.ultimoPellizco.distancia));
      this.ultimoPellizco = actual;
      return;
    }

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

  @HostListener('document:pointerup', ['$event'])
  @HostListener('document:pointercancel', ['$event'])
  onPointerUp(event: PointerEvent): void {
    const eraPellizco = this.punteros.size >= 2;
    this.punteros.delete(event.pointerId);
    this.ultimoPellizco = null;

    if (eraPellizco) {
      // Un dedo sigue apoyado: que continúe como arrastre normal desde donde está, sin saltos,
      // y que levantar los dedos no cuente como un toque sobre un lote.
      const [restante] = [...this.punteros.values()];
      this.arrastreVista = restante
        ? { inicioX: restante.x, inicioY: restante.y, panXInicial: this.panX(), panYInicial: this.panY(), movioSuficiente: true }
        : null;
      this.marcarGestoComoPan();
      return;
    }

    if (this.arrastreVista) {
      if (this.arrastreVista.movioSuficiente) this.marcarGestoComoPan();
      this.arrastreVista = null;
    }

    if (event.type === 'pointerup' && event.pointerType === 'touch') this.registrarToque(event);
  }

  /** El flag solo debe sobrevivir hasta el click que el navegador dispara justo después del
   * pointerup (si es que lo dispara): si no, quedaría "pegado" y se tragaría el siguiente toque real. */
  private marcarGestoComoPan(): void {
    this.ultimoGestoFuePan = true;
    setTimeout(() => (this.ultimoGestoFuePan = false), 0);
  }

  /** Doble toque (dedo): mismo efecto que el doble clic. Un toque cuenta solo si el dedo casi no se
   * movió y no fue sobre un lote (ahí el primer toque ya abrió su detalle). */
  private registrarToque(event: PointerEvent): void {
    const inicio = this.toqueActual;
    this.toqueActual = null;
    if (!inicio || inicio.sobreLote || this.hizoPellizco) return;
    if (Math.hypot(event.clientX - inicio.x, event.clientY - inicio.y) > UMBRAL_ARRASTRE_VISTA_PX) {
      this.ultimoToque = null;
      return;
    }

    const ahora = Date.now();
    const previo = this.ultimoToque;
    if (previo && ahora - previo.t < VENTANA_DOBLE_TOQUE_MS && Math.hypot(inicio.x - previo.x, inicio.y - previo.y) < DISTANCIA_DOBLE_TOQUE_PX) {
      this.ultimoToque = null;
      this.ultimoDobleToque = ahora;
      this.alternarZoomEn(inicio.x, inicio.y);
      return;
    }
    this.ultimoToque = { x: inicio.x, y: inicio.y, t: ahora };
  }
}
