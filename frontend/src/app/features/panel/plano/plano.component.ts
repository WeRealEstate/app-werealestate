import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_COLOR_RGB,
  ESTADO_LOTE_LABELS,
  ESTADO_LOTE_POLIGONO_CLASSES,
  ESTADOS_LOTE_ADMIN_O_LIDER,
  ESTADOS_LOTE_SOLO_ADMIN,
  EstadoLote,
  Lote,
  PlanoDesarrollo,
  PuntoMapa,
} from '../../../core/models/lote.model';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { PdfService } from '../../../core/services/pdf-cotizacion.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';

/** Qué tan cerca (en % del ancho/alto de la imagen) hay que hacer clic del primer vértice para
 * cerrar el polígono que se está dibujando. */
const UMBRAL_CIERRE_PORCENTAJE = 3;

/** Límites de zoom del plano (1 = tamaño normal) y cuánto avanza cada paso de +/- o de la rueda. */
const ZOOM_MIN = 1;
const ZOOM_MAX = 5;
const ZOOM_PASO_BOTON = 1.5;
const ZOOM_PASO_RUEDA = 1.15;

/** Si el cursor se movió más que esto (en px de pantalla) entre el pointerdown y el pointerup,
 * fue un arrastre para desplazar la vista, no un clic real sobre el plano. */
const UMBRAL_ARRASTRE_VISTA_PX = 6;

/** Igual que en Lotes: un asesor solo puede moverse entre Disponible/Apartado, y solo si el lote
 * no está ya en un estado exclusivo de admin o de admin/líder. */
const ESTADOS_ASESOR: EstadoLote[] = ['DISPONIBLE', 'APARTADO'];
const ESTADOS_TODOS: EstadoLote[] = [
  'DISPONIBLE',
  'APARTADO',
  'APARTADO_A_PLAZO',
  'APARTADO_CON_DINERO',
  'EN_PROCESO_DE_FIRMA',
  'VENDIDO',
];

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
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './plano.component.html',
})
export class PlanoComponent {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly pdfService = inject(PdfService);

  @ViewChild('contenedorPlano') private readonly contenedorPlano?: ElementRef<HTMLElement>;

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly poligonoClases = ESTADO_LOTE_POLIGONO_CLASSES;
  readonly estadosLote = Object.keys(ESTADO_LOTE_LABELS) as EstadoLote[];
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly esLider = computed(() => this.auth.currentUser()?.rol === 'LIDER_AREA');

  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;

  /** Cambio de estado que un admin o líder de área está por confirmar desde el panel de detalle:
   * les pide siempre una nota (y, solo para "Apartado a plazo", también la fecha/hora de
   * vencimiento). null cuando el modal está cerrado. Un asesor nunca pasa por aquí. */
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
    return { x: Math.round(x * 100) / 100, y: Math.round(y * 100) / 100 };
  }

  private cercaDe(a: PuntoMapa, b: PuntoMapa): boolean {
    return Math.abs(a.x - b.x) <= UMBRAL_CIERRE_PORCENTAJE && Math.abs(a.y - b.y) <= UMBRAL_CIERRE_PORCENTAJE;
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
    if (this.esAdmin() || this.esLider()) return ESTADOS_TODOS;
    const bloqueado = ESTADOS_LOTE_SOLO_ADMIN.has(lote.estado) || ESTADOS_LOTE_ADMIN_O_LIDER.has(lote.estado);
    return bloqueado ? [lote.estado] : ESTADOS_ASESOR;
  }

  /** Un asesor solo se mueve entre Disponible/Apartado y el cambio se aplica directo; un admin o
   * líder de área puede mover un lote a cualquier estado, así que se le pide justificar el cambio
   * con una nota (y, solo para "Apartado a plazo", también la fecha de vencimiento) en un modal
   * antes de llamar a la API. */
  async cambiarEstado(lote: Lote, nuevoEstado: string): Promise<void> {
    if (nuevoEstado === lote.estado) return;

    if (this.esAdmin() || this.esLider()) {
      this.abrirModalCambioEstado(lote, nuevoEstado as EstadoLote);
      return;
    }

    try {
      const actualizado = await this.lotesService.cambiarEstado(lote.id, nuevoEstado);
      this.aplicarLoteActualizado(actualizado);
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
      this.aplicarLoteActualizado(actualizado);
      this.cambioEstadoPendiente.set(null);
      this.toast.success('Estado del lote actualizado.');
    } catch (error) {
      this.toast.error(this.mensajeError(error, 'No se pudo cambiar el estado del lote.'));
    } finally {
      this.guardandoCambioEstado.set(false);
    }
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
    return lote.desarrollo.precioM2 * lote.superficie;
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
      const canvas = await this.dibujarCanvasPlano(planoActual);
      await this.pdfService.downloadPlanoPdf({
        desarrolloNombre: planoActual.desarrolloNombre,
        fecha: new Date().toLocaleDateString('es-MX'),
        imagenDataUrl: canvas.toDataURL('image/jpeg', 0.85),
        anchoImagen: canvas.width,
        altoImagen: canvas.height,
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

  /** Dibuja la imagen del plano y, encima, el polígono de cada lote ya delimitado con el color de
   * su estado — el mismo <canvas> que después se convierte en imagen para el PDF. */
  private dibujarCanvasPlano(plano: PlanoDesarrollo): Promise<HTMLCanvasElement> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('Este navegador no soporta canvas 2D'));
          return;
        }

        ctx.drawImage(img, 0, 0);

        for (const lote of plano.lotes) {
          if (!this.tienePoligono(lote)) continue;
          const [r, g, b] = ESTADO_LOTE_COLOR_RGB[lote.estado];

          ctx.beginPath();
          lote.mapaPoligono!.forEach((punto, i) => {
            const x = (punto.x / 100) * canvas.width;
            const y = (punto.y / 100) * canvas.height;
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          });
          ctx.closePath();

          ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.45)`;
          ctx.fill();
          ctx.strokeStyle = `rgb(${r}, ${g}, ${b})`;
          ctx.lineWidth = Math.max(canvas.width, canvas.height) * 0.0025;
          ctx.stroke();
        }

        resolve(canvas);
      };
      img.onerror = () => reject(new Error('No se pudo cargar la imagen del plano'));
      img.src = plano.planoUrl!;
    });
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

  /** La rueda del mouse hace zoom solo del plano (hacia donde apunta el cursor), nunca de la
   * página completa: por eso el preventDefault, que bloquea el zoom nativo del navegador. */
  onWheelImagen(event: WheelEvent): void {
    event.preventDefault();
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
