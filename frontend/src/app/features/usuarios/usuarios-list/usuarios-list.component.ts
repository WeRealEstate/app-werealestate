import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
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

  readonly roles = ROLES;
  readonly roleLabels = ROLE_LABELS;
  readonly propioId = computed(() => this.auth.currentUser()?.id);

  readonly usuarios = signal<Usuario[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

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
    await this.guardar(usuario, { nombre: usuario.nombre, rol: nuevoRol, activo: usuario.activo });
  }

  async toggleActivo(usuario: Usuario): Promise<void> {
    await this.guardar(usuario, { nombre: usuario.nombre, rol: usuario.rol, activo: !usuario.activo });
  }

  private async guardar(usuario: Usuario, cambios: { nombre: string; rol: Role; activo: boolean }): Promise<void> {
    this.savingId.set(usuario.id);
    this.errorMessage.set(null);
    try {
      const actualizado = await this.usuariosService.actualizar(usuario.id, cambios);
      this.usuarios.update((lista) => lista.map((u) => (u.id === actualizado.id ? actualizado : u)));
    } catch {
      this.errorMessage.set('No se pudo actualizar el usuario. Intenta de nuevo.');
    } finally {
      this.savingId.set(null);
    }
  }
}
