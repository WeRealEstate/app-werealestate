import { Component, inject } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-asesor-panel',
  standalone: true,
  templateUrl: './asesor-panel.component.html',
})
export class AsesorPanelComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
}
