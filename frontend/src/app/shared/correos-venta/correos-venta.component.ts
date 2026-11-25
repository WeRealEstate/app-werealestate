import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { CorreosVenta, TIPO_CORREO_LABELS } from '../../core/models/venta.model';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

/** Registro de correos de una venta, en letra pequeña: lo enviado (a mano o solo), lo que está por salir y lo que no
 * salió; y la casilla para no mandarle el recordatorio automático. Solo admin y administración. */
@Component({
  selector: 'app-correos-venta',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './correos-venta.component.html',
})
export class CorreosVentaComponent {
  private readonly ventas = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly ventaId = input.required<number>();
  readonly conMensualidades = input(false);
  /** Cualquier valor que cambie cuando haya que volver a pedir el registro. */
  readonly refresco = input<unknown>(null);

  readonly registro = signal<CorreosVenta | null>(null);
  readonly guardando = signal(false);
  readonly etiquetas = TIPO_CORREO_LABELS;
  private pedido = 0;

  constructor() {
    effect(() => {
      const id = this.ventaId();
      this.refresco();
      const pedido = ++this.pedido;
      this.ventas
        .correos(id)
        .then((r) => {
          if (pedido === this.pedido) this.registro.set(r);
        })
        .catch(() => {
          if (pedido === this.pedido) this.registro.set(null);
        });
    });
  }

  async excluir(casilla: HTMLInputElement): Promise<void> {
    this.guardando.set(true);
    try {
      this.registro.set(await this.ventas.excluirRecordatorio(this.ventaId(), casilla.checked));
    } catch {
      casilla.checked = !casilla.checked;
      this.toast.error('No se pudo guardar.');
    } finally {
      this.guardando.set(false);
    }
  }
}
