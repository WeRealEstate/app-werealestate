import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, effect, ElementRef, HostListener, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
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
import { colorDeBarra, fondoDePagina } from '../../../core/utils/pagina';
import { DetectorDeRueda } from '../../../core/utils/rueda';
import { ToastService } from '../../../core/services/toast.service';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';

type ProyectoPublico = 'samai' | 'nanuu';

const NOTA_MAX_LENGTH = 500;

/** Interruptor de la optimización "trazo congelado": mientras dura un gesto (zoom, pellizco,
 * arrastre) el grosor de los bordes de los lotes NO se recalcula — cambiarlo en cada paso obliga al
 * navegador a reevaluar el estilo de todos los polígonos —, y se ajusta una sola vez al soltar.
 * Para deshacerla sin tocar nada más: ponerlo en false (vuelve al comportamiento anterior, el
 * grosor sigue al zoom en vivo), o revertir el commit de esta optimización (git revert). */
const CONGELAR_TRAZO_EN_GESTO = true;
const CLAVE_ASESOR = 'plano-publico-asesor-';

/** Mismos límites/pasos de zoom que /panel/plano (ver ese componente para el porqué de cada
 * valor); no se comparte el código porque esta vista no tiene nada de edición. */
/** Tope inferior de seguridad del zoom de ajuste: un plano absurdamente alto/angosto no debe
 * reducirse hasta desaparecer. */
const ZOOM_MIN = 0.1;
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
const CLAVE_AVISO_ROTAR = 'plano-publico-aviso-rotar';
const DURACION_AVISO_ROTAR_MS = 9000;

function leerAsesorGuardado(proyecto: string): string | null {
  try {
    return sessionStorage.getItem(CLAVE_ASESOR + proyecto);
  } catch {
    return null;
  }
}

function guardarAsesor(proyecto: string, nombre: string | null): void {
  try {
    if (nombre) sessionStorage.setItem(CLAVE_ASESOR + proyecto, nombre);
    else sessionStorage.removeItem(CLAVE_ASESOR + proyecto);
  } catch {
    // Sin almacenamiento: el acceso solo dura hasta recargar.
  }
}

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
  private readonly destroyRef = inject(DestroyRef);

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
  /** Carga del plano (ver CSS .plano-*): la imagen ya llegó; la pantalla de carga sale y el plano se revela. */
  readonly imagenLista = signal(false);
  readonly cargaTerminada = signal(false);
  readonly revelado = signal(false);
  readonly mostrarCarga = computed(
    () => this.isLoading() || (!this.errorMessage() && !!this.planoUrl() && !this.cargaTerminada()),
  );
  readonly errorMessage = signal<string | null>(null);

  /** El plano/nombre del desarrollo vienen anidados en cualquiera de sus lotes (todos comparten el
   * mismo desarrollo) — no hace falta un endpoint aparte. */
  readonly planoUrl = computed(() => this.lotes()[0]?.desarrollo.planoUrl ?? null);
  readonly desarrolloNombre = computed(() => this.lotes()[0]?.desarrollo.nombre ?? '');

  readonly loteActivo = signal<Lote | null>(null);

  /** Lotes ya delimitados con su "points" de SVG y su tooltip calculados una sola vez (cuando cambia
   * la lista), no en cada ciclo de detección de cambios: con cientos de lotes, armar esos strings en
   * cada movimiento del mouse o del dedo era lo que más trabajo de JavaScript metía. */
  readonly poligonos = computed(() =>
    this.lotes()
      .filter((l) => this.tienePoligono(l))
      .map((lote) => ({
        lote,
        puntos: this.puntosSvg(lote.mapaPoligono!),
        titulo: `MZ ${lote.manzana} - Lote ${lote.numeroLote}`,
      })),
  );

  /** true solo durante un gesto (arrastre, pellizco, rueda): el navegador promueve el plano a su propia
   * capa de GPU para moverlo sin repintar. Se quita al terminar para que vuelva a rasterizar nítido
   * al zoom final (con will-change permanente quedaría borroso al acercar). */
  readonly enGesto = signal(false);
  private temporizadorGesto: ReturnType<typeof setTimeout> | undefined;

  /** Zoom con el que se calcula el grosor de los bordes (ver CONGELAR_TRAZO_EN_GESTO): sigue a
   * zoom() salvo durante un gesto, donde se queda en el último valor. */
  readonly zoomTrazo = signal(1);
  private readonly sincronizarTrazo = effect(() => {
    const zoom = this.zoom();
    if (!CONGELAR_TRAZO_EN_GESTO || !this.enGesto()) this.zoomTrazo.set(zoom);
  });

  // Modal "Apartar lote": mismos campos y restricciones que /cotizador-publico/lotes — pide nombre
  // de asesor y de cliente (nota opcional) porque no hay una sesión real detrás de este apartado.
  readonly mostrarApartar = signal(false);

  // Candado: sin un asesor verificado no se muestran los botones de cotizar ni apartar. Se recuerda
  // solo mientras dure la pestaña (sessionStorage), nunca de forma permanente.
  readonly asesorVerificado = signal<string | null>(leerAsesorGuardado(this.proyecto));
  readonly mostrarAcceso = signal(false);
  /** PIN del botón Asesor: un carácter por casilla. */
  readonly pinCasillas = signal<string[]>(['', '', '', '']);
  readonly accesoNombre = computed(() => this.pinCasillas().join(''));
  readonly accesoError = signal<string | null>(null);
  readonly isVerificando = signal(false);
  readonly nombreAsesorInput = signal('');
  readonly nombreClienteInput = signal('');
  readonly notaInput = signal('');
  readonly isApartando = signal(false);
  readonly notaMaxLength = NOTA_MAX_LENGTH;
  readonly nombreAsesorInvalido = computed(() => this.nombreAsesorInput().trim().length === 0);
  readonly nombreClienteInvalido = computed(() => this.nombreClienteInput().trim().length === 0);

  /** Zoom y desplazamiento del plano — mismo mecanismo que /panel/plano, sin nada de edición, pero
   * con un piso de zoom dinámico (ver zoomAjuste) en vez de 1: el plano arranca completo dentro de
   * la pantalla (como "object-fit: contain"), con franjas negras donde sobre espacio. Zoom 1 = el
   * ancho de la imagen ocupa el ancho de la pantalla, así que en pantallas más altas que el plano
   * el ajuste es 1 (franjas arriba y abajo) y en pantallas más angostas es menor a 1 (franjas a
   * los lados). */
  readonly zoom = signal(1);
  readonly zoomMax = ZOOM_MAX;
  readonly zoomAjuste = signal(ZOOM_MIN);
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

  /** Leyenda de estados plegable (ver alternarLeyenda): en pantallas angostas arranca plegada, ahí
   * tapa demasiado del plano. */
  readonly leyendaVisible = signal(typeof window === 'undefined' || window.innerWidth >= 640);

  /** Aviso "gira tu teléfono": celular (puntero táctil) en vertical, y solo una vez por visita. */
  readonly mostrarAvisoRotar = signal(false);

  constructor() {
    // Fondo negro en toda la página y barra del navegador negra: ver core/utils/pagina.ts.
    fondoDePagina(this.destroyRef, 'fondo-negro');
    colorDeBarra(this.destroyRef, '#000000');
    this.escucharVisualViewport();
    this.cargar();
    this.iniciarAvisoRotar();
  }

  /** En Android la barra de URL aparece y desaparece cambiando el área visible sin que siempre se
   * dispare el resize de la ventana: visualViewport sí avisa, y así el plano se vuelve a ajustar. */
  private escucharVisualViewport(): void {
    const visual = window.visualViewport;
    if (!visual) return;
    const alCambiar = () => this.ajustarZoomParaVerCompleto();
    visual.addEventListener('resize', alCambiar);
    this.destroyRef.onDestroy(() => visual.removeEventListener('resize', alCambiar));
  }

  alternarLeyenda(): void {
    this.leyendaVisible.update((v) => !v);
  }

  cerrarAvisoRotar(): void {
    this.mostrarAvisoRotar.set(false);
    try {
      sessionStorage.setItem(CLAVE_AVISO_ROTAR, '1');
    } catch {
      // Sin sessionStorage (modo privado estricto) simplemente puede volver a aparecer.
    }
  }

  private iniciarAvisoRotar(): void {
    if (typeof window === 'undefined') return;
    try {
      if (sessionStorage.getItem(CLAVE_AVISO_ROTAR)) return;
    } catch {
      // Se ignora: se muestra igual.
    }
    const esCelular = window.matchMedia('(pointer: coarse)').matches && Math.min(window.innerWidth, window.innerHeight) < 600;
    if (!esCelular) return;

    const vertical = window.matchMedia('(orientation: portrait)');
    this.mostrarAvisoRotar.set(vertical.matches);

    let temporizador: ReturnType<typeof setTimeout> | undefined;
    const alCambiar = () => {
      clearTimeout(temporizador);
      if (!vertical.matches) {
        // Ya lo giró: el aviso cumplió, no hace falta volver a mostrarlo en esta visita.
        this.cerrarAvisoRotar();
      }
    };
    vertical.addEventListener('change', alCambiar);
    temporizador = setTimeout(() => this.mostrarAvisoRotar.set(false), DURACION_AVISO_ROTAR_MS);
    this.destroyRef.onDestroy(() => {
      clearTimeout(temporizador);
      vertical.removeEventListener('change', alCambiar);
    });
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

  /** El precio lo calcula el servidor con el precio por m² del lote (propio, o el del desarrollo). */
  precioEstimado(lote: Lote): number {
    return lote.precio;
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

  abrirAcceso(): void {
    this.pinCasillas.set(['', '', '', '']);
    this.accesoError.set(null);
    this.mostrarAcceso.set(true);
  }

  /** Escribe en una casilla (acepta pegar el PIN completo) y pasa a la siguiente. */
  escribirPin(indice: number, valor: string, evento: Event): void {
    const limpio = valor.toUpperCase().replace(/[^A-Z0-9]/g, '');
    const casillas = [...this.pinCasillas()];
    if (!limpio) {
      casillas[indice] = '';
      this.pinCasillas.set(casillas);
      (evento.target as HTMLInputElement).value = '';
      return;
    }
    for (let i = 0; i < limpio.length && indice + i < 4; i++) casillas[indice + i] = limpio[i];
    this.pinCasillas.set(casillas);
    this.accesoError.set(null);
    const host = (evento.target as HTMLInputElement).closest('[data-pin]') as HTMLElement;
    const inputs = Array.from(host.querySelectorAll('input'));
    inputs.forEach((el, i) => (el.value = casillas[i]));
    inputs[Math.min(indice + limpio.length, 3)]?.focus();
  }

  teclaPin(indice: number, evento: KeyboardEvent): void {
    const input = evento.target as HTMLInputElement;
    if (evento.key === 'Backspace' && !input.value && indice > 0) {
      const inputs = Array.from((input.closest('[data-pin]') as HTMLElement).querySelectorAll('input'));
      const casillas = [...this.pinCasillas()];
      casillas[indice - 1] = '';
      this.pinCasillas.set(casillas);
      inputs[indice - 1].value = '';
      inputs[indice - 1].focus();
      evento.preventDefault();
    } else if (evento.key === 'ArrowLeft' && indice > 0) {
      (input.previousElementSibling as HTMLInputElement | null)?.focus();
    } else if (evento.key === 'ArrowRight' && indice < 3) {
      (input.nextElementSibling as HTMLInputElement | null)?.focus();
    }
  }

  cerrarAcceso(): void {
    this.mostrarAcceso.set(false);
  }

  async confirmarAcceso(): Promise<void> {
    const nombre = this.accesoNombre().trim();
    if (!nombre || this.isVerificando()) return;
    this.isVerificando.set(true);
    this.accesoError.set(null);
    try {
      const registrado = await this.lotesService.verificarAsesorPublico(nombre, this.proyecto);
      this.asesorVerificado.set(registrado);
      guardarAsesor(this.proyecto, registrado);
      this.mostrarAcceso.set(false);
      this.toast.success(`Bienvenido, ${registrado}.`);
    } catch (e) {
      const estado = e instanceof HttpErrorResponse ? e.status : 0;
      this.accesoError.set(
        estado === 404
          ? 'PIN incorrecto.'
          : estado === 403 && typeof (e as HttpErrorResponse).error?.message === 'string'
            ? (e as HttpErrorResponse).error.message
            : estado === 429
            ? 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.'
            : 'No se pudo verificar. Intenta de nuevo.',
      );
    } finally {
      this.isVerificando.set(false);
    }
  }

  cerrarSesionAsesor(): void {
    this.asesorVerificado.set(null);
    guardarAsesor(this.proyecto, null);
    this.mostrarApartar.set(false);
  }

  abrirApartar(): void {
    this.mostrarApartar.set(true);
    this.nombreAsesorInput.set(this.asesorVerificado() ?? '');
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

  // ---- Zoom y desplazamiento (ver PlanoComponent; el piso es zoomAjuste(), la vista completa) ----

  /** Alto que tendría la imagen en pantalla a zoom 1: como el ancho siempre es el del contenedor
   * (class="w-full" en el <img>), el alto natural es ese ancho × la proporción real de la imagen. */
  private altoNaturalEnPantalla(anchoContenedor: number): number {
    if (this.naturalWidth === 0) return anchoContenedor;
    return anchoContenedor * (this.naturalHeight / this.naturalWidth);
  }

  /** Al cargar la imagen (y si la ventana cambia de tamaño): calcula el zoom que hace caber la
   * imagen COMPLETA en la pantalla, sin recortar — el equivalente de "object-fit: contain", pero
   * aplicado al mismo zoom/pan del visor en vez de a CSS puro, para que el overlay de polígonos
   * (que vive dentro del mismo div escalado) se mantenga perfectamente alineado con la imagen.
   * Deja la imagen centrada; el espacio sobrante se ve negro (ver el fondo del contenedor). */
  private ajustarZoomParaVerCompleto(): void {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor || this.naturalWidth === 0) return;
    const rect = contenedor.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    const altoAZoom1 = this.altoNaturalEnPantalla(rect.width);
    const zoomAjuste = Math.min(1, Math.max(ZOOM_MIN, rect.height / altoAZoom1));
    this.zoomAjuste.set(zoomAjuste);

    this.zoom.set(zoomAjuste);
    this.panX.set((rect.width - rect.width * zoomAjuste) / 2);
    this.panY.set((rect.height - altoAZoom1 * zoomAjuste) / 2);
    this.limitarPan(rect);
  }

  /** Se dispara cuando el <img> termina de cargar — recién ahí se conoce su tamaño real. */
  onImagenCargada(event: Event): void {
    const img = event.target as HTMLImageElement;
    this.naturalWidth = img.naturalWidth;
    this.naturalHeight = img.naturalHeight;
    this.ajustarZoomParaVerCompleto();
    this.terminarCarga(true);
  }

  /** Si la imagen no carga, igual se quita la pantalla de carga (el visor queda vacío y negro). */
  onImagenError(): void {
    this.terminarCarga(false);
  }

  private terminarCarga(conAnimacion: boolean): void {
    if (this.imagenLista()) return;
    this.imagenLista.set(true);
    setTimeout(() => this.cargaTerminada.set(true), 600);
    setTimeout(() => this.revelado.set(true), conAnimacion ? 2600 : 0);
  }

  @HostListener('window:resize')
  onResizeVentana(): void {
    this.ajustarZoomParaVerCompleto();
  }

  private zoomEn(clientX: number, clientY: number, nuevoZoom: number): void {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return;
    const rect = contenedor.getBoundingClientRect();
    const zoomActual = this.zoom();
    const zoomFinal = Math.min(ZOOM_MAX, Math.max(this.zoomAjuste(), nuevoZoom));
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
   * altoNaturalEnPantalla) — así que los límites se calculan sobre el alto real, no sobre el del
   * contenedor. En el ancho sí coinciden siempre (class="w-full"). */
  private limitarPan(rect: DOMRect): void {
    const zoom = this.zoom();
    const anchoContenido = rect.width * zoom;
    const altoContenido = this.altoNaturalEnPantalla(rect.width) * zoom;
    // En un eje donde la imagen es más chica que la pantalla no hay nada que desplazar: se centra
    // (el resto es el fondo negro). Donde es más grande, el pan se acota a sus bordes.
    this.panX.set(
      anchoContenido <= rect.width
        ? (rect.width - anchoContenido) / 2
        : Math.min(0, Math.max(rect.width - anchoContenido, this.panX())),
    );
    this.panY.set(
      altoContenido <= rect.height
        ? (rect.height - altoContenido) / 2
        : Math.min(0, Math.max(rect.height - altoContenido, this.panY())),
    );
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

  /** "Restablecer" vuelve a la vista completa del plano (centrado), que es el zoom mínimo. */
  restablecerZoom(): void {
    this.animar(() => this.ajustarZoomParaVerCompleto());
  }

  /** Proporcional a cuánto giró la rueda: una rueda de mouse (saltos grandes) se siente igual que
   * antes, y un trackpad (muchos eventos chicos) acerca de forma continua en vez de dar un salto
   * fijo por evento. ctrlKey = pellizco en trackpad (el navegador lo manda como rueda + ctrl). */
  private marcarGesto(): void {
    this.enGesto.set(true);
    clearTimeout(this.temporizadorGesto);
    this.temporizadorGesto = setTimeout(() => {
      if (this.punteros.size === 0) this.enGesto.set(false);
    }, 180);
  }

  private readonly detectorRueda = new DetectorDeRueda();

  /** Pellizco (ctrl + rueda) y rueda de mouse: zoom. Dos dedos sobre el trackpad: desplazan el plano. */
  onWheelImagen(event: WheelEvent): void {
    event.preventDefault();
    this.marcarGesto();
    if (!event.ctrlKey && !this.detectorRueda.esRuedaDeMouse(event)) {
      this.desplazarConTrackpad(event);
      return;
    }
    const sensibilidad = event.ctrlKey ? SENSIBILIDAD_PELLIZCO_TRACKPAD : SENSIBILIDAD_RUEDA;
    const factor = Math.exp(-event.deltaY * sensibilidad);
    this.zoomEn(event.clientX, event.clientY, this.zoom() * factor);
  }

  private desplazarConTrackpad(event: WheelEvent): void {
    const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
    if (!rect) return;
    this.panX.update((x) => x - event.deltaX);
    this.panY.update((y) => y - event.deltaY);
    this.limitarPan(rect);
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
      if (this.zoom() > this.zoomAjuste() * 1.05) {
        this.ajustarZoomParaVerCompleto();
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
    if (this.mostrarApartar() || this.mostrarAcceso() || this.loteActivo()) return;

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

  /** Si hay algo de la imagen fuera de la vista en cualquiera de los dos ejes (no necesariamente
   * ambos: a la vista completa la imagen cabe entera, pero al acercar puede desbordar solo en uno).
   * Solo entonces tiene sentido arrastrar para desplazar. */
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
    this.escucharPunteros();
    clearTimeout(this.temporizadorGesto);
    this.enGesto.set(true);

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

  /** Los eventos de movimiento/soltado del puntero solo se escuchan mientras hay uno apoyado en el
   * plano: como HostListener permanente, cada movimiento del mouse por la página (sin tocar nada)
   * disparaba un ciclo de detección de cambios completo — con cientos de polígonos, lo más caro. */
  private escuchandoPunteros = false;
  private readonly alMoverPuntero = (e: PointerEvent) => this.onPointerMove(e);
  private readonly alSoltarPuntero = (e: PointerEvent) => this.onPointerUp(e);

  private escucharPunteros(): void {
    if (this.escuchandoPunteros) return;
    this.escuchandoPunteros = true;
    document.addEventListener('pointermove', this.alMoverPuntero);
    document.addEventListener('pointerup', this.alSoltarPuntero);
    document.addEventListener('pointercancel', this.alSoltarPuntero);
    this.destroyRef.onDestroy(() => this.dejarDeEscucharPunteros());
  }

  private dejarDeEscucharPunteros(): void {
    if (!this.escuchandoPunteros) return;
    this.escuchandoPunteros = false;
    document.removeEventListener('pointermove', this.alMoverPuntero);
    document.removeEventListener('pointerup', this.alSoltarPuntero);
    document.removeEventListener('pointercancel', this.alSoltarPuntero);
  }

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

  onPointerUp(event: PointerEvent): void {
    const eraPellizco = this.punteros.size >= 2;
    this.punteros.delete(event.pointerId);
    this.ultimoPellizco = null;
    if (this.punteros.size === 0) {
      this.dejarDeEscucharPunteros();
      this.marcarGesto();
    }

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
