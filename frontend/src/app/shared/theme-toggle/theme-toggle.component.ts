import { Component, inject } from '@angular/core';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  templateUrl: './theme-toggle.component.html',
})
export class ThemeToggleComponent {
  readonly themeService = inject(ThemeService);

  toggle(): void {
    this.themeService.toggle();
  }
}
