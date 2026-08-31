import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ROLE_LABELS, Role } from '../../../core/models/user.model';

const ROLES: Role[] = ['ASESOR', 'LIDER_AREA', 'EQUIPO_INTERNO', 'ADMIN'];

@Component({
  selector: 'app-usuario-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './usuario-form.component.html',
})
export class UsuarioFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly usuariosService = inject(UsuariosService);
  private readonly router = inject(Router);

  readonly roles = ROLES;
  readonly roleLabels = ROLE_LABELS;
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    nombre: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    email: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] }),
    rol: this.fb.control<Role>('ASESOR', { nonNullable: true, validators: [Validators.required] }),
  });

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();

    try {
      await this.usuariosService.crear(v);
      await this.router.navigate(['/panel/usuarios']);
    } catch (err: unknown) {
      const status = (err as { status?: number })?.status;
      this.errorMessage.set(
        status === 409 ? 'Ya existe un usuario con ese correo.' : 'No se pudo crear el usuario. Intenta de nuevo.',
      );
    } finally {
      this.isLoading.set(false);
    }
  }
}
