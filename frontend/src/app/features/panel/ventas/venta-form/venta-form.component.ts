import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Desarrollo } from '../../../../core/models/lead.model';
import { ESTADO_LOTE_LABELS, Lote } from '../../../../core/models/lote.model';
import { LeadsService } from '../../../../core/services/leads.service';
import { LotesService } from '../../../../core/services/lotes.service';
import { VentasService } from '../../../../core/services/ventas.service';

/** Suficiente para traer todos los lotes de un desarrollo de un jalón: son cientos, no miles. */
const TAMANO_LOTES_DESARROLLO = 1000;

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './venta-form.component.html',
})
export class VentaFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly ventasService = inject(VentasService);
  private readonly router = inject(Router);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly lotesDelDesarrollo = signal<Lote[]>([]);
  readonly isCargandoLotes = signal(false);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    loteId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    cliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    precioVenta: this.fb.control<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    formaPago: this.fb.control('Contado', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    notas: this.fb.control(''),
    marcarLoteVendido: this.fb.control(true, { nonNullable: true }),
  });

  readonly loteSeleccionado = computed<Lote | null>(() => {
    const id = this.form.controls.loteId.value;
    return id == null ? null : (this.lotesDelDesarrollo().find((l) => l.id === id) ?? null);
  });

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    // Autocompleta el precio con el mismo estimado que ya se ve en /panel/lotes (precio por m² del
    // desarrollo × superficie del lote); queda editable por si la venta se cerró en otro monto.
    this.form.controls.loteId.valueChanges.subscribe((loteId) => this.autocompletarPrecio(loteId));
  }

  private autocompletarPrecio(loteId: number | null): void {
    const lote = loteId == null ? null : this.lotesDelDesarrollo().find((l) => l.id === loteId);
    if (!lote) return;
    this.form.controls.precioVenta.setValue(Math.round(lote.desarrollo.precioM2 * lote.superficie));
  }

  async onDesarrolloChange(valor: string): Promise<void> {
    const desarrolloId = valor === '' ? null : Number(valor);
    this.form.controls.desarrolloId.setValue(desarrolloId);
    this.form.controls.loteId.setValue(null);
    this.lotesDelDesarrollo.set([]);
    if (desarrolloId === null) return;

    this.isCargandoLotes.set(true);
    try {
      const resultado = await this.lotesService.buscarPaginado({
        desarrolloId,
        pagina: 0,
        tamano: TAMANO_LOTES_DESARROLLO,
      });
      this.lotesDelDesarrollo.set(resultado.contenido);
    } catch {
      this.errorMessage.set('No se pudieron cargar los lotes de este desarrollo.');
    } finally {
      this.isCargandoLotes.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();

    try {
      await this.ventasService.crear({
        loteId: v.loteId!,
        cliente: v.cliente,
        asesor: v.asesor,
        precioVenta: v.precioVenta!,
        formaPago: v.formaPago,
        fechaVenta: v.fechaVenta,
        notas: v.notas?.trim() || null,
        marcarLoteVendido: v.marcarLoteVendido,
      });
      await this.router.navigate(['/panel/ventas']);
    } catch (error) {
      this.errorMessage.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar la venta. Intenta de nuevo.',
      );
    } finally {
      this.isLoading.set(false);
    }
  }
}
