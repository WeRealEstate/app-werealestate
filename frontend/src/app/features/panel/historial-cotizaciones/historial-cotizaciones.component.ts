import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { CotizacionesService } from '../../../core/services/cotizaciones.service';
import { CotizacionAsesor, CotizacionHistorial } from '../../../core/models/cotizacion.model';
import { descargarCsv } from '../../../core/utils/csv';
import { descargarExcel } from '../../../core/utils/excel';

@Component({
  selector: 'app-historial-cotizaciones',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './historial-cotizaciones.component.html',
})
export class HistorialCotizacionesComponent {
  private readonly cotizacionesService = inject(CotizacionesService);

  readonly cotizaciones = signal<CotizacionHistorial[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');
  readonly asesorId = signal<number | null>(null);
  readonly proyectoFiltro = signal<string | null>(null);

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

  readonly cotizacionesFiltradas = computed(() => {
    const term = this.filtro().trim().toLowerCase();
    const asesorId = this.asesorId();
    const proyecto = this.proyectoFiltro();
    return this.cotizaciones().filter((c) => {
      if (asesorId !== null && c.asesor.id !== asesorId) return false;
      if (proyecto && c.proyecto !== proyecto) return false;
      if (term && !c.nombreCliente.toLowerCase().includes(term)) return false;
      return true;
    });
  });

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.cotizaciones.set(await this.cotizacionesService.listar());
    } catch {
      this.errorMessage.set('No se pudo cargar el historial de cotizaciones.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private datosExportacion() {
    const filas = this.cotizacionesFiltradas().map((c) => ({
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

  exportarCsv(): void {
    const { filas, encabezados } = this.datosExportacion();
    descargarCsv(`cotizaciones_${new Date().toISOString().slice(0, 10)}.csv`, encabezados, filas);
  }

  async exportarExcel(): Promise<void> {
    const { filas, encabezados } = this.datosExportacion();
    await descargarExcel(`cotizaciones_${new Date().toISOString().slice(0, 10)}.xlsx`, encabezados, filas, 'Cotizaciones');
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
