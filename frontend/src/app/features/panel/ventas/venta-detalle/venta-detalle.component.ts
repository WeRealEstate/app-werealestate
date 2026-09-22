import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ToastService } from '../../../../core/services/toast.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { PagoVenta, Venta } from '../../../../core/models/venta.model';

@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './venta-detalle.component.html',
})
export class VentaDetalleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly venta = signal<Venta | null>(null);
  readonly pagos = signal<PagoVenta[]>([]);
  readonly isLoading = signal(true);
  readonly isSavingPago = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly pagoError = signal<string | null>(null);

  private ventaId!: number;

  readonly pagoForm = this.fb.group({
    fecha: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    monto: this.fb.control<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    notas: this.fb.control(''),
  });

  ngOnInit(): void {
    this.ventaId = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [venta, pagos] = await Promise.all([
        this.ventasService.obtener(this.ventaId),
        this.ventasService.listarPagos(this.ventaId),
      ]);
      this.venta.set(venta);
      this.pagos.set(pagos);
    } catch {
      this.errorMessage.set('No se pudo cargar la venta.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async registrarPago(): Promise<void> {
    if (this.pagoForm.invalid || this.isSavingPago()) {
      this.pagoForm.markAllAsTouched();
      return;
    }

    this.isSavingPago.set(true);
    this.pagoError.set(null);
    const v = this.pagoForm.getRawValue();

    try {
      await this.ventasService.registrarPago(this.ventaId, {
        fecha: v.fecha,
        monto: v.monto!,
        notas: v.notas?.trim() || null,
      });
      // El saldo pendiente se recalcula en el servidor; volvemos a cargar todo para que quede
      // consistente en vez de intentar restar a mano aquí.
      await this.cargar();
      this.pagoForm.reset({ fecha: new Date().toISOString().slice(0, 10), monto: null, notas: '' });
      this.toast.success('Abono registrado.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar el abono.';
      this.pagoError.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.isSavingPago.set(false);
    }
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
