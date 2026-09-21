import { DatePipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VentasService } from '../../../../core/services/ventas.service';
import { Venta } from '../../../../core/models/venta.model';

/** Cuántas ventas se cargan por lote. Al llegar al final, "Cargar más" trae el siguiente. */
const TAMANO_PAGINA = 10;

/** Espera esto antes de volver a consultar al servidor mientras el usuario sigue escribiendo. */
const DEBOUNCE_BUSQUEDA_MS = 350;

@Component({
  selector: 'app-ventas-list',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './ventas-list.component.html',
})
export class VentasListComponent {
  private readonly ventasService = inject(VentasService);

  readonly ventas = signal<Venta[]>([]);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hayMas = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.cargar();
  }

  onFiltroInput(valor: string): void {
    this.filtro.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.cargar(), DEBOUNCE_BUSQUEDA_MS);
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const resultado = await this.buscarPagina(0, TAMANO_PAGINA);
      this.ventas.set(resultado.contenido);
      this.hayMas.set(resultado.hayMas);
      this.pagina = 0;
    } catch {
      this.errorMessage.set('No se pudo cargar el historial de ventas.');
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
      this.ventas.update((actuales) => [...actuales, ...resultado.contenido]);
      this.hayMas.set(resultado.hayMas);
      this.pagina = siguiente;
    } catch {
      this.errorMessage.set('No se pudieron cargar más ventas.');
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  private buscarPagina(pagina: number, tamano: number) {
    return this.ventasService.buscarPaginado({ busqueda: this.filtro().trim() || undefined, pagina, tamano });
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
