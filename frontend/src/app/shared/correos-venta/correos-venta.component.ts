import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { CorreosVenta, TIPO_CORREO_LABELS, TipoCorreo } from '../../core/models/venta.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

/** Registro de correos de una venta, en letra pequeña: lo enviado (a mano o solo), lo que está por salir y lo que no
 * salió (con «Reintentar» si todavía se puede mandar); y la casilla para no mandarle el recordatorio automático. Solo
 * admin y administración. */
@Component({
  selector: 'app-correos-venta',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './correos-venta.component.html',
})
export class CorreosVentaComponent {
  private readonly ventas = inject(VentasService);
  private readonly toast = inject(ToastService);
  private readonly confirmar = inject(ConfirmService);

  readonly ventaId = input.required<number>();
  readonly conMensualidades = input(false);
  /** Cualquier valor que cambie cuando haya que volver a pedir el registro. */
  readonly refresco = input<unknown>(null);

  readonly registro = signal<CorreosVenta | null>(null);
  readonly guardando = signal(false);
  /** Id del correo que se está reintentando. */
  readonly reintentando = signal<number | null>(null);
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

  async reintentar(correoId: number, tipo: TipoCorreo): Promise<void> {
    if (this.reintentando() !== null) return;
    const destino = tipo === 'LIQUIDACION' ? 'a finanzas' : 'al correo del cliente titular';
    const confirmado = await this.confirmar.confirm({
      titulo: 'Reintentar correo',
      mensaje: `Se enviará ahora ${destino}: ${this.etiquetas[tipo]}.`,
      textoConfirmar: 'Enviar',
    });
    if (!confirmado) return;
    this.reintentando.set(correoId);
    const id = this.ventaId();
    try {
      const registro = await this.ventas.reintentarCorreo(id, correoId);
      if (id === this.ventaId()) this.registro.set(registro);
      this.toast.success('Correo enviado.');
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo enviar el correo.',
      );
    } finally {
      this.reintentando.set(null);
    }
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
