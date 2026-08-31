import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LeadsService } from '../../../core/services/leads.service';
import {
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Lead,
  Seguimiento,
  TIPO_SEGUIMIENTO_LABELS,
  TipoSeguimiento,
} from '../../../core/models/lead.model';

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './lead-detail.component.html',
})
export class LeadDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly leadsService = inject(LeadsService);
  private readonly fb = inject(FormBuilder);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly tipoLabels = TIPO_SEGUIMIENTO_LABELS;
  readonly estados = Object.keys(ESTADO_LEAD_LABELS) as EstadoLead[];
  readonly tipos = Object.keys(TIPO_SEGUIMIENTO_LABELS) as TipoSeguimiento[];

  readonly lead = signal<Lead | null>(null);
  readonly seguimientos = signal<Seguimiento[]>([]);
  readonly isLoading = signal(true);
  readonly isSavingEstado = signal(false);
  readonly isSavingSeguimiento = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private leadId!: number;

  readonly seguimientoForm = this.fb.group({
    tipo: this.fb.control<TipoSeguimiento>('LLAMADA', { nonNullable: true, validators: [Validators.required] }),
    nota: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    resultado: this.fb.control('', { nonNullable: true }),
    proximoSeguimiento: this.fb.control('', { nonNullable: true }),
  });

  async ngOnInit(): Promise<void> {
    this.leadId = Number(this.route.snapshot.paramMap.get('id'));
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [lead, seguimientos] = await Promise.all([
        this.leadsService.obtener(this.leadId),
        this.leadsService.listarSeguimientos(this.leadId),
      ]);
      this.lead.set(lead);
      this.seguimientos.set(seguimientos);
    } catch {
      this.errorMessage.set('No se pudo cargar el lead.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cambiarEstado(nuevoEstado: EstadoLead): Promise<void> {
    const actual = this.lead();
    if (!actual || actual.estado === nuevoEstado) return;

    this.isSavingEstado.set(true);
    try {
      const actualizado = await this.leadsService.actualizar(this.leadId, {
        nombreCliente: actual.nombreCliente,
        telefono: actual.telefono,
        email: actual.email,
        origen: actual.origen,
        estado: nuevoEstado,
        valorEstimado: actual.valorEstimado,
      });
      this.lead.set(actualizado);
    } catch {
      this.errorMessage.set('No se pudo actualizar el estado.');
    } finally {
      this.isSavingEstado.set(false);
    }
  }

  async registrarSeguimiento(): Promise<void> {
    if (this.seguimientoForm.invalid || this.isSavingSeguimiento()) {
      this.seguimientoForm.markAllAsTouched();
      return;
    }

    this.isSavingSeguimiento.set(true);
    this.errorMessage.set(null);
    const v = this.seguimientoForm.getRawValue();

    try {
      const nuevo = await this.leadsService.crearSeguimiento(this.leadId, {
        tipo: v.tipo,
        nota: v.nota,
        resultado: v.resultado || null,
        proximoSeguimiento: v.proximoSeguimiento ? new Date(v.proximoSeguimiento).toISOString() : null,
      });
      this.seguimientos.update((lista) => [nuevo, ...lista]);
      this.seguimientoForm.reset({ tipo: 'LLAMADA', nota: '', resultado: '', proximoSeguimiento: '' });

      const leadActualizado = await this.leadsService.obtener(this.leadId);
      this.lead.set(leadActualizado);
    } catch {
      this.errorMessage.set('No se pudo registrar el seguimiento.');
    } finally {
      this.isSavingSeguimiento.set(false);
    }
  }
}
