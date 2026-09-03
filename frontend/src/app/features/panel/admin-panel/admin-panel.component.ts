import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { AsignarTareasComponent } from '../../../shared/asignar-tareas/asignar-tareas.component';
import { DesempenoGeneralComponent } from './desempeno-general/desempeno-general.component';
import { AdminTareasComponent } from './admin-tareas/admin-tareas.component';

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [RouterLink, AsignarTareasComponent, DesempenoGeneralComponent, AdminTareasComponent],
  templateUrl: './admin-panel.component.html',
})
export class AdminPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly leadsService = inject(LeadsService);
  readonly user = this.auth.currentUser;

  readonly totalFrios = signal<number | null>(null);

  constructor() {
    this.leadsService
      .listarFrios()
      .then((frios) => this.totalFrios.set(frios.length))
      .catch(() => this.totalFrios.set(null));
  }
}
