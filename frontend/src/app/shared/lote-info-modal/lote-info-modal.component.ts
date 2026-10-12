import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  Lote,
  MovimientoLote,
} from '../../core/models/lote.model';
import { PagoVenta, TIPO_ABONO_LABELS, Venta } from '../../core/models/venta.model';
import { LotesService } from '../../core/services/lotes.service';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';
import { WeLoaderComponent } from '../we-loader/we-loader.component';
import { ReciboFormComponent } from '../recibo-form/recibo-form.component';

/** "Información del lote" de un lote comprometido (apartado, en firma o vendido) — solo para admin
 * y líder de área (mismo permiso que el módulo de Ventas). Un Vendido muestra su Venta si existe en
 * el módulo de Ventas ('venta', con abonos y registro de nuevos); cualquier otro estado comprometido
 * (o un Vendido marcado a mano sin pasar por Ventas) muestra su historial de movimientos ('estado').
 * Lo usan /panel/lotes y /panel/plano; el padre lo monta con un @if y reacciona a `cerrar`. */
@Component({
  selector: 'app-lote-info-modal',
  standalone: true,
  imports: [ReciboFormComponent, WeLoaderComponent, RouterLink, DatePipe],
  templateUrl: './lote-info-modal.component.html',
})
export class LoteInfoModalComponent implements OnInit {
  private readonly lotesService = inject(LotesService);
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly lote = input.required<Lote>();
  readonly cerrar = output<void>();

  readonly tipoLabels = TIPO_ABONO_LABELS;
  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;

  readonly infoModo = signal<'venta' | 'estado' | null>(null);
  readonly ventaDeLote = signal<Venta | null>(null);
  readonly historialInfoLote = signal<MovimientoLote[]>([]);
  readonly cargandoInfoLote = signal(false);
  readonly errorInfoLote = signal<string | null>(null);

  /** Historial de abonos de la venta mostrada; el registro de uno nuevo lo hace app-recibo-form. */
  readonly pagosInfoLote = signal<PagoVenta[]>([]);

  ngOnInit(): void {
    void this.cargar();
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Precio negociado de este lote específico dentro de su venta (puede diferir del estimado si
   * la venta incluyó varios lotes con precios distintos); null si por algún motivo no aparece en
   * la lista de lotes de la venta encontrada. */
  precioDeLoteEnVenta(venta: Venta, loteId: number): number | null {
    return venta.lotes.find((l) => l.lote.id === loteId)?.precio ?? null;
  }

  /** Cuántos días completos lleva un lote en su estado actual, a partir de fechaCambioEstado. */
  diasEnEstado(lote: Lote): number {
    const ms = Date.now() - new Date(lote.fechaCambioEstado).getTime();
    return Math.max(0, Math.floor(ms / 86_400_000));
  }

  private async cargar(): Promise<void> {
    const lote = this.lote();
    this.cargandoInfoLote.set(true);
    try {
      if (lote.estado === 'VENDIDO') {
        const venta = await this.ventasService.obtenerPorLote(lote.id).catch(() => null);
        if (venta) {
          this.ventaDeLote.set(venta);
          this.pagosInfoLote.set(await this.ventasService.listarPagos(venta.id));
          this.infoModo.set('venta');
          return;
        }
      }
      this.historialInfoLote.set(await this.lotesService.historialDeLote(lote.id));
      this.infoModo.set('estado');
    } catch {
      this.errorInfoLote.set('No se pudo cargar la información de este lote.');
    } finally {
      this.cargandoInfoLote.set(false);
    }
  }

  /** Vuelve a cargar la venta y sus pagos desde el servidor tras registrar un recibo, para que total
   * abonado/saldo queden consistentes con lo que ya calculó el backend, en vez de restar a mano aquí. */
  async recargarVentaInfoLote(): Promise<void> {
    const venta = this.ventaDeLote();
    if (!venta) return;
    try {
      const [ventaActualizada, pagos] = await Promise.all([
        this.ventasService.obtener(venta.id),
        this.ventasService.listarPagos(venta.id),
      ]);
      this.ventaDeLote.set(ventaActualizada);
      this.pagosInfoLote.set(pagos);
    } catch {
      this.toast.error('El abono se registró, pero no se pudo actualizar la información.');
    }
  }
}
