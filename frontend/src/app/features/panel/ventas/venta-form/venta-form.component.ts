import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Desarrollo } from '../../../../core/models/lead.model';
import { ESTADO_LOTE_LABELS, Lote } from '../../../../core/models/lote.model';
import { LeadsService } from '../../../../core/services/leads.service';
import { LotesService } from '../../../../core/services/lotes.service';
import { VentasService } from '../../../../core/services/ventas.service';

/** Suficiente para traer todos los lotes de un desarrollo de un jalón: son cientos, no miles. */
const TAMANO_LOTES_DESARROLLO = 1000;

interface LoteAgregado {
  lote: Lote;
  precio: number;
}

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink],
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

  /** Lotes que ya se agregaron a esta venta (un cliente puede comprar varios en la misma
   * operación, con una sola mensualidad/plazo/saldo combinado). */
  readonly lotesAgregados = signal<LoteAgregado[]>([]);
  readonly precioTotal = computed(() => this.lotesAgregados().reduce((sum, l) => sum + l.precio, 0));

  readonly form = this.fb.group({
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    cliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('Contado', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    // Opcionales: una venta de contado no los necesita.
    mensualidad: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    plazoMeses: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    notas: this.fb.control(''),
    marcarLoteVendido: this.fb.control(true, { nonNullable: true }),
  });

  /** Mini-formulario para agregar un lote a la lista (separado del form principal: no se envía
   * directo, cada "Agregar" lo empuja a lotesAgregados). */
  readonly loteParaAgregarId = signal<number | null>(null);
  readonly precioParaAgregar = signal<number | null>(null);

  /** Lotes del desarrollo elegido que todavía no se agregaron, para no repetir uno en la misma venta. */
  readonly lotesDisponiblesParaAgregar = computed<Lote[]>(() => {
    const agregadosIds = new Set(this.lotesAgregados().map((l) => l.lote.id));
    return this.lotesDelDesarrollo().filter((l) => !agregadosIds.has(l.id));
  });

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
  }

  async onDesarrolloChange(valor: string): Promise<void> {
    const desarrolloId = valor === '' ? null : Number(valor);
    this.form.controls.desarrolloId.setValue(desarrolloId);
    this.loteParaAgregarId.set(null);
    this.precioParaAgregar.set(null);
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

  /** Autocompleta el precio con el mismo estimado que ya se ve en /panel/lotes (precio por m² del
   * desarrollo × superficie del lote); queda editable por si la venta se cerró en otro monto. */
  onLoteParaAgregarChange(valor: string): void {
    const loteId = valor === '' ? null : Number(valor);
    this.loteParaAgregarId.set(loteId);
    const lote = loteId == null ? null : this.lotesDelDesarrollo().find((l) => l.id === loteId);
    this.precioParaAgregar.set(lote ? Math.round(lote.desarrollo.precioM2 * lote.superficie) : null);
  }

  agregarLote(): void {
    const loteId = this.loteParaAgregarId();
    const precio = this.precioParaAgregar();
    if (loteId == null || precio == null || precio <= 0) return;

    const lote = this.lotesDelDesarrollo().find((l) => l.id === loteId);
    if (!lote) return;

    this.lotesAgregados.update((actuales) => [...actuales, { lote, precio }]);
    this.loteParaAgregarId.set(null);
    this.precioParaAgregar.set(null);
  }

  quitarLote(loteId: number): void {
    this.lotesAgregados.update((actuales) => actuales.filter((l) => l.lote.id !== loteId));
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.lotesAgregados().length === 0 || this.isLoading()) {
      this.form.markAllAsTouched();
      if (this.lotesAgregados().length === 0) {
        this.errorMessage.set('Agrega al menos un lote a la venta.');
      }
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();

    try {
      await this.ventasService.crear({
        lotes: this.lotesAgregados().map((l) => ({ loteId: l.lote.id, precio: l.precio })),
        cliente: v.cliente,
        asesor: v.asesor,
        formaPago: v.formaPago,
        fechaVenta: v.fechaVenta,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
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

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
