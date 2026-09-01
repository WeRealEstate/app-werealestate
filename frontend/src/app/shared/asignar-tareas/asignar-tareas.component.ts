import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { TareasService } from '../../core/services/tareas.service';
import { ToastService } from '../../core/services/toast.service';
import { UsuariosService } from '../../core/services/usuarios.service';
import { Tarea } from '../../core/models/tarea.model';
import { UsuarioResumen } from '../../core/models/lead.model';

/**
 * Formulario "Asignar una tarea" + lista "Tareas que has asignado". Lo usan tanto el líder de
 * área como el admin (misma dinámica para ambos): las tareas solo se pueden asignar a equipo interno.
 */
@Component({
  selector: 'app-asignar-tareas',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './asignar-tareas.component.html',
})
export class AsignarTareasComponent {
  private readonly tareasService = inject(TareasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly tareasCreadas = signal<Tarea[]>([]);
  readonly usuariosAsignables = signal<UsuarioResumen[]>([]);
  readonly isLoading = signal(true);
  readonly savingTareaId = signal<number | null>(null);
  readonly isCreando = signal(false);

  readonly form = this.fb.group({
    titulo: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: this.fb.control('', { nonNullable: true }),
    asignadoAId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    fechaLimite: this.fb.control('', { nonNullable: true }),
  });

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      const [creadas, usuarios] = await Promise.all([
        this.tareasService.tareasCreadas(),
        this.usuariosService.asignables(),
      ]);
      this.tareasCreadas.set(creadas);
      this.usuariosAsignables.set(usuarios);
    } catch {
      // Sigue siendo usable aunque falle la carga.
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleTarea(tarea: Tarea): Promise<void> {
    this.savingTareaId.set(tarea.id);
    try {
      const actualizada = await this.tareasService.cambiarEstado(tarea.id, !tarea.completada);
      this.tareasCreadas.update((lista) => lista.map((t) => (t.id === actualizada.id ? actualizada : t)));
    } finally {
      this.savingTareaId.set(null);
    }
  }

  async crearTarea(): Promise<void> {
    if (this.form.invalid || this.isCreando()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isCreando.set(true);
    const v = this.form.getRawValue();
    try {
      const nueva = await this.tareasService.crear({
        titulo: v.titulo,
        descripcion: v.descripcion || null,
        asignadoAId: v.asignadoAId!,
        fechaLimite: v.fechaLimite || null,
      });
      this.tareasCreadas.update((lista) => [nueva, ...lista]);
      this.form.reset({ titulo: '', descripcion: '', asignadoAId: null, fechaLimite: '' });
      this.toast.success(`Tarea asignada a ${nueva.asignadoA.nombre}.`);
    } catch {
      this.toast.error('No se pudo crear la tarea. Intenta de nuevo.');
    } finally {
      this.isCreando.set(false);
    }
  }
}
