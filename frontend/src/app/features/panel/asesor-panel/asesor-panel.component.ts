import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { NotificacionesService } from '../../../core/services/notificaciones.service';
import { TareasService } from '../../../core/services/tareas.service';
import { ESTADO_LEAD_LABELS, EstadoLead, Lead } from '../../../core/models/lead.model';
import { Notificacion } from '../../../core/models/notificacion.model';
import { Tarea } from '../../../core/models/tarea.model';

@Component({
  selector: 'app-asesor-panel',
  standalone: true,
  imports: [RouterLink, KeyValuePipe, DatePipe],
  templateUrl: './asesor-panel.component.html',
})
export class AsesorPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  private readonly notificacionesService = inject(NotificacionesService);
  private readonly tareasService = inject(TareasService);

  readonly user = this.auth.currentUser;
  readonly estadoLabels = ESTADO_LEAD_LABELS;

  readonly leads = signal<Lead[]>([]);
  readonly pendientes = signal<Notificacion[]>([]);
  readonly tareas = signal<Tarea[]>([]);
  readonly isLoading = signal(true);
  readonly savingTareaId = signal<number | null>(null);

  readonly totalLeads = computed(() => this.leads().length);
  readonly totalFrios = computed(() => this.leads().filter((l) => l.frio).length);
  readonly porEstado = computed(() => {
    const conteo = {} as Record<EstadoLead, number>;
    for (const l of this.leads()) conteo[l.estado] = (conteo[l.estado] ?? 0) + 1;
    return conteo;
  });

  contarEstado(estado: string): number {
    return this.porEstado()[estado as EstadoLead] ?? 0;
  }

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      const [leads, notificaciones, tareas] = await Promise.all([
        this.leadsService.listar(),
        this.notificacionesService.listar(),
        this.tareasService.misTareas(),
      ]);
      this.leads.set(leads);
      this.pendientes.set(notificaciones.filter((n) => n.leadId !== null));
      this.tareas.set(tareas);
    } catch {
      // El resumen es informativo: si falla, la página sigue siendo usable.
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleTarea(tarea: Tarea): Promise<void> {
    this.savingTareaId.set(tarea.id);
    try {
      const actualizada = await this.tareasService.cambiarEstado(tarea.id, !tarea.completada);
      this.tareas.update((lista) => lista.map((t) => (t.id === actualizada.id ? actualizada : t)));
    } finally {
      this.savingTareaId.set(null);
    }
  }
}
