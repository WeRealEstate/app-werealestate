import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  Lote,
  MovimientoLote,
} from '../../core/models/lote.model';
import { PagoVenta, Venta } from '../../core/models/venta.model';
import { LotesService } from '../../core/services/lotes.service';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';
import { WeLoaderComponent } from '../we-loader/we-loader.component';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

/** "Información del lote" de un lote comprometido (apartado, en firma o vendido) — solo para admin
 * y líder de área (mismo permiso que el módulo de Ventas). Un Vendido muestra su Venta si existe en
 * el módulo de Ventas ('venta', con abonos y registro de nuevos); cualquier otro estado comprometido
 * (o un Vendido marcado a mano sin pasar por Ventas) muestra su historial de movimientos ('estado').
 * Lo usan /panel/lotes y /panel/plano; el padre lo monta con un @if y reacciona a `cerrar`. */
@Component({
  selector: 'app-lote-info-modal',
  standalone: true,
  imports: [MonedaInputDirective, WeLoaderComponent, FormsModule, RouterLink, DatePipe],
  templateUrl: './lote-info-modal.component.html',
})
export class LoteInfoModalComponent implements OnInit {
  private readonly lotesService = inject(LotesService);
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly lote = input.required<Lote>();
  readonly cerrar = output<void>();

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;

  readonly infoModo = signal<'venta' | 'estado' | null>(null);
  readonly ventaDeLote = signal<Venta | null>(null);
  readonly historialInfoLote = signal<MovimientoLote[]>([]);
  readonly cargandoInfoLote = signal(false);
  readonly errorInfoLote = signal<string | null>(null);

  /** Historial de abonos de la venta mostrada, y el mini-formulario para registrar uno nuevo sin
   * salir de la pantalla desde donde se abrió. */
  readonly pagosInfoLote = signal<PagoVenta[]>([]);
  readonly fechaPagoInfoLote = signal(new Date().toISOString().slice(0, 10));
  readonly montoPagoInfoLote = signal<number | null>(null);
  readonly notasPagoInfoLote = signal('');
  readonly guardandoPagoInfoLote = signal(false);
  readonly errorPagoInfoLote = signal<string | null>(null);

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

  private reiniciarFormularioAbonoInfoLote(): void {
    this.fechaPagoInfoLote.set(new Date().toISOString().slice(0, 10));
    this.montoPagoInfoLote.set(null);
    this.notasPagoInfoLote.set('');
    this.errorPagoInfoLote.set(null);
  }

  /** Vuelve a cargar la venta y sus pagos desde el servidor (igual que
   * VentaDetalleComponent.registrarPago) para que total abonado/saldo queden consistentes con lo
   * que ya calculó el backend, en vez de restar a mano aquí. */
  async registrarAbonoInfoLote(): Promise<void> {
    const venta = this.ventaDeLote();
    const monto = this.montoPagoInfoLote();
    if (!venta || this.guardandoPagoInfoLote()) return;
    if (!this.fechaPagoInfoLote() || !monto || monto <= 0) {
      this.errorPagoInfoLote.set('Ingresa una fecha y un monto válido.');
      return;
    }

    this.guardandoPagoInfoLote.set(true);
    this.errorPagoInfoLote.set(null);
    try {
      await this.ventasService.registrarPago(venta.id, {
        fecha: this.fechaPagoInfoLote(),
        monto,
        notas: this.notasPagoInfoLote().trim() || null,
      });
      const [ventaActualizada, pagos] = await Promise.all([
        this.ventasService.obtener(venta.id),
        this.ventasService.listarPagos(venta.id),
      ]);
      this.ventaDeLote.set(ventaActualizada);
      this.pagosInfoLote.set(pagos);
      this.reiniciarFormularioAbonoInfoLote();
      this.toast.success('Abono registrado.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar el abono.';
      this.errorPagoInfoLote.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.guardandoPagoInfoLote.set(false);
    }
  }
}
