import { DatePipe } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { LucideArrowDown, LucideArrowUp, LucideEye } from '@lucide/angular';
import { inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VentasService } from '../../../../core/services/ventas.service';
import { LeadsService } from '../../../../core/services/leads.service';
import { Desarrollo } from '../../../../core/models/lead.model';
import { Venta } from '../../../../core/models/venta.model';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';
import { dinero } from '../../../../core/utils/formato-pantalla';

/** Cuántas ventas se cargan por lote. Al llegar al final, "Cargar más" trae el siguiente. */
const TAMANO_PAGINA = 10;

/** Espera esto antes de volver a consultar al servidor mientras el usuario sigue escribiendo. */
const DEBOUNCE_BUSQUEDA_MS = 350;

/** Un desarrollo dentro de la lista: sus ventas ya cargadas (clasificadas por el desarrollo de su primer lote, como
 * se ve en la columna Desarrollo), si hay más por cargar y cuántas ventas tiene en total. */
interface Seccion {
  desarrollo: Desarrollo;
  ventas: Venta[];
  hayMas: boolean;
  pagina: number;
  cargandoMas: boolean;
  total: number | null;
}

/** SAMAI primero, luego Nanuu y al final cualquier otro desarrollo por nombre (igual que la lista de Clientes). */
function ordenDesarrollo(d: Desarrollo): number {
  const n = d.nombre.toLowerCase();
  return n.includes('samai') ? 0 : n.includes('nanuu') ? 1 : 2;
}

@Component({
  selector: 'app-ventas-list',
  standalone: true,
  imports: [WeLoaderComponent, DatePipe, RouterLink, LucideEye, LucideArrowUp, LucideArrowDown],
  templateUrl: './ventas-list.component.html',
})
export class VentasListComponent {
  private readonly ventasService = inject(VentasService);
  private readonly leadsService = inject(LeadsService);

  /** La lista dividida por desarrollo (secciones sin ventas no se muestran). */
  readonly secciones = signal<Seccion[]>([]);
  readonly seccionesConVentas = computed(() => this.secciones().filter((s) => s.ventas.length > 0));
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');
  /** Desarrollos para el selector, y el elegido (null = todos). Siempre arranca en "Todos". */
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly desarrolloId = signal<number | null>(null);
  /** Orden de la lista por número de venta: desc = el más alto arriba. */
  readonly orden = signal<'asc' | 'desc'>('desc');

  readonly contadores = signal<{ lotesVendidos: number; ventasRegistradas: number } | null>(null);
  readonly contadoresError = signal(false);
  readonly lotesVendidosTexto = computed(() => this.textoContador(this.contadores()?.lotesVendidos));
  readonly ventasRegistradasTexto = computed(() => this.textoContador(this.contadores()?.ventasRegistradas));

  private textoContador(total: number | undefined): string {
    if (this.contadoresError()) return 'No disponible';
    return total === undefined ? 'Calculando' : total.toLocaleString('en-US');
  }

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.cargarContadores();
    // La lista se divide por desarrollo, así que primero se necesitan los desarrollos.
    this.leadsService
      .listarDesarrollosGestionables()
      .then((d) => this.desarrollos.set([...d].sort((a, b) => ordenDesarrollo(a) - ordenDesarrollo(b) || a.nombre.localeCompare(b.nombre, 'es'))))
      .catch(() => undefined)
      .finally(() => void this.cargar());
  }

  /** Al cambiar de desarrollo se recarga la lista desde la primera página y los contadores de ese desarrollo. */
  onDesarrolloChange(valor: string): void {
    this.desarrolloId.set(valor ? Number(valor) : null);
    void this.cargarContadores();
    void this.cargar();
  }

  private async cargarContadores(): Promise<void> {
    try {
      this.contadoresError.set(false);
      this.contadores.set(await this.ventasService.contadores(this.desarrolloId()));
    } catch {
      this.contadoresError.set(true);
      this.contadores.set(null);
    }
  }

  alternarOrden(): void {
    this.orden.update((o) => (o === 'desc' ? 'asc' : 'desc'));
    void this.cargar();
  }

  onFiltroInput(valor: string): void {
    this.filtro.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.cargar(), DEBOUNCE_BUSQUEDA_MS);
  }

  /** Carga la primera página de cada desarrollo (o solo del elegido en el selector), cada uno con su propio "Cargar más". */
  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const elegido = this.desarrolloId();
      const destinos = this.desarrollos().filter((d) => elegido === null || d.id === elegido);
      if (destinos.length === 0) throw new Error('Sin desarrollos');
      const secciones = await Promise.all(
        destinos.map(async (desarrollo): Promise<Seccion> => {
          const [resultado, contadores] = await Promise.all([
            this.buscarPagina(desarrollo.id, 0),
            this.ventasService.contadores(desarrollo.id).catch(() => null),
          ]);
          return {
            desarrollo,
            ventas: this.delDesarrollo(resultado.contenido, desarrollo.id),
            hayMas: resultado.hayMas,
            pagina: 0,
            cargandoMas: false,
            total: contadores?.ventasRegistradas ?? null,
          };
        }),
      );
      this.secciones.set(secciones);
    } catch {
      this.errorMessage.set('No se pudo cargar el historial de ventas.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cargarMas(seccion: Seccion): Promise<void> {
    if (seccion.cargandoMas || !seccion.hayMas) return;
    this.actualizarSeccion(seccion.desarrollo.id, { cargandoMas: true });
    try {
      const siguiente = seccion.pagina + 1;
      const resultado = await this.buscarPagina(seccion.desarrollo.id, siguiente);
      const nuevas = this.delDesarrollo(resultado.contenido, seccion.desarrollo.id);
      this.secciones.update((lista) =>
        lista.map((s) =>
          s.desarrollo.id === seccion.desarrollo.id
            ? { ...s, ventas: [...s.ventas, ...nuevas], hayMas: resultado.hayMas, pagina: siguiente, cargandoMas: false }
            : s,
        ),
      );
    } catch {
      this.actualizarSeccion(seccion.desarrollo.id, { cargandoMas: false });
      this.errorMessage.set('No se pudieron cargar más ventas.');
    }
  }

  private actualizarSeccion(id: number, cambios: Partial<Seccion>): void {
    this.secciones.update((lista) => lista.map((s) => (s.desarrollo.id === id ? { ...s, ...cambios } : s)));
  }

  /** Una venta con lotes de dos desarrollos se clasifica por su primer lote (el que muestra la columna Desarrollo). */
  private delDesarrollo(ventas: Venta[], desarrolloId: number): Venta[] {
    return ventas.filter((v) => v.lotes[0]?.lote.desarrollo.id === desarrolloId);
  }

  private buscarPagina(desarrolloId: number, pagina: number) {
    return this.ventasService.buscarPaginado({
      busqueda: this.filtro().trim() || undefined,
      orden: this.orden(),
      pagina,
      tamano: TAMANO_PAGINA,
      desarrolloId,
    });
  }

  money(valor: number): string {
    return dinero(valor);
  }
}
