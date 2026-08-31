import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { TareasService } from '../../../core/services/tareas.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { Tarea } from '../../../core/models/tarea.model';
import { UsuarioResumen } from '../../../core/models/lead.model';

@Component({
  selector: 'app-equipo-panel',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './equipo-panel.component.html',
})
export class EquipoPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly tareasService = inject(TareasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly user = this.auth.currentUser;
  readonly esLider = computed(() => this.user()?.rol === 'LIDER_AREA');

  readonly misTareas = signal<Tarea[]>([]);
  readonly tareasCreadas = signal<Tarea[]>([]);
  readonly usuariosAsignables = signal<UsuarioResumen[]>([]);
  readonly isLoading = signal(true);
  readonly savingTareaId = signal<number | null>(null);
  readonly isCreando = signal(false);

  readonly totalPendientes = computed(() => this.misTareas().filter((t) => !t.completada).length);
  readonly totalCompletadas = computed(() => this.misTareas().filter((t) => t.completada).length);

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
      const tareas = await this.tareasService.misTareas();
      this.misTareas.set(tareas);

      if (this.esLider()) {
        const [creadas, usuarios] = await Promise.all([
          this.tareasService.tareasCreadas(),
          this.usuariosService.asignables(),
        ]);
        this.tareasCreadas.set(creadas);
        this.usuariosAsignables.set(usuarios);
      }
    } catch {
      // El panel sigue siendo usable aunque falle la carga de tareas.
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleTarea(tarea: Tarea, lista: 'mias' | 'creadas'): Promise<void> {
    this.savingTareaId.set(tarea.id);
    try {
      const actualizada = await this.tareasService.cambiarEstado(tarea.id, !tarea.completada);
      const actualizar = (t: Tarea) => (t.id === actualizada.id ? actualizada : t);
      this.misTareas.update((l) => l.map(actualizar));
      this.tareasCreadas.update((l) => l.map(actualizar));
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
      if (nueva.asignadoA.id === this.user()?.id) {
        this.misTareas.update((lista) => [nueva, ...lista]);
      }
      this.form.reset({ titulo: '', descripcion: '', asignadoAId: null, fechaLimite: '' });
      this.toast.success(`Tarea asignada a ${nueva.asignadoA.nombre}.`);
    } catch {
      this.toast.error('No se pudo crear la tarea. Intenta de nuevo.');
    } finally {
      this.isCreando.set(false);
    }
  }
}
