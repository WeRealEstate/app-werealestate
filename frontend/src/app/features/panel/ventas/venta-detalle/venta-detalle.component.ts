import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ToastService } from '../../../../core/services/toast.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AsesoresExternosService } from '../../../../core/services/asesores-externos.service';
import { PagoVenta, Venta } from '../../../../core/models/venta.model';
import { UsuarioResumen } from '../../../../core/models/lead.model';
import { AsesorExterno } from '../../../../core/models/asesor-externo.model';
import { asesorSeleccionDe, parseAsesorSeleccion } from '../venta-form/venta-form.component';

@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './venta-detalle.component.html',
})
export class VentaDetalleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ventasService = inject(VentasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly venta = signal<Venta | null>(null);
  readonly asesoresInternos = signal<UsuarioResumen[]>([]);
  readonly asesoresExternos = signal<AsesorExterno[]>([]);
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

  /** Temporal: permite modificar los datos capturados de la venta (no sus lotes/precios). Se va a
   * quitar más adelante — avisan cuándo. */
  readonly editando = signal(false);
  readonly isSavingEdicion = signal(false);
  readonly errorEdicion = signal<string | null>(null);
  readonly edicionForm = this.fb.group({
    cliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    mensualidad: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    plazoMeses: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    engancheLabel: this.fb.control(''),
    enganche: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    notas: this.fb.control(''),
  });

  ngOnInit(): void {
    this.ventaId = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar();
    this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
    this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
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

  // --- Modificar venta (temporal) ---

  abrirEdicion(): void {
    const v = this.venta();
    if (!v) return;
    this.edicionForm.reset({
      cliente: v.cliente,
      asesor: asesorSeleccionDe(v.asesor),
      fechaVenta: v.fechaVenta,
      formaPago: v.formaPago,
      mensualidad: v.mensualidad,
      plazoMeses: v.plazoMeses,
      engancheLabel: v.engancheLabel ?? '',
      enganche: v.enganche,
      notas: v.notas ?? '',
    });
    this.errorEdicion.set(null);
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    this.editando.set(false);
  }

  async guardarEdicion(): Promise<void> {
    if (this.edicionForm.invalid || this.isSavingEdicion()) {
      this.edicionForm.markAllAsTouched();
      return;
    }

    this.isSavingEdicion.set(true);
    this.errorEdicion.set(null);
    const v = this.edicionForm.getRawValue();

    try {
      const actualizada = await this.ventasService.actualizar(this.ventaId, {
        cliente: v.cliente,
        ...parseAsesorSeleccion(v.asesor),
        fechaVenta: v.fechaVenta,
        formaPago: v.formaPago,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
        engancheLabel: v.engancheLabel?.trim() || null,
        enganche: v.enganche,
        notas: v.notas?.trim() || null,
      });
      this.venta.set(actualizada);
      this.editando.set(false);
      this.toast.success('Venta actualizada.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo actualizar la venta.';
      this.errorEdicion.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.isSavingEdicion.set(false);
    }
  }
}
