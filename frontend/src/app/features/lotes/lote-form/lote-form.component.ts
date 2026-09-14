import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';

@Component({
  selector: 'app-lote-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './lote-form.component.html',
})
export class LoteFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loteId = signal<number | null>(null);
  readonly modoEdicion = computed(() => this.loteId() !== null);
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly isLoading = signal(false);
  readonly isCargando = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    manzana: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    numeroLote: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    superficie: this.fb.control<number | null>(null, { validators: [Validators.required, Validators.min(0.0001)] }),
    precio: this.fb.control<number | null>(null),
  });

  async ngOnInit(): Promise<void> {
    this.desarrollos.set(await this.leadsService.listarDesarrollos());

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.loteId.set(id);
      await this.cargarLoteParaEditar(id);
    }
  }

  private async cargarLoteParaEditar(id: number): Promise<void> {
    this.isCargando.set(true);
    this.errorMessage.set(null);
    try {
      const lote = await this.lotesService.obtener(id);
      this.form.patchValue({
        desarrolloId: lote.desarrollo.id,
        manzana: lote.manzana,
        numeroLote: lote.numeroLote,
        superficie: lote.superficie,
        precio: lote.precio,
      });
      this.form.controls.desarrolloId.disable();
    } catch {
      this.errorMessage.set('No se pudo cargar el lote a editar.');
    } finally {
      this.isCargando.set(false);
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
    const id = this.loteId();

    try {
      if (id !== null) {
        await this.lotesService.actualizar(id, {
          manzana: v.manzana,
          numeroLote: v.numeroLote,
          superficie: v.superficie!,
          precio: v.precio,
        });
      } else {
        await this.lotesService.crear({
          desarrolloId: v.desarrolloId!,
          manzana: v.manzana,
          numeroLote: v.numeroLote,
          superficie: v.superficie!,
          precio: v.precio,
        });
      }
      await this.router.navigate(['/panel/lotes']);
    } catch (error) {
      this.errorMessage.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : `No se pudo ${id !== null ? 'actualizar' : 'crear'} el lote. Intenta de nuevo.`,
      );
    } finally {
      this.isLoading.set(false);
    }
  }
}
