import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { Role } from '../../../core/models/user.model';

const ROLE_LABELS: Record<Role, string> = {
  ASESOR: 'Asesor',
  LIDER_AREA: 'Líder de área',
  EQUIPO_INTERNO: 'Equipo interno',
  ADMIN: 'Administrador',
};

@Component({
  selector: 'app-panel-layout',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './panel-layout.component.html',
})
export class PanelLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.currentUser;
  readonly roleLabel = computed(() => {
    const rol = this.user()?.rol;
    return rol ? ROLE_LABELS[rol] : '';
  });

  logout(): void {
    this.auth.logout();
  }
}
