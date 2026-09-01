import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { TareasService } from '../../../core/services/tareas.service';
import { AsignarTareasComponent } from '../../../shared/asignar-tareas/asignar-tareas.component';
import { Tarea } from '../../../core/models/tarea.model';

@Component({
  selector: 'app-equipo-panel',
  standalone: true,
  imports: [DatePipe, AsignarTareasComponent],
  templateUrl: './equipo-panel.component.html',
})
export class EquipoPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly tareasService = inject(TareasService);

  readonly user = this.auth.currentUser;
  readonly esLider = computed(() => this.user()?.rol === 'LIDER_AREA');

  readonly misTareas = signal<Tarea[]>([]);
  readonly isLoading = signal(true);
  readonly savingTareaId = signal<number | null>(null);

  readonly totalPendientes = computed(() => this.misTareas().filter((t) => !t.completada).length);
  readonly totalCompletadas = computed(() => this.misTareas().filter((t) => t.completada).length);

  constructor() {
    if (!this.esLider()) {
      this.cargar();
    } else {
      this.isLoading.set(false);
    }
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      this.misTareas.set(await this.tareasService.misTareas());
    } catch {
      // El panel sigue siendo usable aunque falle la carga de tareas.
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleTarea(tarea: Tarea): Promise<void> {
    this.savingTareaId.set(tarea.id);
    try {
      const actualizada = await this.tareasService.cambiarEstado(tarea.id, !tarea.completada);
      this.misTareas.update((lista) => lista.map((t) => (t.id === actualizada.id ? actualizada : t)));
    } finally {
      this.savingTareaId.set(null);
    }
  }
}
