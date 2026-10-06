import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanzasValor } from '../../../core/models/finanzas.model';
import { FinanzasService } from '../../../core/services/finanzas.service';

/** Pestaña "Valor vendido": cuánto dinero hay en lotes vendidos (por desarrollo y por lote). */
@Component({
  selector: 'app-finanzas-valor',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './finanzas-valor.component.html',
})
export class FinanzasValorComponent {
  private readonly finanzasService = inject(FinanzasService);

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly datos = signal<FinanzasValor | null>(null);

  readonly filtroDesarrollo = signal('');
  readonly busqueda = signal('');
  readonly detalleFiltrado = computed(() => {
    const d = this.datos();
    if (!d) return [];
    const desarrollo = this.filtroDesarrollo();
    const texto = this.busqueda().trim().toLowerCase();
    return d.detalle.filter(
      (l) =>
        (!desarrollo || l.desarrollo === desarrollo) &&
        (!texto ||
          l.cliente.toLowerCase().includes(texto) ||
          `${l.manzana}-${l.numeroLote}`.toLowerCase().includes(texto) ||
          String(l.ventaNumero).includes(texto)),
    );
  });
  readonly totalFiltrado = computed(() => this.detalleFiltrado().reduce((suma, l) => suma + l.precio, 0));

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.datos.set(await this.finanzasService.valorVendido());
    } catch {
      this.errorMessage.set('No se pudo cargar el valor vendido. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Porción del total que representa un desarrollo, para la barra de cada tarjeta. */
  porcentaje(valor: number): number {
    const total = this.datos()?.total ?? 0;
    return total > 0 ? Math.round((valor / total) * 100) : 0;
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
