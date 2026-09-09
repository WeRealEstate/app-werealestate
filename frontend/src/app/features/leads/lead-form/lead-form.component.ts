import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo, ESTADOS_REPUBLICA, Lead, PAIS_LABELS, Pais } from '../../../core/models/lead.model';
import { Usuario } from '../../../core/models/user.model';

/** Roles que pueden recibir la asignación de un lead: quienes trabajan leads, más el admin,
 * que también tiene su propia bolsa de leads. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA', 'ADMIN']);

@Component({
  selector: 'app-lead-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './lead-form.component.html',
})
export class LeadFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  private leadOriginal: Lead | null = null;

  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly leadId = signal<number | null>(null);
  readonly modoEdicion = computed(() => this.leadId() !== null);
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly asesores = signal<Usuario[]>([]);
  readonly isLoading = signal(false);
  readonly isCargandoLead = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly paisLabels = PAIS_LABELS;
  readonly paisOptions: Pais[] = ['MEXICANO', 'EXTRANJERO'];
  readonly estadosRepublica = ESTADOS_REPUBLICA;

  readonly form = this.fb.group({
    nombreCliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    email: this.fb.control('', { nonNullable: true, validators: [Validators.email] }),
    origen: this.fb.control('', { nonNullable: true }),
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    valorEstimado: this.fb.control<number | null>(null),
    asesorId: this.fb.control<number | null>(null),
    edad: this.fb.control<number | null>(null),
    pais: this.fb.control<Pais>('MEXICANO', { nonNullable: true }),
    estadoRepublica: this.fb.control<string | null>(null),
  });

  async ngOnInit(): Promise<void> {
    this.form.controls.pais.valueChanges.subscribe((pais) => {
      if (pais === 'EXTRANJERO') {
        this.form.controls.estadoRepublica.setValue(null);
        this.form.controls.estadoRepublica.disable();
      } else {
        this.form.controls.estadoRepublica.enable();
      }
    });

    this.desarrollos.set(await this.leadsService.listarDesarrollos());

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.leadId.set(id);
      await this.cargarLeadParaEditar(id);
    } else if (this.esAdmin()) {
      this.form.controls.asesorId.addValidators(Validators.required);
    }

    if (this.esAdmin() && !this.modoEdicion()) {
      const usuarios = await this.usuariosService.listar();
      this.asesores.set(usuarios.filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol)));
    }
  }

  private async cargarLeadParaEditar(id: number): Promise<void> {
    this.isCargandoLead.set(true);
    this.errorMessage.set(null);
    try {
      const lead = await this.leadsService.obtener(id);
      this.leadOriginal = lead;
      this.form.patchValue({
        nombreCliente: lead.nombreCliente,
        telefono: lead.telefono,
        email: lead.email ?? '',
        origen: lead.origen ?? '',
        desarrolloId: lead.desarrollo.id,
        valorEstimado: lead.valorEstimado,
        edad: lead.edad,
        pais: lead.pais ?? 'MEXICANO',
        estadoRepublica: lead.estadoRepublica,
      });
    } catch {
      this.errorMessage.set('No se pudo cargar el lead a editar.');
    } finally {
      this.isCargandoLead.set(false);
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
    const id = this.leadId();

    try {
      if (id !== null) {
        const actualizado = await this.leadsService.actualizar(id, {
          nombreCliente: v.nombreCliente,
          telefono: v.telefono,
          email: v.email || null,
          origen: v.origen || null,
          desarrolloId: v.desarrolloId!,
          estado: this.leadOriginal!.estado,
          valorEstimado: v.valorEstimado,
          edad: v.edad,
          pais: v.pais,
          estadoRepublica: v.estadoRepublica,
        });
        this.toast.success('Lead actualizado.');
        await this.router.navigate(['/panel/leads', actualizado.id]);
      } else {
        const lead = await this.leadsService.crear({
          nombreCliente: v.nombreCliente,
          telefono: v.telefono,
          email: v.email || null,
          origen: v.origen || null,
          desarrolloId: v.desarrolloId!,
          valorEstimado: v.valorEstimado,
          asesorId: v.asesorId,
          edad: v.edad,
          pais: v.pais,
          estadoRepublica: v.estadoRepublica,
        });
        await this.router.navigate(['/panel/leads', lead.id]);
      }
    } catch (error) {
      this.errorMessage.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : `No se pudo ${id !== null ? 'actualizar' : 'crear'} el lead. Intenta de nuevo.`,
      );
    } finally {
      this.isLoading.set(false);
    }
  }
}
