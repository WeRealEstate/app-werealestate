import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-equipo-panel',
  standalone: true,
  templateUrl: './equipo-panel.component.html',
})
export class EquipoPanelComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
  readonly esLider = computed(() => this.user()?.rol === 'LIDER_AREA');
}
