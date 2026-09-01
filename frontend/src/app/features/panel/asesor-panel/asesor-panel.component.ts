import { Component, computed, inject, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { NotificacionesService } from '../../../core/services/notificaciones.service';
import { ESTADO_LEAD_LABELS, EstadoLead, Lead } from '../../../core/models/lead.model';
import { Notificacion } from '../../../core/models/notificacion.model';

@Component({
  selector: 'app-asesor-panel',
  standalone: true,
  imports: [RouterLink, KeyValuePipe],
  templateUrl: './asesor-panel.component.html',
})
export class AsesorPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  private readonly notificacionesService = inject(NotificacionesService);

  readonly user = this.auth.currentUser;
  readonly estadoLabels = ESTADO_LEAD_LABELS;

  readonly leads = signal<Lead[]>([]);
  readonly pendientes = signal<Notificacion[]>([]);
  readonly isLoading = signal(true);

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
      const [leads, notificaciones] = await Promise.all([
        this.leadsService.listar(),
        this.notificacionesService.listar(),
      ]);
      this.leads.set(leads);
      this.pendientes.set(notificaciones.filter((n) => n.leadId !== null));
    } catch {
      // El resumen es informativo: si falla, la página sigue siendo usable.
    } finally {
      this.isLoading.set(false);
    }
  }
}
