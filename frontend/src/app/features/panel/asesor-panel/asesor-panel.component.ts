import { Component, computed, inject, signal } from '@angular/core';
import { LucideUserPlus, LucideUsers } from '@lucide/angular';
import { KeyValuePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { NotificacionesService } from '../../../core/services/notificaciones.service';
import { ESTADO_LEAD_LABELS, EstadoLead, ResumenLeads } from '../../../core/models/lead.model';
import { AsignarTareasComponent } from '../../../shared/asignar-tareas/asignar-tareas.component';

@Component({
  selector: 'app-asesor-panel',
  standalone: true,
  imports: [RouterLink, KeyValuePipe, AsignarTareasComponent, LucideUsers, LucideUserPlus],
  templateUrl: './asesor-panel.component.html',
})
export class AsesorPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  private readonly notificacionesService = inject(NotificacionesService);

  readonly user = this.auth.currentUser;
  readonly estadoLabels = ESTADO_LEAD_LABELS;

  /** Solo los conteos (GET /api/leads/resumen): antes se descargaban todos sus leads para mostrar tres números. */
  readonly resumen = signal<ResumenLeads | null>(null);
  /** Las mismas notificaciones que la campana (una sola petición para las dos), solo las de leads. */
  readonly pendientes = computed(() => this.notificacionesService.lista().filter((n) => n.leadId !== null));
  readonly isLoading = signal(true);

  readonly totalLeads = computed(() => this.resumen()?.total ?? 0);
  readonly totalFrios = computed(() => this.resumen()?.frios ?? 0);

  contarEstado(estado: string): number {
    return this.resumen()?.porEstado[estado as EstadoLead] ?? 0;
  }

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      // Sin Leads ni Pipeline (el admin se los quitó) la API de leads respondería 403: no se pide.
      const verLeads = this.auth.tieneAlgunModulo(['LEADS', 'PIPELINE']);
      // La campana pide las notificaciones al mismo tiempo: refrescar() comparte esa petición en vez de repetirla.
      const [resumen] = await Promise.all([
        verLeads ? this.leadsService.resumen() : Promise.resolve(null),
        this.notificacionesService.refrescar(30_000),
      ]);
      this.resumen.set(resumen);
    } catch {
      // El resumen es informativo: si falla, la página sigue siendo usable.
    } finally {
      this.isLoading.set(false);
    }
  }
}
