import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_COLOR_RGB,
  ESTADO_LOTE_LABELS,
  ESTADO_LOTE_POLIGONO_CLASSES,
  ESTADOS_DE_VENTA,
  ESTADOS_LOTE_ADMIN_O_LIDER,
  ESTADOS_LOTE_ORDEN,
  ESTADOS_LOTE_ORDEN_ADMIN,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
  PlanoDesarrollo,
  PuntoMapa,
  MAX_LOTES_APARTADO_MULTIPLE,
  estadosParaAdmin,
} from '../../../core/models/lote.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { PdfService } from '../../../core/services/pdf-cotizacion.service';
import { DetectorDeRueda } from '../../../core/utils/rueda';
import { ToastService } from '../../../core/services/toast.service';
import { ApartarVariosModalComponent } from '../../../shared/apartar-varios-modal/apartar-varios-modal.component';
import { LoteCambioEstadoModalComponent } from '../../../shared/lote-cambio-estado-modal/lote-cambio-estado-modal.component';
import { LoteInfoModalComponent } from '../../../shared/lote-info-modal/lote-info-modal.component';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';
import { leerImagenParaPdf } from '../../../core/utils/imagen-pdf';

/** Qué tan cerca (en % del ancho/alto de la imagen) hay que hacer clic del primer vértice para
 * cerrar el polígono que se está dibujando. */
const UMBRAL_CIERRE_PORCENTAJE = 3;

/** Límites de zoom del plano (1 = tamaño normal) y cuánto avanza cada paso de +/- o de la rueda.
 * ZOOM_MAX está pensado para poder llegar a la resolución nativa del PNG del plano (varios miles
 * de px de ancho: se rasteriza a partir del PDF, ver DesarrolloService) aunque el contenedor en
 * pantalla lo muestre mucho más chico a zoom 1 — más allá de eso ya no hay más detalle que mostrar,
 * solo se ampliarían los mismos píxeles. */
const ZOOM_MIN = 1;
const ZOOM_MAX = 15;
const ZOOM_PASO_BOTON = 1.5;
const ZOOM_PASO_RUEDA = 1.15;


/** Si el cursor se movió más que esto (en px de pantalla) entre el pointerdown y el pointerup,
 * fue un arrastre para desplazar la vista, no un clic real sobre el plano. */
const UMBRAL_ARRASTRE_VISTA_PX = 6;

/** Igual que en Lotes: un asesor solo puede moverse entre Disponible/Apartado, y solo si el lote
 * no está ya en un estado exclusivo de admin o de admin/líder. Admin y administración: ver estadosParaAdmin. */
const ESTADOS_ASESOR: EstadoLote[] = ['DISPONIBLE', 'APARTADO'];

/** Cuánto se espera a que el navegador conceda la pantalla completa real antes de usar el respaldo. */
const PLAZO_PANTALLA_COMPLETA_MS = 800;

/** 'SAMAI Campestre'/'Aldea Nanuu' son los únicos desarrollos que el Cotizador sabe cotizar (ver
 * PROJECTS_CONFIG); cualquier otro (p. ej. "Otro") no tiene un botón "Cotizar" en el Plano. */
const NOMBRE_DESARROLLO_A_PROYECTO: Record<string, 'samai' | 'nanuu'> = {
  'SAMAI Campestre': 'samai',
  'Aldea Nanuu': 'nanuu',
};

/** Editor + visor del plano interactivo de un desarrollo: un admin sube la imagen (el plano de
 * ventas que ya usan en marketing) y delimita cada lote dibujando su polígono real, clic por clic,
 * cerrándolo al volver a hacer clic sobre el primer vértice; a partir de ahí el color de cada
 * polígono sigue el estado real del lote, para todos los roles. No requiere coordenadas
 * geográficas ni el archivo original (CAD/KML): solo dónde cae cada lote dentro de esa imagen. */
@Component({
  selector: 'app-plano',
  standalone: true,
  imports: [WeLoaderComponent, FormsModule, DecimalPipe, DatePipe, LoteCambioEstadoModalComponent, LoteInfoModalComponent, ApartarVariosModalComponent],
  templateUrl: './plano.component.html',
})
export class PlanoComponent implements OnDestroy {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly router = inject(Router);
  private readonly pdfService = inject(PdfService);

  @ViewChild('contenedorPlano') private readonly contenedorPlano?: ElementRef<HTMLElement>;
  @ViewChild('raizPlano') private readonly raizPlano?: ElementRef<HTMLElement>;

  // --- Pantalla completa. Se pide para toda la página del plano (no solo para el mapa) porque con la
  // pantalla completa real del navegador solo se ve ese elemento: así las fichas de lote y el cambio de
  // estado, que viven dentro, siguen apareciendo. En ella se esconden el encabezado, las herramientas de
  // admin y la leyenda; queda el plano, el zoom y el botón para salir. ---
  readonly pantallaCompleta = signal(false);

  // --- Apartar varios lotes a la vez: un botón activa el "modo selección" (tocar un lote lo marca en vez de
  // abrir su ficha), solo se pueden marcar lotes Disponibles, hasta MAX_LOTES_APARTADO_MULTIPLE, y se apartan
  // todos juntos con los mismos datos (todo o nada). ---
  readonly modoSeleccion = signal(false);
  readonly seleccion = signal<number[]>([]);
  readonly mostrarApartarVarios = signal(false);
  readonly maxSeleccion = MAX_LOTES_APARTADO_MULTIPLE;
  readonly lotesSeleccionados = computed(() => {
    const lotes = this.plano()?.lotes ?? [];
    return this.seleccion()
      .map((id) => lotes.find((l) => l.id === id))
      .filter((l): l is Lote => !!l);
  });

  alternarModoSeleccion(): void {
    this.modoSeleccion.update((v) => !v);
    this.seleccion.set([]);
    this.loteActivo.set(null);
  }

  private alternarSeleccion(lote: Lote): void {
    if (this.seleccion().includes(lote.id)) {
      this.seleccion.update((ids) => ids.filter((id) => id !== lote.id));
      return;
    }
    if (lote.estado !== 'DISPONIBLE') {
      this.toast.error('Solo se pueden elegir lotes disponibles.');
      return;
    }
    if (this.seleccion().length >= MAX_LOTES_APARTADO_MULTIPLE) {
      this.toast.error(`Máximo ${MAX_LOTES_APARTADO_MULTIPLE} lotes a la vez.`);
      return;
    }
    this.seleccion.update((ids) => [...ids, lote.id]);
  }

  onApartadosVarios(lotes: Lote[]): void {
    lotes.forEach((l) => this.aplicarLoteActualizado(l));
    this.mostrarApartarVarios.set(false);
    this.modoSeleccion.set(false);
    this.seleccion.set([]);
  }
  /** Ancho/alto de la imagen del plano: en pantalla completa el contenedor se ajusta a ella para que
   * #contenedorPlano mida exactamente lo que la imagen (ver el comentario de la plantilla sobre bordes). */
  readonly relacionAspecto = signal(1.5);
  readonly anchoPantallaCompleta = computed(() => `min(100vw, calc(100dvh * ${this.relacionAspecto()}))`);
  /** true si la pantalla completa es la real del navegador; false = respaldo que solo cubre la ventana
   * (navegadores que no la permiten, como Safari en iPhone). */
  private pantallaCompletaDelNavegador = false;

  alCargarImagen(evento: Event): void {
    const img = evento.target as HTMLImageElement;
    if (img.naturalWidth > 0 && img.naturalHeight > 0) this.relacionAspecto.set(img.naturalWidth / img.naturalHeight);
  }

  async alternarPantallaCompleta(): Promise<void> {
    if (this.pantallaCompleta()) {
      await this.salirPantallaCompleta();
      return;
    }
    const raiz = this.raizPlano?.nativeElement;
    if (!raiz) return;
    this.restablecerZoom();
    if (document.fullscreenEnabled && raiz.requestFullscreen) {
      // Hay navegadores incrustados (visores dentro de otras apps) donde la petición ni se concede ni se
      // rechaza: sin el plazo, el botón parecería muerto. Si pasa, se usa el respaldo.
      const resultado = await Promise.race([
        raiz.requestFullscreen().then(
          () => 'ok' as const,
          () => 'rechazada' as const,
        ),
        new Promise<'plazo'>((resolver) => setTimeout(() => resolver('plazo'), PLAZO_PANTALLA_COMPLETA_MS)),
      ]);
      if (resultado === 'ok') {
        this.pantallaCompletaDelNavegador = true;
        this.pantallaCompleta.set(true);
        return;
      }
    }
    this.pantallaCompletaDelNavegador = false;
    this.pantallaCompleta.set(true);
    this.subirALaCapaSuperior(raiz);
  }

  /** En el respaldo la página es un contenedor fijo, pero la barra lateral del panel queda por encima (vive
   * fuera del contexto de apilamiento de esta página y ningún z-index lo arregla). Un popover "manual" va a
   * la capa superior del navegador, por encima de todo, sin ser pantalla completa ni cerrarse al hacer clic
   * afuera. Si el navegador no lo soporta, queda solo el contenedor fijo. */
  private respaldoEnCapaSuperior = false;

  private subirALaCapaSuperior(raiz: HTMLElement): void {
    if (typeof raiz.showPopover !== 'function') return;
    try {
      raiz.setAttribute('popover', 'manual');
      raiz.showPopover();
      this.respaldoEnCapaSuperior = true;
    } catch {
      raiz.removeAttribute('popover');
    }
  }

  private bajarDeLaCapaSuperior(): void {
    const raiz = this.raizPlano?.nativeElement;
    if (!raiz || !this.respaldoEnCapaSuperior) return;
    this.respaldoEnCapaSuperior = false;
    try {
      raiz.hidePopover();
    } catch {
      // ya estaba cerrado
    }
    raiz.removeAttribute('popover');
  }

  async salirPantallaCompleta(): Promise<void> {
    if (document.fullscreenElement) {
      try {
        await document.exitFullscreen();
      } catch {
        // ya no estaba en pantalla completa
      }
    }
    this.bajarDeLaCapaSuperior();
    this.pantallaCompletaDelNavegador = false;
    this.pantallaCompleta.set(false);
    this.restablecerZoom();
  }

  /** Con Esc (o el gesto del navegador) la pantalla completa real se cierra sola: se actualiza la vista. */
  @HostListener('document:fullscreenchange')
  alCambiarPantallaCompleta(): void {
    if (document.fullscreenElement && document.fullscreenElement === this.raizPlano?.nativeElement) {
      // La pantalla completa real llegó (incluso tarde, ya con el respaldo puesto).
      this.bajarDeLaCapaSuperior();
      this.pantallaCompletaDelNavegador = true;
      this.pantallaCompleta.set(true);
    } else if (this.pantallaCompletaDelNavegador && !document.fullscreenElement) {
      this.pantallaCompletaDelNavegador = false;
      this.pantallaCompleta.set(false);
      this.restablecerZoom();
    }
  }

  ngOnDestroy(): void {
    this.bajarDeLaCapaSuperior();
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined);
  }

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly estadosDeVenta = ESTADOS_DE_VENTA;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly poligonoClases = ESTADO_LOTE_POLIGONO_CLASSES;
  /** Leyenda y PDF: los 6 estados de siempre; admin y administración ven además "Reservado por We". */
  get estadosLote(): EstadoLote[] {
    return this.esAdmin() || this.esLider() ? ESTADOS_LOTE_ORDEN_ADMIN : ESTADOS_LOTE_ORDEN;
  }
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly esLider = computed(() => this.auth.currentUser()?.rol === 'LIDER_AREA');

  /** Mismo permiso que el módulo de ventas (roleGuard ADMIN/LIDER_AREA): solo ellos pueden ver la
   * información de un lote comprometido (apartado, en firma o vendido), igual que en /panel/lotes. */
  readonly puedeVerInfoLote = computed(() => this.esAdmin() || this.esLider());

  /** Lote sobre el que se pidió "ver información" (ver LoteInfoModalComponent); null cierra el modal. */
  readonly loteInfo = signal<Lote | null>(null);

  /** Cambio de estado que un admin o líder de área está por confirmar (ver
   * LoteCambioEstadoModalComponent); null cuando el modal está cerrado. Un asesor nunca pasa por
   * aquí. */
  readonly cambioEstadoPendiente = signal<{ lote: Lote; nuevoEstado: EstadoLote; selector?: NgModel } | null>(null);

  readonly generandoPdf = signal(false);

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly desarrolloId = signal<number | null>(null);
  readonly plano = signal<PlanoDesarrollo | null>(null);
  readonly isLoading = signal(false);
  readonly subiendoPlano = signal(false);

  readonly modoEdicion = signal(false);
  readonly loteAUbicarId = signal<number | null>(null);
  readonly loteActivo = signal<Lote | null>(null);

  /** Vértices puestos hasta ahora del polígono nuevo que se está dibujando para el lote elegido;
   * vacío cuando ese lote ya tiene un polígono guardado (se corrige arrastrando sus vértices, ver
   * iniciarArrastreVertice) o cuando todavía no se ha puesto ningún vértice. */
  readonly puntosEnProgreso = signal<PuntoMapa[]>([]);

  /** Posición actual del cursor sobre el plano (mientras se dibuja); alimenta la línea de vista
   * previa entre el último vértice puesto y el próximo clic. null fuera de modo edición, sin
   * dibujo en curso, o cuando el cursor sale de la imagen. */
  readonly posicionCursor = signal<PuntoMapa | null>(null);

  /** Lote cuyo vértice se está arrastrando ahora mismo, para resaltarlo mientras se corrige. */
  readonly loteIdArrastrando = signal<number | null>(null);
  private verticeArrastrando: { loteId: number; indice: number } | null = null;

  /** Zoom y desplazamiento del plano (independiente del zoom de la página): 1 = tamaño normal,
   * pan en px sobre el contenedor sin escalar. Se aplican como transform al div interno que
   * envuelve la imagen y sus overlays, así que las posiciones en % de los vértices no cambian. */
  readonly zoom = signal(1);
  readonly zoomMax = ZOOM_MAX;
  readonly panX = signal(0);
  readonly panY = signal(0);
  private arrastreVista: { inicioX: number; inicioY: number; panXInicial: number; panYInicial: number; movioSuficiente: boolean } | null = null;
  /** true si el gesto que se acaba de soltar fue un arrastre para desplazar la vista (no un clic
   * real); onClickImagen/onClickPoligono lo consumen para no procesar el clic que sigue. */
  private ultimoGestoFuePan = false;

  readonly lotesSinUbicar = computed(() => this.plano()?.lotes.filter((l) => !this.tienePoligono(l)) ?? []);
  readonly totalLotes = computed(() => this.plano()?.lotes.length ?? 0);
  readonly totalUbicados = computed(() => this.totalLotes() - this.lotesSinUbicar().length);

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((desarrollos) => {
      this.desarrollos.set(desarrollos);
      if (desarrollos.length > 0) {
        this.desarrolloId.set(desarrollos[0].id);
        this.cargarMapa();
      }
    });
  }

  tienePoligono(lote: Lote): boolean {
    return !!lote.mapaPoligono && lote.mapaPoligono.length >= 3;
  }

  /** Convierte los vértices en el atributo "points" que espera <polygon>/<polyline>. */
  puntosSvg(puntos: PuntoMapa[]): string {
    return puntos.map((p) => `${p.x},${p.y}`).join(' ');
  }

  onDesarrolloChange(valor: string): void {
    this.desarrolloId.set(valor === '' ? null : +valor);
    this.modoSeleccion.set(false);
    this.seleccion.set([]);
    this.loteActivo.set(null);
    this.loteAUbicarId.set(null);
    this.puntosEnProgreso.set([]);
    this.posicionCursor.set(null);
    this.restablecerZoom();
    this.cargarMapa();
  }

  async cargarMapa(): Promise<void> {
    const id = this.desarrolloId();
    if (id === null) return;
    this.isLoading.set(true);
    try {
      const plano = await this.lotesService.obtenerMapa(id);
      this.plano.set(plano);
    } catch {
      this.toast.error('No se pudo cargar el plano de este desarrollo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  toggleModoEdicion(): void {
    this.modoEdicion.update((v) => !v);
    this.loteAUbicarId.set(null);
    this.loteActivo.set(null);
    this.puntosEnProgreso.set([]);
    this.posicionCursor.set(null);
  }

  async onArchivoPlanoSeleccionado(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    const id = this.desarrolloId();
    if (!archivo || id === null) return;

    this.subiendoPlano.set(true);
    try {
      await this.leadsService.subirPlano(id, archivo);
      await this.cargarMapa();
      this.toast.success('Plano actualizado.');
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo subir el plano. Intenta de nuevo.',
      );
    } finally {
      this.subiendoPlano.set(false);
      input.value = '';
    }
  }

  onSeleccionarLoteAUbicar(valor: string): void {
    this.loteAUbicarId.set(valor === '' ? null : +valor);
    this.puntosEnProgreso.set([]);
    this.posicionCursor.set(null);
  }

  /** Convierte una posición de pantalla (clientX/Y) al % dentro de la imagen sin escalar, deshaciendo
   * el pan/zoom actual del visor: el div interno se desplaza panX/panY y se escala zoom, así que el
   * punto de contenido bajo el cursor es ((posición en el contenedor) - pan) / zoom. */
  private posicionRelativa(clientX: number, clientY: number): PuntoMapa {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return { x: 0, y: 0 };
    const rect = contenedor.getBoundingClientRect();
    const zoom = this.zoom();
    const innerX = (clientX - rect.left - this.panX()) / zoom;
    const innerY = (clientY - rect.top - this.panY()) / zoom;
    const x = Math.min(100, Math.max(0, (innerX / rect.width) * 100));
    const y = Math.min(100, Math.max(0, (innerY / rect.height) * 100));
    // 4 decimales: a 2 (lo que había antes) cada paso de redondeo ya se sentía como un salto/imán
    // en vez de seguir el cursor libremente en cuanto el zoom pasaba de unos cientos por ciento.
    return { x: Math.round(x * 10000) / 10000, y: Math.round(y * 10000) / 10000 };
  }

  /** UMBRAL_CIERRE_PORCENTAJE está en % de la imagen sin escalar; se divide entre el zoom actual
   * para que la tolerancia se mantenga constante EN PANTALLA sin importar cuánto se haya acercado —
   * si no, a zoom alto ese mismo % equivale a muchos más píxeles reales, y un clic para el cuarto
   * vértice cae "cerca" del primero sin querer, cerrando la figura antes de tiempo. */
  private cercaDe(a: PuntoMapa, b: PuntoMapa): boolean {
    const umbral = UMBRAL_CIERRE_PORCENTAJE / this.zoom();
    return Math.abs(a.x - b.x) <= umbral && Math.abs(a.y - b.y) <= umbral;
  }

  /** Sigue el cursor mientras se dibuja, para la línea de vista previa del próximo tramo. */
  onMouseMoveImagen(event: MouseEvent): void {
    if (!this.modoEdicion() || this.puntosEnProgreso().length === 0) {
      this.posicionCursor.set(null);
      return;
    }
    this.posicionCursor.set(this.posicionRelativa(event.clientX, event.clientY));
  }

  onMouseLeaveImagen(): void {
    this.posicionCursor.set(null);
  }

  /** Puntos a unir en la línea de vista previa: los vértices ya puestos más el tramo recto hasta
   * la posición real del cursor (sin ajustarla al primer vértice ni resaltar nada, para no
   * insinuar un cierre automático que no ocurre: cerrar la figura sigue siendo un clic aparte). */
  puntosLineaTrazando(): PuntoMapa[] {
    const puntos = this.puntosEnProgreso();
    const cursor = this.posicionCursor();
    return cursor ? [...puntos, cursor] : puntos;
  }

  /** En modo edición, con un lote elegido que todavía no tiene polígono guardado (o se está
   * redibujando tras quitar el anterior), cada clic sobre la imagen agrega un vértice; un clic
   * cerca del primer vértice, con al menos 3 ya puestos, cierra la figura y la guarda. Si el lote
   * ya tiene un polígono, un clic sobre el fondo (fuera de cualquier figura) le agrega un vértice
   * de más en el borde más cercano — para lotes que necesitan más esquinas de las que ya tiene. */
  async onClickImagen(event: MouseEvent): Promise<void> {
    if (this.ultimoGestoFuePan) {
      this.ultimoGestoFuePan = false;
      return;
    }
    if (!this.modoEdicion()) return;
    const loteId = this.loteAUbicarId();
    if (loteId === null) return;
    const lote = this.plano()?.lotes.find((l) => l.id === loteId);
    if (!lote) return;

    const punto = this.posicionRelativa(event.clientX, event.clientY);

    if (this.tienePoligono(lote) && this.puntosEnProgreso().length === 0) {
      await this.agregarVerticeEnBorde(loteId, lote.mapaPoligono!, punto);
      return;
    }

    const puntos = this.puntosEnProgreso();

    if (puntos.length >= 3 && this.cercaDe(punto, puntos[0])) {
      await this.guardarPoligono(loteId, puntos);
      return;
    }

    this.puntosEnProgreso.set([...puntos, punto]);
  }

  /** Inserta un vértice nuevo justo después del borde más cercano al punto dado, para poder
   * agregarle más esquinas a un polígono ya guardado sin borrarlo y volver a dibujarlo entero. */
  private async agregarVerticeEnBorde(loteId: number, puntos: PuntoMapa[], nuevo: PuntoMapa): Promise<void> {
    let mejorIndice = 0;
    let mejorDistancia = Infinity;
    for (let i = 0; i < puntos.length; i++) {
      const distancia = this.distanciaPuntoSegmento(nuevo, puntos[i], puntos[(i + 1) % puntos.length]);
      if (distancia < mejorDistancia) {
        mejorDistancia = distancia;
        mejorIndice = i;
      }
    }
    const actualizados = [...puntos];
    actualizados.splice(mejorIndice + 1, 0, nuevo);
    await this.guardarPoligono(loteId, actualizados, 'Vértice agregado.');
  }

  private distanciaPuntoSegmento(p: PuntoMapa, a: PuntoMapa, b: PuntoMapa): number {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const largoCuadrado = dx * dx + dy * dy;
    if (largoCuadrado === 0) return Math.hypot(p.x - a.x, p.y - a.y);
    const t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / largoCuadrado));
    return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
  }

  private async guardarPoligono(loteId: number, puntos: PuntoMapa[], mensajeExito = 'Lote delimitado.'): Promise<void> {
    try {
      const actualizado = await this.lotesService.actualizarPoligonoMapa(loteId, puntos);
      this.plano.update((p) => (p ? { ...p, lotes: p.lotes.map((l) => (l.id === loteId ? actualizado : l)) } : p));
      this.puntosEnProgreso.set([]);
      this.posicionCursor.set(null);
      this.toast.success(mensajeExito);
    } catch {
      this.toast.error('No se pudo guardar la delimitación del lote.');
    }
  }

  /** Quita el último vértice puesto mientras se dibuja (botón "Deshacer" o tecla Escape). */
  quitarUltimoPunto(): void {
    this.puntosEnProgreso.update((puntos) => puntos.slice(0, -1));
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    // En el respaldo (sin pantalla completa real) el navegador no cierra nada con Esc: se cierra aquí.
    if (this.pantallaCompleta() && !this.pantallaCompletaDelNavegador) {
      this.bajarDeLaCapaSuperior();
      this.pantallaCompleta.set(false);
      this.restablecerZoom();
      return;
    }
    if (this.modoEdicion() && this.puntosEnProgreso().length > 0) {
      this.quitarUltimoPunto();
    }
  }

  /** Enter avanza al siguiente lote sin delimitar (o al primero, si no hay ninguno elegido
   * todavía), saltándose los que ya tienen polígono. Se ignora si hay un dibujo a medias (para no
   * perderlo sin querer) o si el foco está en el <select>. */
  @HostListener('document:keydown.enter', ['$event'])
  onEnterKey(event: Event): void {
    if (!this.modoEdicion() || document.activeElement instanceof HTMLSelectElement) return;
    if (this.puntosEnProgreso().length > 0) return;
    event.preventDefault();
    this.avanzarSiguienteLote();
  }

  /** Botón "Siguiente lote" en la plantilla: mismo salto que Enter, para quien prefiera el mouse. */
  avanzarSiguienteLote(): void {
    const lotes = this.plano()?.lotes ?? [];
    if (lotes.length === 0) return;

    const actualId = this.loteAUbicarId();
    const indiceActual = actualId === null ? -1 : lotes.findIndex((l) => l.id === actualId);
    for (let i = 1; i <= lotes.length; i++) {
      const candidato = lotes[(indiceActual + i) % lotes.length];
      if (!this.tienePoligono(candidato)) {
        this.onSeleccionarLoteAUbicar(String(candidato.id));
        return;
      }
    }
    this.onSeleccionarLoteAUbicar('');
    this.toast.success('Todos los lotes están delimitados.');
  }

  /** Clic sobre el polígono de un lote. En modo edición: si es el lote que ya se está corrigiendo,
   * le agrega un vértice de más ahí mismo (en vez de solo re-seleccionarlo); si es otro lote,
   * cambia cuál se está delimitando (salvo que haya un dibujo a medias, para no perderlo sin
   * querer). Fuera de modo edición, lo selecciona para ver sus datos. */
  onClickPoligono(lote: Lote, event: MouseEvent): void {
    event.stopPropagation();
    if (this.ultimoGestoFuePan) {
      this.ultimoGestoFuePan = false;
      return;
    }
    if (this.modoSeleccion() && !this.modoEdicion()) {
      this.alternarSeleccion(lote);
      return;
    }
    if (this.modoEdicion()) {
      if (this.puntosEnProgreso().length > 0 && this.loteAUbicarId() !== lote.id) return;
      if (this.loteAUbicarId() === lote.id && this.tienePoligono(lote)) {
        const punto = this.posicionRelativa(event.clientX, event.clientY);
        void this.agregarVerticeEnBorde(lote.id, lote.mapaPoligono!, punto);
        return;
      }
      this.onSeleccionarLoteAUbicar(String(lote.id));
    } else {
      this.loteActivo.set(this.loteActivo()?.id === lote.id ? null : lote);
    }
  }

  // ---- Quitar la delimitación de todos los lotes (solo admin, con contraseña) ----

  readonly confirmandoLimpiarTodo = signal(false);
  readonly passwordLimpiar = signal('');
  readonly errorLimpiar = signal<string | null>(null);
  readonly limpiandoTodo = signal(false);

  abrirLimpiarTodo(): void {
    this.passwordLimpiar.set('');
    this.errorLimpiar.set(null);
    this.confirmandoLimpiarTodo.set(true);
  }

  cerrarLimpiarTodo(): void {
    if (this.limpiandoTodo()) return;
    this.confirmandoLimpiarTodo.set(false);
    this.passwordLimpiar.set('');
  }

  async confirmarLimpiarTodo(): Promise<void> {
    const id = this.desarrolloId();
    const password = this.passwordLimpiar();
    if (id === null || !password || this.limpiandoTodo()) return;

    this.limpiandoTodo.set(true);
    this.errorLimpiar.set(null);
    try {
      const { limpiados } = await this.lotesService.limpiarMapa(id, password);
      this.confirmandoLimpiarTodo.set(false);
      this.passwordLimpiar.set('');
      this.loteActivo.set(null);
      this.loteAUbicarId.set(null);
      this.puntosEnProgreso.set([]);
      await this.cargarMapa();
      this.toast.success(`Se quitó la delimitación de ${limpiados} lote${limpiados === 1 ? '' : 's'}.`);
    } catch (error) {
      this.errorLimpiar.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo quitar la delimitación. Intenta de nuevo.',
      );
    } finally {
      this.limpiandoTodo.set(false);
    }
  }

  async quitarDelimitacion(lote: Lote): Promise<void> {
    try {
      const actualizado = await this.lotesService.actualizarPoligonoMapa(lote.id, null);
      this.plano.update((p) => (p ? { ...p, lotes: p.lotes.map((l) => (l.id === lote.id ? actualizado : l)) } : p));
      this.loteActivo.set(null);
      if (this.loteAUbicarId() === lote.id) {
        this.puntosEnProgreso.set([]);
      }
    } catch {
      this.toast.error('No se pudo quitar la delimitación de este lote.');
    }
  }

  // ---- Cambiar el estado de un lote desde el panel de detalle ----

  /** Un admin y un líder de área tienen control total sobre el estado de cualquier lote; un
   * asesor solo se mueve entre Disponible/Apartado, y ni siquiera puede sacar un lote ya
   * comprometido (apartado a plazo, con dinero, en firma o vendido) de ese estado. */
  estadosDisponiblesPara(lote: Lote): EstadoLote[] {
    if (this.esAdmin() || this.esLider()) return estadosParaAdmin(lote);
    const bloqueado = ESTADOS_LOTE_SOLO_ADMIN.has(lote.estado) || ESTADOS_LOTE_ADMIN_O_LIDER.has(lote.estado);
    return bloqueado ? [lote.estado] : ESTADOS_ASESOR;
  }

  /** Un asesor solo se mueve entre Disponible/Apartado y el cambio se aplica directo; un admin o
   * líder de área puede mover un lote a cualquier estado, así que se le pide justificar el cambio
   * con una nota (y, solo para "Apartado a plazo", también la fecha de vencimiento) en un modal
   * antes de llamar a la API; al cancelar, el selector regresa al estado real. Vendido y En proceso de
   * firma los pone la venta: para un lote sin venta, elegirlos lleva a registrarla. */
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
      this.aplicarLoteActualizado(actualizado);
    } catch (error) {
      this.toast.error(this.mensajeError(error, 'No se pudo cambiar el estado del lote.'));
    }
  }

  /** Vendido y En proceso de firma solo los pone una venta: se confirma y se abre Nueva venta con el desarrollo y el
   * lote ya elegidos; el lote cambia al guardarla (según el contrato). El aviso vive fuera de esta página, así que
   * antes se sale de la pantalla completa. */
  private async irARegistrarVenta(lote: Lote, selector?: NgModel): Promise<void> {
    if (this.pantallaCompleta()) await this.salirPantallaCompleta();
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

  /** La venta del lote (solo admin y administración reciben su número). */
  verVenta(lote: Lote): void {
    if (lote.ventaNumero) void this.router.navigate(['/panel/ventas', lote.ventaNumero]);
  }

  cancelarCambioEstado(): void {
    const pendiente = this.cambioEstadoPendiente();
    if (pendiente) this.regresarSelector(pendiente.lote, pendiente.selector);
    this.cambioEstadoPendiente.set(null);
  }

  onCambioEstadoConfirmado(actualizado: Lote): void {
    this.aplicarLoteActualizado(actualizado);
    this.cambioEstadoPendiente.set(null);
  }

  verInfoLote(lote: Lote): void {
    this.loteInfo.set(lote);
  }

  cerrarInfoLote(): void {
    this.loteInfo.set(null);
  }

  /** Refleja el lote actualizado tanto en la lista del plano como en el panel de detalle abierto. */
  private aplicarLoteActualizado(actualizado: Lote): void {
    this.plano.update((p) => (p ? { ...p, lotes: p.lotes.map((l) => (l.id === actualizado.id ? actualizado : l)) } : p));
    if (this.loteActivo()?.id === actualizado.id) {
      this.loteActivo.set(actualizado);
    }
  }

  private mensajeError(error: unknown, porDefecto: string): string {
    return error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
      ? error.error.message
      : porDefecto;
  }

  /** El precio ya no se captura a mano: siempre es el precio por m² del desarrollo × la superficie. */
  precioEstimado(lote: Lote): number {
    return lote.precio;
  }

  /** Proyecto que entiende el Cotizador para este lote; null si su desarrollo no es SAMAI ni Nanuu. */
  proyectoCotizador(lote: Lote): 'samai' | 'nanuu' | null {
    return NOMBRE_DESARROLLO_A_PROYECTO[lote.desarrollo.nombre] ?? null;
  }

  /** Botón "Cotizar este lote": abre el Cotizador con el proyecto, manzana y lote ya precargados. */
  cotizarLote(lote: Lote): void {
    const proyecto = this.proyectoCotizador(lote);
    if (!proyecto) return;
    void this.router.navigate(['/panel/cotizador'], {
      queryParams: { proyecto, manzana: lote.manzana, lote: lote.numeroLote },
    });
  }

  // ---- Descargar el plano completo como PDF, coloreado por estado ----

  async descargarPdf(): Promise<void> {
    const planoActual = this.plano();
    if (!planoActual?.planoUrl) return;

    this.generandoPdf.set(true);
    try {
      const imagen = await leerImagenParaPdf(planoActual.planoUrl);
      await this.pdfService.downloadPlanoPdf({
        desarrolloNombre: planoActual.desarrolloNombre,
        fecha: new Date().toLocaleDateString('es-MX'),
        imagenBytes: imagen.bytes,
        anchoImagen: imagen.ancho,
        altoImagen: imagen.alto,
        poligonos: planoActual.lotes
          .filter((lote) => this.tienePoligono(lote))
          .map((lote) => ({ puntos: lote.mapaPoligono!, color: ESTADO_LOTE_COLOR_RGB[lote.estado] })),
        leyenda: this.estadosLote.map((estado) => ({
          label: this.estadoLabels[estado],
          color: ESTADO_LOTE_COLOR_RGB[estado],
        })),
      });
    } catch {
      this.toast.error('No se pudo generar el PDF del plano.');
    } finally {
      this.generandoPdf.set(false);
    }
  }

  // ---- Zoom y desplazamiento del plano (aparte del zoom del navegador, que no lo toca) ----

  /** Acerca/aleja manteniendo fijo, bajo ese punto de pantalla, el mismo punto de la imagen — así
   * la vista no "salta" al hacer zoom con la rueda sobre un lote en particular. */
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

  /** Evita que, al alejar el zoom o soltar el arrastre, la imagen quede desplazada fuera de vista. */
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

  private readonly detectorRueda = new DetectorDeRueda();

  /** La rueda del mouse (y el pellizco del trackpad) hacen zoom solo del plano (hacia donde apunta
   * el cursor), nunca de la página completa: por eso el preventDefault, que bloquea el zoom nativo
   * del navegador. Dos dedos sobre el trackpad, en cambio, desplazan el plano. */
  onWheelImagen(event: WheelEvent): void {
    event.preventDefault();
    if (!event.ctrlKey && !this.detectorRueda.esRuedaDeMouse(event)) {
      const rect = this.contenedorPlano?.nativeElement.getBoundingClientRect();
      if (!rect) return;
      this.panX.update((x) => x - event.deltaX);
      this.panY.update((y) => y - event.deltaY);
      this.limitarPan(rect);
      return;
    }
    const factor = event.deltaY < 0 ? ZOOM_PASO_RUEDA : 1 / ZOOM_PASO_RUEDA;
    this.zoomEn(event.clientX, event.clientY, this.zoom() * factor);
  }

  /** Con zoom aplicado, arrastrar desplaza la vista en vez de hacer clic; si el mouse no se mueve
   * lo suficiente antes de soltar, se procesa como un clic normal (ver onPointerUp). */
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

  // ---- Arrastrar vértices de un polígono ya guardado, para corregirlo ----

  iniciarArrastreVertice(lote: Lote, indice: number, event: PointerEvent): void {
    if (!this.modoEdicion() || this.loteAUbicarId() !== lote.id) return;
    event.stopPropagation();
    event.preventDefault();
    this.verticeArrastrando = { loteId: lote.id, indice };
    this.loteIdArrastrando.set(lote.id);
  }

  @HostListener('document:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (this.verticeArrastrando) {
      const { loteId, indice } = this.verticeArrastrando;
      const punto = this.posicionRelativa(event.clientX, event.clientY);
      this.plano.update((p) => {
        if (!p) return p;
        return {
          ...p,
          lotes: p.lotes.map((l) => {
            if (l.id !== loteId || !l.mapaPoligono) return l;
            return { ...l, mapaPoligono: l.mapaPoligono.map((pt, i) => (i === indice ? punto : pt)) };
          }),
        };
      });
      return;
    }

    if (this.arrastreVista) {
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
  }

  @HostListener('document:pointerup')
  async onPointerUp(): Promise<void> {
    if (this.arrastreVista) {
      this.ultimoGestoFuePan = this.arrastreVista.movioSuficiente;
      this.arrastreVista = null;
      return;
    }

    if (!this.verticeArrastrando) return;
    const { loteId } = this.verticeArrastrando;
    this.verticeArrastrando = null;
    this.loteIdArrastrando.set(null);
    const lote = this.plano()?.lotes.find((l) => l.id === loteId);
    if (!lote?.mapaPoligono) return;
    try {
      await this.lotesService.actualizarPoligonoMapa(loteId, lote.mapaPoligono);
    } catch {
      this.toast.error('No se pudo guardar la corrección del vértice.');
      await this.cargarMapa();
    }
  }
}
