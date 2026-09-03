import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import {
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Lead,
  Seguimiento,
  TIPO_SEGUIMIENTO_LABELS,
  TipoSeguimiento,
  UsuarioResumen,
} from '../../../core/models/lead.model';
import { Usuario } from '../../../core/models/user.model';
import { DURACION_OPCIONES, DURACION_POR_DEFECTO, HORA_POR_DEFECTO, combinarFechaHora } from '../../../core/utils/fecha-hora';

/** Roles que efectivamente trabajan leads y por lo tanto pueden recibir una reasignación. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './lead-detail.component.html',
})
export class LeadDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly tipoLabels = TIPO_SEGUIMIENTO_LABELS;
  readonly estados = Object.keys(ESTADO_LEAD_LABELS) as EstadoLead[];
  readonly tipos = Object.keys(TIPO_SEGUIMIENTO_LABELS) as TipoSeguimiento[];
  readonly duracionOpciones = DURACION_OPCIONES;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly lead = signal<Lead | null>(null);
  readonly seguimientos = signal<Seguimiento[]>([]);
  readonly asesores = signal<Usuario[]>([]);
  readonly isLoading = signal(true);
  readonly isSavingEstado = signal(false);
  readonly isSavingAsesor = signal(false);
  readonly isSavingSeguimiento = signal(false);
  readonly isArchivando = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Los asesores asignables, más el dueño actual del lead si por algún motivo no está en esa lista. */
  readonly opcionesAsesor = computed<UsuarioResumen[]>(() => {
    const lista: UsuarioResumen[] = this.asesores();
    const actual = this.lead()?.asesor;
    if (!actual || lista.some((a) => a.id === actual.id)) return lista;
    return [...lista, actual].sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  private leadId!: number;

  readonly seguimientoForm = this.fb.group({
    tipo: this.fb.control<TipoSeguimiento>('LLAMADA', { nonNullable: true, validators: [Validators.required] }),
    nota: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    resultado: this.fb.control('', { nonNullable: true }),
    proximoSeguimiento: this.fb.control('', { nonNullable: true }),
    horaSeguimiento: this.fb.control(HORA_POR_DEFECTO, { nonNullable: true }),
    duracionSeguimiento: this.fb.control(DURACION_POR_DEFECTO, { nonNullable: true }),
  });

  async ngOnInit(): Promise<void> {
    this.leadId = Number(this.route.snapshot.paramMap.get('id'));
    await this.cargar();

    if (this.esAdmin()) {
      const usuarios = await this.usuariosService.listar();
      this.asesores.set(usuarios.filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol)));
    }
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
        edad: actual.edad,
        pais: actual.pais,
        estadoRepublica: actual.estadoRepublica,
      });
      this.lead.set(actualizado);
      this.toast.success(`Estado actualizado a "${this.estadoLabels[nuevoEstado]}".`);
    } catch {
      this.errorMessage.set('No se pudo actualizar el estado.');
      this.toast.error('No se pudo actualizar el estado.');
    } finally {
      this.isSavingEstado.set(false);
    }
  }

  async reasignar(nuevoAsesorId: number): Promise<void> {
    const actual = this.lead();
    if (!actual || actual.asesor.id === nuevoAsesorId) return;

    this.isSavingAsesor.set(true);
    this.errorMessage.set(null);
    try {
      const actualizado = await this.leadsService.reasignar(this.leadId, nuevoAsesorId);
      this.lead.set(actualizado);
      this.toast.success(`Lead reasignado a ${actualizado.asesor.nombre}.`);
    } catch {
      this.errorMessage.set('No se pudo reasignar el lead.');
      this.toast.error('No se pudo reasignar el lead.');
    } finally {
      this.isSavingAsesor.set(false);
    }
  }

  async archivar(): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isArchivando()) return;
    if (
      !confirm(
        `¿Archivar a ${actual.nombreCliente}? Dejará de aparecer en la lista activa, pero se conserva como métrica y puedes desarchivarlo cuando quieras.`,
      )
    ) {
      return;
    }

    this.isArchivando.set(true);
    try {
      const actualizado = await this.leadsService.archivar(this.leadId);
      this.lead.set(actualizado);
      this.toast.success(`${actualizado.nombreCliente} fue archivado.`);
    } catch {
      this.toast.error('No se pudo archivar el lead.');
    } finally {
      this.isArchivando.set(false);
    }
  }

  async desarchivar(): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isArchivando()) return;

    this.isArchivando.set(true);
    try {
      const actualizado = await this.leadsService.desarchivar(this.leadId);
      this.lead.set(actualizado);
      this.toast.success(`${actualizado.nombreCliente} fue desarchivado.`);
    } catch {
      this.toast.error('No se pudo desarchivar el lead.');
    } finally {
      this.isArchivando.set(false);
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
        proximoSeguimiento: v.proximoSeguimiento ? combinarFechaHora(v.proximoSeguimiento, v.horaSeguimiento) : null,
        duracionMinutos: v.proximoSeguimiento ? v.duracionSeguimiento : null,
      });
      this.seguimientos.update((lista) => [nuevo, ...lista]);
      this.seguimientoForm.reset({
        tipo: 'LLAMADA',
        nota: '',
        resultado: '',
        proximoSeguimiento: '',
        horaSeguimiento: HORA_POR_DEFECTO,
        duracionSeguimiento: DURACION_POR_DEFECTO,
      });

      const leadActualizado = await this.leadsService.obtener(this.leadId);
      this.lead.set(leadActualizado);
      this.toast.success('Seguimiento registrado.');
    } catch {
      this.errorMessage.set('No se pudo registrar el seguimiento.');
      this.toast.error('No se pudo registrar el seguimiento.');
    } finally {
      this.isSavingSeguimiento.set(false);
    }
  }
}
