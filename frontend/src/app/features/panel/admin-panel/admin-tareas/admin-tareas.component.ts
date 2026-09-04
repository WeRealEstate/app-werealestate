import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TareasService } from '../../../../core/services/tareas.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ConfirmService } from '../../../../core/services/confirm.service';
import { Tarea } from '../../../../core/models/tarea.model';
import { UsuarioResumen } from '../../../../core/models/lead.model';

/**
 * Vista de administrador: TODAS las tareas del equipo (de cualquier líder de área o del propio
 * admin), con quién las creó, a quién están asignadas, si están completadas, y la posibilidad de
 * reasignarlas o borrarlas. Existe porque una tarea (aunque ya esté completada) bloquea el borrado
 * del usuario que la creó o de quien la tiene asignada — este es el lugar para destrabar eso.
 */
@Component({
  selector: 'app-admin-tareas',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './admin-tareas.component.html',
})
export class AdminTareasComponent {
  private readonly tareasService = inject(TareasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly tareas = signal<Tarea[]>([]);
  readonly usuariosAsignables = signal<UsuarioResumen[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly reasignandoTareaId = signal<number | null>(null);
  readonly eliminandoTareaId = signal<number | null>(null);

  readonly hayTareas = computed(() => this.tareas().length > 0);

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [tareas, usuarios] = await Promise.all([
        this.tareasService.listarTodas(),
        this.usuariosService.asignables(),
      ]);
      this.tareas.set(tareas);
      this.usuariosAsignables.set(usuarios);
    } catch {
      this.errorMessage.set('No se pudieron cargar las tareas del equipo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Las opciones del selector: el equipo interno activo, más el asignado actual si ya no está activo. */
  opcionesReasignar(tarea: Tarea): UsuarioResumen[] {
    const activos = this.usuariosAsignables();
    if (activos.some((u) => u.id === tarea.asignadoA.id)) return activos;
    return [tarea.asignadoA, ...activos];
  }

  async reasignar(tarea: Tarea, nuevoAsignadoAId: number): Promise<void> {
    if (nuevoAsignadoAId === tarea.asignadoA.id) return;

    this.reasignandoTareaId.set(tarea.id);
    try {
      const actualizada = await this.tareasService.reasignar(tarea.id, { nuevoAsignadoAId });
      this.tareas.update((lista) => lista.map((t) => (t.id === actualizada.id ? actualizada : t)));
      this.toast.success(`"${tarea.titulo}" reasignada a ${actualizada.asignadoA.nombre}.`);
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo reasignar la tarea.',
      );
    } finally {
      this.reasignandoTareaId.set(null);
    }
  }

  async eliminar(tarea: Tarea): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Borrar tarea',
      mensaje: `¿Borrar la tarea "${tarea.titulo}"? Esto no se puede deshacer.`,
      textoConfirmar: 'Borrar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.eliminandoTareaId.set(tarea.id);
    try {
      await this.tareasService.eliminar(tarea.id);
      this.tareas.update((lista) => lista.filter((t) => t.id !== tarea.id));
      this.toast.success('Tarea borrada.');
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo borrar la tarea.',
      );
    } finally {
      this.eliminandoTareaId.set(null);
    }
  }
}
