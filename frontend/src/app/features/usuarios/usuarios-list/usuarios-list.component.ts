import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ROLE_LABELS, Role, Usuario } from '../../../core/models/user.model';

const ROLES: Role[] = ['ASESOR', 'LIDER_AREA', 'EQUIPO_INTERNO', 'ADMIN'];

@Component({
  selector: 'app-usuarios-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './usuarios-list.component.html',
})
export class UsuariosListComponent {
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly roles = ROLES;
  readonly roleLabels = ROLE_LABELS;
  readonly propioId = computed(() => this.auth.currentUser()?.id);

  readonly usuarios = signal<Usuario[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly resetId = signal<number | null>(null);
  readonly nuevaPassword = signal('');
  readonly resetError = signal<string | null>(null);

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.usuarios.set(await this.usuariosService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar los usuarios. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cambiarRol(usuario: Usuario, nuevoRol: Role): Promise<void> {
    if (nuevoRol === usuario.rol) return;
    await this.guardar(
      usuario,
      { nombre: usuario.nombre, rol: nuevoRol, activo: usuario.activo },
      `Rol de ${usuario.nombre} actualizado a ${this.roleLabels[nuevoRol]}.`,
    );
  }

  async toggleActivo(usuario: Usuario): Promise<void> {
    const nuevoEstado = !usuario.activo;
    await this.guardar(
      usuario,
      { nombre: usuario.nombre, rol: usuario.rol, activo: nuevoEstado },
      `${usuario.nombre} ahora está ${nuevoEstado ? 'activo' : 'inactivo'}.`,
    );
  }

  private async guardar(
    usuario: Usuario,
    cambios: { nombre: string; rol: Role; activo: boolean },
    mensajeExito: string,
  ): Promise<void> {
    this.savingId.set(usuario.id);
    this.errorMessage.set(null);
    try {
      const actualizado = await this.usuariosService.actualizar(usuario.id, cambios);
      this.usuarios.update((lista) => lista.map((u) => (u.id === actualizado.id ? actualizado : u)));
      this.toast.success(mensajeExito);
    } catch {
      this.errorMessage.set('No se pudo actualizar el usuario. Intenta de nuevo.');
      this.toast.error('No se pudo actualizar el usuario.');
    } finally {
      this.savingId.set(null);
    }
  }

  abrirReset(usuario: Usuario): void {
    this.resetId.set(usuario.id);
    this.nuevaPassword.set('');
    this.resetError.set(null);
  }

  cancelarReset(): void {
    this.resetId.set(null);
    this.nuevaPassword.set('');
    this.resetError.set(null);
  }

  async confirmarReset(usuario: Usuario): Promise<void> {
    const password = this.nuevaPassword();
    if (password.length < 8) {
      this.resetError.set('Debe tener al menos 8 caracteres.');
      return;
    }

    this.savingId.set(usuario.id);
    this.resetError.set(null);
    try {
      await this.usuariosService.restablecerPassword(usuario.id, password);
      this.resetId.set(null);
      this.nuevaPassword.set('');
      this.toast.success(`Contraseña de ${usuario.nombre} actualizada.`);
    } catch {
      this.resetError.set('No se pudo restablecer la contraseña. Intenta de nuevo.');
      this.toast.error('No se pudo restablecer la contraseña.');
    } finally {
      this.savingId.set(null);
    }
  }
}
