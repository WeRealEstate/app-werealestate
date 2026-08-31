import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-asesor-panel',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './asesor-panel.component.html',
})
export class AsesorPanelComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
}
