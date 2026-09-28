import { Component, inject } from '@angular/core';
import { LucideMoon, LucideSun } from '@lucide/angular';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [LucideMoon, LucideSun],
  templateUrl: './theme-toggle.component.html',
})
export class ThemeToggleComponent {
  readonly themeService = inject(ThemeService);

  toggle(): void {
    this.themeService.toggle();
  }
}
