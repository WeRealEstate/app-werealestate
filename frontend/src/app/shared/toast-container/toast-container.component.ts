import { Component, inject } from '@angular/core';
import { LucideCircleAlert, LucideCircleCheck, LucideX } from '@lucide/angular';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [LucideCircleCheck, LucideCircleAlert, LucideX],
  templateUrl: './toast-container.component.html',
})
export class ToastContainerComponent {
  readonly toastService = inject(ToastService);

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }
}
