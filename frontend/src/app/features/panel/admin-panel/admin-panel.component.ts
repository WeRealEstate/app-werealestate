import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './admin-panel.component.html',
})
export class AdminPanelComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
}
