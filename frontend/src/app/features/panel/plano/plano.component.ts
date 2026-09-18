import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Desarrollo } from '../../../core/models/lead.model';
import {
  ESTADO_LOTE_LABELS,
  ESTADO_LOTE_POLIGONO_CLASSES,
  EstadoLote,
  Lote,
  PlanoDesarrollo,
  PuntoMapa,
} from '../../../core/models/lote.model';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { ToastService } from '../../../core/services/toast.service';

/** Qué tan cerca (en % del ancho/alto de la imagen) hay que hacer clic del primer vértice para
 * cerrar el polígono que se está dibujando. */
const UMBRAL_CIERRE_PORCENTAJE = 3;

/** Editor + visor del plano interactivo de un desarrollo: un admin sube la imagen (el plano de
 * ventas que ya usan en marketing) y delimita cada lote dibujando su polígono real, clic por clic,
 * cerrándolo al volver a hacer clic sobre el primer vértice; a partir de ahí el color de cada
 * polígono sigue el estado real del lote, para todos los roles. No requiere coordenadas
 * geográficas ni el archivo original (CAD/KML): solo dónde cae cada lote dentro de esa imagen. */
@Component({
  selector: 'app-plano',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './plano.component.html',
})
export class PlanoComponent {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  @ViewChild('contenedorPlano') private readonly contenedorPlano?: ElementRef<HTMLElement>;

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly poligonoClases = ESTADO_LOTE_POLIGONO_CLASSES;
  readonly estadosLote = Object.keys(ESTADO_LOTE_LABELS) as EstadoLote[];
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

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

  readonly lotesSinUbicar = computed(() => this.plano()?.lotes.filter((l) => !this.tienePoligono(l)) ?? []);
  readonly totalLotes = computed(() => this.plano()?.lotes.length ?? 0);
  readonly totalUbicados = computed(() => this.totalLotes() - this.lotesSinUbicar().length);

  constructor() {
    this.leadsService.listarDesarrollos().then((desarrollos) => {
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

  private posicionRelativa(clientX: number, clientY: number): PuntoMapa {
    const contenedor = this.contenedorPlano?.nativeElement;
    if (!contenedor) return { x: 0, y: 0 };
    const rect = contenedor.getBoundingClientRect();
    const x = Math.min(100, Math.max(0, ((clientX - rect.left) / rect.width) * 100));
    const y = Math.min(100, Math.max(0, ((clientY - rect.top) / rect.height) * 100));
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

  /** true cuando el cursor ya está lo bastante cerca del primer vértice como para cerrar la
   * figura con el próximo clic; resalta ese vértice para avisarlo. */
  listoParaCerrar(): boolean {
    const cursor = this.posicionCursor();
    const puntos = this.puntosEnProgreso();
    return puntos.length >= 3 && !!cursor && this.cercaDe(cursor, puntos[0]);
  }

  /** Puntos a unir en la línea "goma elástica": los vértices ya puestos más el tramo hasta el
   * cursor, que se ajusta (snap) al primer vértice en cuanto queda lo bastante cerca, para que se
   * vea con exactitud dónde va a caer si se cierra la figura ahí mismo. */
  puntosLineaTrazando(): PuntoMapa[] {
    const puntos = this.puntosEnProgreso();
    const cursor = this.posicionCursor();
    if (!cursor) return puntos;
    const puntoPreview = this.listoParaCerrar() ? puntos[0] : cursor;
    return [...puntos, puntoPreview];
  }

  /** En modo edición, con un lote elegido que todavía no tiene polígono guardado (o se está
   * redibujando tras quitar el anterior), cada clic sobre la imagen agrega un vértice; un clic
   * cerca del primer vértice, con al menos 3 ya puestos, cierra la figura y la guarda. */
  async onClickImagen(event: MouseEvent): Promise<void> {
    if (!this.modoEdicion()) return;
    const loteId = this.loteAUbicarId();
    if (loteId === null) return;
    const lote = this.plano()?.lotes.find((l) => l.id === loteId);
    if (!lote || (this.tienePoligono(lote) && this.puntosEnProgreso().length === 0)) return;

    const punto = this.posicionRelativa(event.clientX, event.clientY);
    const puntos = this.puntosEnProgreso();

    if (puntos.length >= 3 && this.cercaDe(punto, puntos[0])) {
      await this.guardarPoligono(loteId, puntos);
      return;
    }

    this.puntosEnProgreso.set([...puntos, punto]);
  }

  private async guardarPoligono(loteId: number, puntos: PuntoMapa[]): Promise<void> {
    try {
      const actualizado = await this.lotesService.actualizarPoligonoMapa(loteId, puntos);
      this.plano.update((p) => (p ? { ...p, lotes: p.lotes.map((l) => (l.id === loteId ? actualizado : l)) } : p));
      this.puntosEnProgreso.set([]);
      this.posicionCursor.set(null);
      this.toast.success('Lote delimitado.');
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

  /** Clic sobre el polígono de un lote: en modo edición cambia cuál se está delimitando (salvo que
   * haya un dibujo a medias, para no perderlo sin querer); si no, lo selecciona para ver sus datos. */
  onClickPoligono(lote: Lote, event: MouseEvent): void {
    event.stopPropagation();
    if (this.modoEdicion()) {
      if (this.puntosEnProgreso().length > 0 && this.loteAUbicarId() !== lote.id) return;
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
    if (!this.verticeArrastrando) return;
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
  }

  @HostListener('document:pointerup')
  async onPointerUp(): Promise<void> {
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
