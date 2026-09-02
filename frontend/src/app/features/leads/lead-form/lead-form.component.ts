import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { Desarrollo, ESTADOS_REPUBLICA, PAIS_LABELS, Pais } from '../../../core/models/lead.model';
import { Usuario } from '../../../core/models/user.model';

/** Roles que efectivamente trabajan leads y por lo tanto pueden recibir la asignación. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

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

  /** Si se llega desde el tablero de tarjetas, vuelve ahí en vez de al detalle del lead nuevo. */
  readonly returnTo = this.route.snapshot.queryParamMap.get('returnTo');

  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly asesores = signal<Usuario[]>([]);
  readonly isLoading = signal(false);
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
    this.desarrollos.set(await this.leadsService.listarDesarrollos());

    if (this.esAdmin()) {
      this.form.controls.asesorId.addValidators(Validators.required);
      const usuarios = await this.usuariosService.listar();
      const asignables = usuarios.filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol));

      const asesorIdParam = this.route.snapshot.queryParamMap.get('asesorId');
      if (asesorIdParam) {
        const id = +asesorIdParam;
        this.form.controls.asesorId.setValue(id);
        // Si viene del propio tablero del admin, agrégalo a las opciones (no es un asesor "asignable" normal).
        const actual = this.auth.currentUser();
        if (actual && actual.id === id && !asignables.some((a) => a.id === id)) {
          asignables.push({ ...actual, activo: true });
        }
      }

      this.asesores.set(asignables);
    }

    this.form.controls.pais.valueChanges.subscribe((pais) => {
      if (pais === 'EXTRANJERO') {
        this.form.controls.estadoRepublica.setValue(null);
        this.form.controls.estadoRepublica.disable();
      } else {
        this.form.controls.estadoRepublica.enable();
      }
    });
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
      if (this.returnTo) {
        await this.router.navigateByUrl(this.returnTo);
      } else {
        await this.router.navigate(['/panel/leads', lead.id]);
      }
    } catch (error) {
      this.errorMessage.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear el lead. Intenta de nuevo.',
      );
    } finally {
      this.isLoading.set(false);
    }
  }
}
