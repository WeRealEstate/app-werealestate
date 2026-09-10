import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CotizacionesService } from '../../../core/services/cotizaciones.service';
import { CotizacionAsesor, CotizacionHistorial } from '../../../core/models/cotizacion.model';
import { descargarCsv } from '../../../core/utils/csv';
import { descargarExcel } from '../../../core/utils/excel';

/** Cuántas cotizaciones se cargan por lote. Al llegar al final, "Cargar más" trae el siguiente. */
const TAMANO_PAGINA = 10;

/** Para exportar se necesita todo lo que coincide con los filtros, no solo lo ya cargado en
 * pantalla; se pide de un jalón con un tamaño de página generoso. */
const TAMANO_EXPORTACION = 5000;

/** Espera esto antes de volver a consultar al servidor mientras el usuario sigue escribiendo. */
const DEBOUNCE_BUSQUEDA_MS = 350;

@Component({
  selector: 'app-historial-cotizaciones',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './historial-cotizaciones.component.html',
})
export class HistorialCotizacionesComponent {
  private readonly cotizacionesService = inject(CotizacionesService);

  /** Cotizaciones acumuladas de los lotes cargados hasta ahora, ya filtradas por el servidor. */
  readonly cotizaciones = signal<CotizacionHistorial[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hayMas = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');
  readonly asesorId = signal<number | null>(null);
  readonly proyectoFiltro = signal<string | null>(null);

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  /** Asesores y proyectos vistos en las cotizaciones ya cargadas, para poblar los filtros. */
  readonly asesoresDisponibles = computed<CotizacionAsesor[]>(() => {
    const porId = new Map<number, CotizacionAsesor>();
    for (const c of this.cotizaciones()) {
      porId.set(c.asesor.id, c.asesor);
    }
    return [...porId.values()].sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  readonly proyectosDisponibles = computed<string[]>(() => {
    return [...new Set(this.cotizaciones().map((c) => c.proyecto))].sort();
  });

  readonly hayFiltrosActivos = computed(
    () => this.filtro().trim().length > 0 || this.asesorId() !== null || this.proyectoFiltro() !== null,
  );

  constructor() {
    this.cargar();
  }

  onFiltroInput(valor: string): void {
    this.filtro.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.cargar(), DEBOUNCE_BUSQUEDA_MS);
  }

  onAsesorChange(valor: string): void {
    this.asesorId.set(valor === '' ? null : +valor);
    this.cargar();
  }

  onProyectoChange(valor: string): void {
    this.proyectoFiltro.set(valor === '' ? null : valor);
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const resultado = await this.buscarPagina(0, TAMANO_PAGINA);
      this.cotizaciones.set(resultado.contenido);
      this.hayMas.set(resultado.hayMas);
      this.pagina = 0;
    } catch {
      this.errorMessage.set('No se pudo cargar el historial de cotizaciones.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cargarMas(): Promise<void> {
    if (this.isLoadingMore() || !this.hayMas()) return;
    this.isLoadingMore.set(true);
    try {
      const siguiente = this.pagina + 1;
      const resultado = await this.buscarPagina(siguiente, TAMANO_PAGINA);
      this.cotizaciones.update((actuales) => [...actuales, ...resultado.contenido]);
      this.hayMas.set(resultado.hayMas);
      this.pagina = siguiente;
    } catch {
      this.errorMessage.set('No se pudieron cargar más cotizaciones.');
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  private buscarPagina(pagina: number, tamano: number) {
    return this.cotizacionesService.buscarPaginado({
      busqueda: this.filtro().trim() || undefined,
      asesorId: this.asesorId() ?? undefined,
      proyecto: this.proyectoFiltro() ?? undefined,
      pagina,
      tamano,
    });
  }

  private datosExportacion(cotizaciones: CotizacionHistorial[]) {
    const filas = cotizaciones.map((c) => ({
      fecha: c.fechaCreacion,
      asesor: c.asesor.nombre,
      cliente: c.nombreCliente,
      proyecto: c.proyecto,
      manzana: c.manzana ?? '',
      lote: c.lote ?? '',
      superficie: c.superficie,
      precioTotal: c.precioTotal,
      formaPago: c.formaPago,
      enganche: c.enganche,
      mensualidad: c.mensualidad,
      totalInversion: c.totalInversion,
    }));

    const encabezados = {
      fecha: 'Fecha',
      asesor: 'Asesor',
      cliente: 'Cliente',
      proyecto: 'Proyecto',
      manzana: 'Manzana',
      lote: 'Lote',
      superficie: 'Superficie (m²)',
      precioTotal: 'Precio total',
      formaPago: 'Forma de pago',
      enganche: 'Enganche',
      mensualidad: 'Mensualidad',
      totalInversion: 'Total de inversión',
    };

    return { filas, encabezados };
  }

  /** La exportación trae TODAS las cotizaciones que coinciden con los filtros actuales, no solo
   * las que ya están cargadas en pantalla (podrían ser solo las primeras 10). */
  private async obtenerTodasLasFiltradas(): Promise<CotizacionHistorial[]> {
    const resultado = await this.buscarPagina(0, TAMANO_EXPORTACION);
    return resultado.contenido;
  }

  async exportarCsv(): Promise<void> {
    const cotizaciones = await this.obtenerTodasLasFiltradas();
    const { filas, encabezados } = this.datosExportacion(cotizaciones);
    descargarCsv(`cotizaciones_${new Date().toISOString().slice(0, 10)}.csv`, encabezados, filas);
  }

  async exportarExcel(): Promise<void> {
    const cotizaciones = await this.obtenerTodasLasFiltradas();
    const { filas, encabezados } = this.datosExportacion(cotizaciones);
    await descargarExcel(`cotizaciones_${new Date().toISOString().slice(0, 10)}.xlsx`, encabezados, filas, 'Cotizaciones');
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
