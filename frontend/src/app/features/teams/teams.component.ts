import { Component, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../core/services/toast.service';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { AsesorExterno, TipoAsesorExterno } from '../../core/models/asesor-externo.model';
import { WeLoaderComponent } from '../../shared/we-loader/we-loader.component';

/** 0 = líder, 1 = línea 1, 2 = línea 2, 3 = independiente. */
type Nivel = 0 | 1 | 2 | 3;

interface Punto {
  x: number;
  y: number;
}

/** Nodo del lienzo ya con su posición efectiva (la de usuario si la movió, si no la automática). */
interface NodoVista {
  a: AsesorExterno;
  nivel: Nivel;
  x: number;
  y: number;
  w: number;
  h: number;
  hijos: number;
  colapsado: boolean;
}

interface NodoLayout {
  a: AsesorExterno;
  nivel: Nivel;
  w: number;
  h: number;
  ax: number;
  ay: number;
  parentId: number | null;
  /** Ids de los hijos visibles (vacío si está colapsado). */
  hijosVisibles: number[];
  hijosTotal: number;
}

interface Arista {
  id: number;
  d: string;
}

interface ArrastreNodo {
  id: number;
  inicio: Punto; // puntero en coordenadas del mundo
  inicios: Map<number, Punto>; // posición efectiva de cada nodo que se mueve (él y su subárbol visible)
  pantallaX: number;
  pantallaY: number;
  movido: boolean;
}

const ANCHO: Record<Nivel, number> = { 0: 190, 1: 156, 2: 144, 3: 150 };
const ALTO: Record<Nivel, number> = { 0: 66, 1: 58, 2: 52, 3: 42 };
const NIVEL_Y: Record<0 | 1 | 2, number> = { 0: 0, 1: 66 + 92, 2: 66 + 92 + 58 + 92 };
const GAP_X = 28;
const GAP_EQUIPOS = 96;
const ANCHO_MAX_FILA = 1900;
const CUADRICULA = 10;
const ZOOM_MIN = 0.15;
const ZOOM_MAX = 2.5;
const UMBRAL_ARRASTRE = 4;
const CLAVE_POSICIONES = 'we-comunidades-posiciones-v1';
const CLAVE_COLAPSADOS = 'we-comunidades-colapsados-v1';

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [WeLoaderComponent, RouterLink],
  templateUrl: './teams.component.html',
})
export class TeamsComponent {
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);

  readonly viewport = viewChild<ElementRef<HTMLElement>>('viewport');

  readonly asesores = signal<AsesorExterno[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  /** Tarjeta con el detalle/acciones del asesor en el que se hizo clic; null = cerrada. */
  readonly detalleAbierto = signal<AsesorExterno | null>(null);

  /** Id del asesor que acaba de aterrizar en su nueva posición tras reasignarlo (resaltado breve). */
  readonly recienMovidoId = signal<number | null>(null);

  // --- Vista del lienzo (zoom + desplazamiento) ---
  readonly zoom = signal(1);
  readonly panX = signal(0);
  readonly panY = signal(0);
  readonly pantallaCompleta = signal(false);

  // --- Posiciones y colapsados que el usuario fijó a mano (se guardan en este navegador) ---
  readonly posiciones = signal<Record<number, Punto>>(this.leerLocal<Record<number, Punto>>(CLAVE_POSICIONES, {}));
  readonly colapsados = signal<Set<number>>(new Set(this.leerLocal<number[]>(CLAVE_COLAPSADOS, [])));

  /** Qué tiene el cursor encima mientras se arrastra una tarjeta: id de un nodo, 'ZONA' o null. */
  readonly objetivo = signal<number | 'ZONA' | null>(null);
  readonly arrastrandoId = signal<number | null>(null);
  readonly paneando = signal(false);

  private arrastre: ArrastreNodo | null = null;
  private readonly punteros = new Map<number, Punto>();
  private panInicio: { x: number; y: number; px: number; py: number } | null = null;
  private pinchDist = 0;
  private yaAjustado = false;

  // ------------------------------------------------------------------ datos y árbol

  readonly lideres = computed(() => this.asesores().filter((a) => a.tipo === 'LIDER'));
  readonly independientes = computed(() => this.asesores().filter((a) => a.tipo === 'INDEPENDIENTE'));

  private readonly porNombre = (a: AsesorExterno, b: AsesorExterno) => a.nombre.localeCompare(b.nombre);

  /** Árbol automático: posición "de fábrica" de cada nodo visible, sin tocar lo que el usuario movió. */
  private readonly layout = computed(() => {
    const todos = this.asesores();
    const colapsados = this.colapsados();
    const lideres = todos.filter((a) => a.tipo === 'LIDER').sort(this.porNombre);
    const hijosDe = new Map<number, AsesorExterno[]>();
    for (const a of todos) {
      if (a.tipo === 'LINEA' && a.liderDirectoId != null) {
        const lista = hijosDe.get(a.liderDirectoId) ?? [];
        lista.push(a);
        hijosDe.set(a.liderDirectoId, lista);
      }
    }
    for (const lista of hijosDe.values()) lista.sort(this.porNombre);

    const hijosDelNodo = (a: AsesorExterno, nivel: Nivel): AsesorExterno[] =>
      nivel >= 2 ? [] : (hijosDe.get(a.id) ?? []).filter((h) => h.nivelLinea === nivel + 1);
    const visibles = (a: AsesorExterno, nivel: Nivel): AsesorExterno[] =>
      colapsados.has(a.id) ? [] : hijosDelNodo(a, nivel);

    const ancho = (a: AsesorExterno, nivel: Nivel): number => {
      const hs = visibles(a, nivel);
      if (hs.length === 0) return ANCHO[nivel];
      const suma = hs.reduce((s, h) => s + ancho(h, (nivel + 1) as Nivel), 0) + GAP_X * (hs.length - 1);
      return Math.max(ANCHO[nivel], suma);
    };
    const profundidad = (a: AsesorExterno, nivel: Nivel): number => {
      const hs = visibles(a, nivel);
      return hs.length === 0 ? nivel : Math.max(...hs.map((h) => profundidad(h, (nivel + 1) as Nivel)));
    };

    const nodos = new Map<number, NodoLayout>();
    const colocar = (a: AsesorExterno, nivel: Nivel, izq: number, y0: number, parentId: number | null): void => {
      const total = ancho(a, nivel);
      const hs = visibles(a, nivel);
      nodos.set(a.id, {
        a,
        nivel,
        w: ANCHO[nivel],
        h: ALTO[nivel],
        ax: izq + (total - ANCHO[nivel]) / 2,
        ay: y0 + NIVEL_Y[nivel as 0 | 1 | 2],
        parentId,
        hijosVisibles: hs.map((h) => h.id),
        hijosTotal: hijosDelNodo(a, nivel).length,
      });
      const usado = hs.reduce((s, h) => s + ancho(h, (nivel + 1) as Nivel), 0) + GAP_X * Math.max(0, hs.length - 1);
      let x = izq + (total - usado) / 2;
      for (const h of hs) {
        colocar(h, (nivel + 1) as Nivel, x, y0, a.id);
        x += ancho(h, (nivel + 1) as Nivel) + GAP_X;
      }
    };

    // Equipos en filas que se parten solas cuando se pasan del ancho máximo.
    let x = 0;
    let y = 0;
    let altoFila = 0;
    let maxX = 0;
    for (const l of lideres) {
      const w = ancho(l, 0);
      if (x > 0 && x + w > ANCHO_MAX_FILA) {
        x = 0;
        y += altoFila + GAP_EQUIPOS;
        altoFila = 0;
      }
      colocar(l, 0, x, y, null);
      const nivelMax = profundidad(l, 0) as 0 | 1 | 2;
      altoFila = Math.max(altoFila, NIVEL_Y[nivelMax] + ALTO[nivelMax]);
      x += w + GAP_EQUIPOS;
      maxX = Math.max(maxX, x - GAP_EQUIPOS);
    }
    const fondoEquipos = lideres.length > 0 ? y + altoFila : 0;

    // Zona de independientes, debajo de todos los equipos.
    const indep = todos.filter((a) => a.tipo === 'INDEPENDIENTE').sort(this.porNombre);
    const anchoZona = Math.max(maxX, 760);
    const padding = 22;
    const cabecera = 38;
    const columnas = Math.max(1, Math.floor((anchoZona - padding * 2 + 14) / (ANCHO[3] + 14)));
    const filas = Math.max(1, Math.ceil(indep.length / columnas));
    const zonaY = lideres.length > 0 ? fondoEquipos + 120 : 0;
    const zona = {
      x: 0,
      y: zonaY,
      w: anchoZona,
      h: cabecera + padding + filas * (ALTO[3] + 14) - 14 + (indep.length === 0 ? 8 : 0),
    };
    indep.forEach((a, i) => {
      nodos.set(a.id, {
        a,
        nivel: 3,
        w: ANCHO[3],
        h: ALTO[3],
        ax: zona.x + padding + (i % columnas) * (ANCHO[3] + 14),
        ay: zona.y + cabecera + Math.floor(i / columnas) * (ALTO[3] + 14),
        parentId: null,
        hijosVisibles: [],
        hijosTotal: 0,
      });
    });
    return { nodos, zona };
  });

  readonly zona = computed(() => this.layout().zona);

  /** Posición efectiva: la que el usuario fijó, o la automática + lo que se movió su padre. */
  private readonly efectivas = computed(() => {
    const { nodos } = this.layout();
    const pos = this.posiciones();
    const resultado = new Map<number, NodoVista>();
    const delta = new Map<number, Punto>();
    for (const [id, n] of nodos) {
      const d = n.parentId != null ? (delta.get(n.parentId) ?? { x: 0, y: 0 }) : { x: 0, y: 0 };
      const fijada = pos[id];
      const x = fijada ? fijada.x : n.ax + d.x;
      const y = fijada ? fijada.y : n.ay + d.y;
      delta.set(id, { x: x - n.ax, y: y - n.ay });
      resultado.set(id, {
        a: n.a,
        nivel: n.nivel,
        x,
        y,
        w: n.w,
        h: n.h,
        hijos: n.hijosTotal,
        colapsado: n.hijosTotal > 0 && n.hijosVisibles.length === 0,
      });
    }
    return resultado;
  });

  readonly nodosVista = computed(() => [...this.efectivas().values()]);

  readonly aristas = computed<Arista[]>(() => {
    const { nodos } = this.layout();
    const ef = this.efectivas();
    const salida: Arista[] = [];
    for (const [id, n] of nodos) {
      if (n.parentId == null) continue;
      const p = ef.get(n.parentId);
      const c = ef.get(id);
      if (!p || !c) continue;
      const x1 = p.x + p.w / 2;
      const y1 = p.y + p.h;
      const x2 = c.x + c.w / 2;
      const y2 = c.y;
      const k = Math.max(36, Math.abs(y2 - y1) / 2);
      salida.push({ id, d: `M ${x1} ${y1} C ${x1} ${y1 + k}, ${x2} ${y2 - k}, ${x2} ${y2}` });
    }
    return salida;
  });

  readonly hayContenido = computed(() => this.asesores().length > 0);

  readonly zoomPorciento = computed(() => Math.round(this.zoom() * 100));

  readonly fondoStyle = computed(() => {
    const t = Math.max(8, 24 * this.zoom());
    return {
      'background-size': `${t}px ${t}px`,
      'background-position': `${this.panX()}px ${this.panY()}px`,
    };
  });

  readonly mundoTransform = computed(() => `translate(${this.panX()}px, ${this.panY()}px) scale(${this.zoom()})`);

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.asesores.set(await this.asesoresExternosService.listar());
      this.yaAjustado = false;
      setTimeout(() => this.ajustarPrimeraVez(), 0);
    } catch {
      this.errorMessage.set('No se pudieron cargar los equipos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
      setTimeout(() => this.ajustarPrimeraVez(), 50);
    }
  }

  private ajustarPrimeraVez(): void {
    if (this.yaAjustado || !this.viewport() || this.nodosVista().length === 0) return;
    this.yaAjustado = true;
    this.ajustar();
  }

  // ------------------------------------------------------------------ zoom y desplazamiento

  private rectViewport(): DOMRect | null {
    return this.viewport()?.nativeElement.getBoundingClientRect() ?? null;
  }

  private aMundo(clientX: number, clientY: number): Punto {
    const r = this.rectViewport();
    if (!r) return { x: 0, y: 0 };
    return { x: (clientX - r.left - this.panX()) / this.zoom(), y: (clientY - r.top - this.panY()) / this.zoom() };
  }

  /** Cambia el zoom manteniendo fijo el punto (cx, cy) dado en píxeles del lienzo. */
  private zoomEn(nuevo: number, cx: number, cy: number): void {
    const z = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, nuevo));
    const k = z / this.zoom();
    this.panX.set(cx - (cx - this.panX()) * k);
    this.panY.set(cy - (cy - this.panY()) * k);
    this.zoom.set(z);
  }

  acercar(factor: number): void {
    const r = this.rectViewport();
    if (!r) return;
    this.zoomEn(this.zoom() * factor, r.width / 2, r.height / 2);
  }

  zoomCien(): void {
    const r = this.rectViewport();
    if (!r) return;
    this.zoomEn(1, r.width / 2, r.height / 2);
  }

  /** Encuadra todo el contenido dentro del lienzo. */
  ajustar(): void {
    const r = this.rectViewport();
    const nodos = this.nodosVista();
    if (!r || nodos.length === 0) return;
    const z = this.zona();
    const hayIndep = this.independientes().length > 0;
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (const n of nodos) {
      minX = Math.min(minX, n.x);
      minY = Math.min(minY, n.y);
      maxX = Math.max(maxX, n.x + n.w);
      maxY = Math.max(maxY, n.y + n.h);
    }
    if (hayIndep) {
      minX = Math.min(minX, z.x);
      minY = Math.min(minY, z.y);
      maxX = Math.max(maxX, z.x + z.w);
      maxY = Math.max(maxY, z.y + z.h);
    }
    const margen = 40;
    const w = Math.max(1, maxX - minX);
    const h = Math.max(1, maxY - minY);
    const nuevo = Math.min(1, Math.max(ZOOM_MIN, Math.min((r.width - margen * 2) / w, (r.height - margen * 2) / h)));
    this.zoom.set(nuevo);
    this.panX.set((r.width - w * nuevo) / 2 - minX * nuevo);
    this.panY.set(Math.max(margen / 2, (r.height - h * nuevo) / 2) - minY * nuevo);
  }

  /** Borra las posiciones movidas a mano y vuelve al acomodo automático. */
  reordenar(): void {
    this.posiciones.set({});
    this.guardarPosiciones();
    setTimeout(() => this.ajustar(), 0);
  }

  alternarPantallaCompleta(): void {
    this.pantallaCompleta.update((v) => !v);
    setTimeout(() => this.ajustar(), 50);
  }

  @HostListener('document:keydown.escape')
  salirPantallaCompleta(): void {
    if (this.pantallaCompleta() && !this.detalleAbierto()) {
      this.pantallaCompleta.set(false);
      setTimeout(() => this.ajustar(), 50);
    }
  }

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    const r = this.rectViewport();
    if (!r) return;
    // Los trackpads mandan deltas chicos y seguidos; el ratón, saltos grandes. Ambos suavizados.
    const delta = event.deltaMode === 1 ? event.deltaY * 16 : event.deltaY;
    const factor = Math.exp(-Math.max(-120, Math.min(120, delta)) * (event.ctrlKey ? 0.01 : 0.0018));
    this.zoomEn(this.zoom() * factor, event.clientX - r.left, event.clientY - r.top);
  }

  // Punteros sobre el fondo: arrastrar = mover el lienzo; dos dedos = pellizcar para hacer zoom.
  onFondoDown(event: PointerEvent): void {
    if (event.button !== 0 && event.pointerType === 'mouse') return;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.punteros.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (this.punteros.size === 1) {
      this.panInicio = { x: event.clientX, y: event.clientY, px: this.panX(), py: this.panY() };
      this.paneando.set(true);
    } else if (this.punteros.size === 2) {
      this.pinchDist = this.distanciaPunteros();
      this.panInicio = null;
    }
  }

  onFondoMove(event: PointerEvent): void {
    if (this.punteros.has(event.pointerId)) this.punteros.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (this.punteros.size >= 2) {
      const d = this.distanciaPunteros();
      const r = this.rectViewport();
      if (r && this.pinchDist > 0) {
        const [a, b] = [...this.punteros.values()];
        this.zoomEn(this.zoom() * (d / this.pinchDist), (a.x + b.x) / 2 - r.left, (a.y + b.y) / 2 - r.top);
      }
      this.pinchDist = d;
    } else if (this.panInicio) {
      this.panX.set(this.panInicio.px + event.clientX - this.panInicio.x);
      this.panY.set(this.panInicio.py + event.clientY - this.panInicio.y);
    }
  }

  onFondoUp(event: PointerEvent): void {
    this.punteros.delete(event.pointerId);
    if (this.punteros.size === 1) {
      const [p] = [...this.punteros.values()];
      this.panInicio = { x: p.x, y: p.y, px: this.panX(), py: this.panY() };
    } else if (this.punteros.size === 0) {
      this.panInicio = null;
      this.paneando.set(false);
    }
  }

  private distanciaPunteros(): number {
    const [a, b] = [...this.punteros.values()];
    return a && b ? Math.hypot(a.x - b.x, a.y - b.y) : 0;
  }

  // ------------------------------------------------------------------ mover y reasignar tarjetas

  private subarbol(id: number): number[] {
    const { nodos } = this.layout();
    const ids: number[] = [];
    const visitar = (i: number) => {
      ids.push(i);
      for (const h of nodos.get(i)?.hijosVisibles ?? []) visitar(h);
    };
    visitar(id);
    return ids;
  }

  onNodoDown(event: PointerEvent, nodo: NodoVista): void {
    event.stopPropagation();
    if (event.button !== 0 && event.pointerType === 'mouse') return;
    if (this.savingId() != null) return;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    const ef = this.efectivas();
    const inicios = new Map<number, Punto>();
    for (const id of this.subarbol(nodo.a.id)) {
      const n = ef.get(id);
      if (n) inicios.set(id, { x: n.x, y: n.y });
    }
    this.arrastre = {
      id: nodo.a.id,
      inicio: this.aMundo(event.clientX, event.clientY),
      inicios,
      pantallaX: event.clientX,
      pantallaY: event.clientY,
      movido: false,
    };
  }

  onNodoMove(event: PointerEvent): void {
    const ar = this.arrastre;
    if (!ar) return;
    if (!ar.movido) {
      if (Math.hypot(event.clientX - ar.pantallaX, event.clientY - ar.pantallaY) < UMBRAL_ARRASTRE) return;
      ar.movido = true;
      this.arrastrandoId.set(ar.id);
    }
    const w = this.aMundo(event.clientX, event.clientY);
    const dx = this.ajustarACuadricula(w.x - ar.inicio.x);
    const dy = this.ajustarACuadricula(w.y - ar.inicio.y);
    const nuevas = { ...this.posiciones() };
    for (const [id, p] of ar.inicios) nuevas[id] = { x: p.x + dx, y: p.y + dy };
    this.posiciones.set(nuevas);
    this.objetivo.set(this.buscarObjetivo(w, ar.id));
  }

  async onNodoUp(event: PointerEvent, nodo: NodoVista): Promise<void> {
    const ar = this.arrastre;
    this.arrastre = null;
    this.arrastrandoId.set(null);
    this.objetivo.set(null);
    if (!ar) return;

    if (!ar.movido) {
      this.abrirDetalle(nodo.a);
      return;
    }

    const w = this.aMundo(event.clientX, event.clientY);
    const asesor = nodo.a;
    const valido = this.buscarObjetivo(w, ar.id);

    if (valido === 'ZONA' || typeof valido === 'number') {
      const ok =
        valido === 'ZONA'
          ? await this.guardarTipo(asesor, 'INDEPENDIENTE', null)
          : await this.guardarTipo(asesor, 'LINEA', valido);
      // Reasignado: el nodo (y lo que cuelga de él) vuelve al acomodo automático de su nuevo lugar.
      if (ok) this.quitarPosiciones([...ar.inicios.keys()]);
      else this.restaurar(ar);
      return;
    }
    // Soltada encima de otra tarjeta con la que no se puede combinar: vuelve a donde estaba.
    if (this.tarjetaBajo(w, ar.id) != null) {
      this.restaurar(ar);
      return;
    }
    this.guardarPosiciones();
  }

  onNodoCancel(): void {
    const ar = this.arrastre;
    this.arrastre = null;
    this.arrastrandoId.set(null);
    this.objetivo.set(null);
    if (ar?.movido) this.restaurar(ar);
  }

  private restaurar(ar: ArrastreNodo): void {
    const nuevas = { ...this.posiciones() };
    for (const [id, p] of ar.inicios) nuevas[id] = p;
    this.posiciones.set(nuevas);
    this.guardarPosiciones();
  }

  private ajustarACuadricula(v: number): number {
    return Math.round(v / CUADRICULA) * CUADRICULA;
  }

  private tarjetaBajo(p: Punto, excluirId: number): NodoVista | null {
    const excluidos = new Set(this.subarbol(excluirId));
    const nodos = this.nodosVista();
    for (let i = nodos.length - 1; i >= 0; i--) {
      const n = nodos[i];
      if (excluidos.has(n.a.id)) continue;
      if (p.x >= n.x && p.x <= n.x + n.w && p.y >= n.y && p.y <= n.y + n.h) return n;
    }
    return null;
  }

  /** Destino válido bajo el puntero: el id de un nodo donde "cuelga", 'ZONA' para independiente, o null. */
  private buscarObjetivo(p: Punto, arrastradoId: number): number | 'ZONA' | null {
    const mov = this.asesores().find((a) => a.id === arrastradoId);
    if (!mov || mov.tipo === 'LIDER') return null; // los líderes solo cambian de lugar
    const bajo = this.tarjetaBajo(p, arrastradoId);
    if (bajo) {
      if (bajo.a.id === mov.liderDirectoId) return null; // ya cuelga de ahí
      if (bajo.a.tipo === 'INDEPENDIENTE') return mov.tipo === 'LINEA' ? 'ZONA' : null; // soltar sobre un independiente = soltar en su zona
      const tieneHijos = (this.layout().nodos.get(mov.id)?.hijosTotal ?? 0) > 0;
      if (bajo.a.tipo === 'LIDER') return bajo.a.id;
      if (bajo.a.tipo === 'LINEA' && bajo.a.nivelLinea === 1 && !tieneHijos) return bajo.a.id;
      return null;
    }
    const z = this.zona();
    if (mov.tipo === 'LINEA' && p.x >= z.x && p.x <= z.x + z.w && p.y >= z.y && p.y <= z.y + z.h) return 'ZONA';
    return null;
  }

  private quitarPosiciones(ids: number[]): void {
    const nuevas = { ...this.posiciones() };
    for (const id of ids) delete nuevas[id];
    this.posiciones.set(nuevas);
    this.guardarPosiciones();
  }

  // ------------------------------------------------------------------ colapsar / detalle / guardado

  alternarColapso(event: Event, id: number): void {
    event.stopPropagation();
    const set = new Set(this.colapsados());
    if (set.has(id)) set.delete(id);
    else set.add(id);
    this.colapsados.set(set);
    this.escribirLocal(CLAVE_COLAPSADOS, [...set]);
  }

  abrirDetalle(asesor: AsesorExterno): void {
    this.detalleAbierto.set(asesor);
  }

  cerrarDetalle(): void {
    this.detalleAbierto.set(null);
  }

  async hacerLiderDesdeDetalle(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'LIDER', null);
    this.cerrarDetalle();
  }

  async quitarDeEquipoDesdeDetalle(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'INDEPENDIENTE', null);
    this.cerrarDetalle();
  }

  private async guardarTipo(
    asesor: AsesorExterno,
    tipo: TipoAsesorExterno,
    liderDirectoId: number | null,
  ): Promise<boolean> {
    this.savingId.set(asesor.id);
    try {
      const actualizado = await this.asesoresExternosService.actualizar(asesor.id, {
        nombre: asesor.nombre,
        celular: asesor.celular,
        correo: asesor.correo,
        activo: asesor.activo,
        tipo,
        liderDirectoId,
      });
      this.asesores.update((lista) => lista.map((a) => (a.id === actualizado.id ? actualizado : a)));
      this.recienMovidoId.set(actualizado.id);
      setTimeout(() => {
        if (this.recienMovidoId() === actualizado.id) this.recienMovidoId.set(null);
      }, 900);
      return true;
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo actualizar el equipo.';
      this.toast.error(mensaje);
      return false;
    } finally {
      this.savingId.set(null);
    }
  }

  private guardarPosiciones(): void {
    this.escribirLocal(CLAVE_POSICIONES, this.posiciones());
  }

  private leerLocal<T>(clave: string, defecto: T): T {
    try {
      const crudo = localStorage.getItem(clave);
      return crudo ? (JSON.parse(crudo) as T) : defecto;
    } catch {
      return defecto;
    }
  }

  private escribirLocal(clave: string, valor: unknown): void {
    try {
      localStorage.setItem(clave, JSON.stringify(valor));
    } catch {
      // sin almacenamiento (modo privado, cuota): el lienzo sigue funcionando, solo no recuerda.
    }
  }
}
