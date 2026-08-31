import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificacionesService } from '../../core/services/notificaciones.service';
import { ToastService } from '../../core/services/toast.service';
import { Notificacion } from '../../core/models/notificacion.model';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  private readonly notificacionesService = inject(NotificacionesService);
  private readonly toast = inject(ToastService);

  readonly notificaciones = signal<Notificacion[]>([]);
  readonly isOpen = signal(false);
  readonly total = computed(() => this.notificaciones().length);

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      const lista = await this.notificacionesService.listar();
      this.notificaciones.set(lista);
      if (lista.length > 0) {
        this.toast.success(
          `Tienes ${lista.length} pendiente${lista.length === 1 ? '' : 's'}: revisa la campana de notificaciones.`,
        );
      }
    } catch {
      // Las notificaciones son informativas: si fallan, simplemente no se muestran.
    }
  }

  toggle(): void {
    this.isOpen.update((v) => !v);
  }

  close(): void {
    this.isOpen.set(false);
  }

  rutaDeNotificacion(n: Notificacion): string[] {
    if (n.leadId !== null) return ['/panel/leads', String(n.leadId)];
    return ['/panel/equipo'];
  }
}
