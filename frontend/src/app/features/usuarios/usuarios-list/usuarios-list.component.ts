import { Component, computed, inject, signal } from '@angular/core';
import { LucideKey, LucideTrash2 } from '@lucide/angular';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ModulosSelectorComponent } from '../../../shared/modulos-selector/modulos-selector.component';
import { Modulo, ROLE_LABELS, Role, Usuario } from '../../../core/models/user.model';

const ROLES: Role[] = ['ASESOR', 'LIDER_AREA', 'EQUIPO_INTERNO', 'ADMIN'];

@Component({
  selector: 'app-usuarios-list',
  standalone: true,
  imports: [RouterLink, LucideKey, LucideTrash2, ModulosSelectorComponent],
  templateUrl: './usuarios-list.component.html',
})
export class UsuariosListComponent {
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly roles = ROLES;
  readonly roleLabels = ROLE_LABELS;
  readonly propioId = computed(() => this.auth.currentUser()?.id);

  readonly usuarios = signal<Usuario[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  /** El error de "tiene actividad registrada" al borrar se resuelve borrando esa tarea en
   * Inicio → "Todas las tareas del equipo", pero no queda obvio desde acá si no lo señalamos. */
  readonly errorEsPorTareaBloqueando = computed(
    () => this.errorMessage()?.includes('tiene actividad registrada') ?? false,
  );

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
      // Al cambiar de rol, sus módulos se reinician a los de ese rol (modulos: null).
      { nombre: usuario.nombre, rol: nuevoRol, activo: usuario.activo, modulos: null },
      `Rol de ${usuario.nombre} actualizado a ${this.roleLabels[nuevoRol]}.`,
    );
  }

  async eliminar(usuario: Usuario): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar usuario',
      mensaje: `¿Eliminar a ${usuario.nombre} definitivamente? Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.savingId.set(usuario.id);
    this.errorMessage.set(null);
    try {
      await this.usuariosService.eliminar(usuario.id);
      this.usuarios.update((lista) => lista.filter((u) => u.id !== usuario.id));
      this.toast.success(`${usuario.nombre} fue eliminado.`);
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar el usuario.';
      this.errorMessage.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }

  async toggleActivo(usuario: Usuario): Promise<void> {
    const nuevoEstado = !usuario.activo;
    await this.guardar(
      usuario,
      { nombre: usuario.nombre, rol: usuario.rol, activo: nuevoEstado, modulos: usuario.rol === 'ADMIN' ? null : usuario.modulos },
      `${usuario.nombre} ahora está ${nuevoEstado ? 'activo' : 'inactivo'}.`,
    );
  }

  private async guardar(
    usuario: Usuario,
    cambios: { nombre: string; rol: Role; activo: boolean; modulos: Modulo[] | null },
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

  // --- Módulos de un usuario: se editan en un modal aparte con casillas ---
  readonly editandoModulosDe = signal<Usuario | null>(null);
  readonly modulosEnEdicion = signal<Modulo[]>([]);

  abrirModulos(usuario: Usuario): void {
    this.modulosEnEdicion.set([...usuario.modulos]);
    this.editandoModulosDe.set(usuario);
  }

  cerrarModulos(): void {
    this.editandoModulosDe.set(null);
  }

  async guardarModulos(): Promise<void> {
    const usuario = this.editandoModulosDe();
    if (!usuario) return;
    await this.guardar(
      usuario,
      { nombre: usuario.nombre, rol: usuario.rol, activo: usuario.activo, modulos: this.modulosEnEdicion() },
      `Módulos de ${usuario.nombre} actualizados.`,
    );
    this.editandoModulosDe.set(null);
  }

  resumenModulos(usuario: Usuario): string {
    if (usuario.rol === 'ADMIN') return 'Todos';
    const n = usuario.modulos.length;
    return n === 0 ? 'Ninguno' : `${n} módulo${n === 1 ? '' : 's'}`;
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
