import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { ESTADO_LEAD_LABELS, Lead } from '../../../core/models/lead.model';

@Component({
  selector: 'app-leads-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './leads-list.component.html',
})
export class LeadsListComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly leads = signal<Lead[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');

  readonly leadsFiltrados = computed(() => {
    const term = this.filtro().trim().toLowerCase();
    if (!term) return this.leads();
    return this.leads().filter(
      (l) =>
        l.nombreCliente.toLowerCase().includes(term) ||
        l.telefono.includes(term) ||
        l.desarrollo.nombre.toLowerCase().includes(term),
    );
  });

  readonly totalFrios = computed(() => this.leads().filter((l) => l.frio).length);

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.leads.set(await this.leadsService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar los leads. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  badgeClass(estado: Lead['estado']): string {
    switch (estado) {
      case 'NUEVO':
        return 'bg-we-blue-light/20 text-we-primary';
      case 'CONTACTADO':
        return 'bg-we-secondary/40 text-we-text/70';
      case 'INTERESADO':
        return 'bg-we-primary/15 text-we-primary';
      case 'CITA_AGENDADA':
        return 'bg-amber-100 text-amber-700';
      case 'NEGOCIACION':
        return 'bg-orange-100 text-orange-700';
      case 'CERRADO_GANADO':
        return 'bg-green-100 text-green-700';
      case 'CERRADO_PERDIDO':
        return 'bg-red-100 text-red-700';
    }
  }
}
