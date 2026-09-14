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
    if (n.eventoId !== null) return ['/panel/calendario'];
    return ['/panel/equipo'];
  }

  /** Al navegar desde una notificación, o al descartarla con la "X", se marca como leída de
   * verdad (queda registrada en el backend): no vuelve a aparecer, ni al recargar ni en otra
   * sesión, a menos que la condición que la generó cambie de verdad (ver NotificacionService). */
  alNavegar(index: number): void {
    this.close();
    this.marcarLeidaYQuitar(index);
  }

  eliminar(index: number, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.marcarLeidaYQuitar(index);
  }

  private marcarLeidaYQuitar(index: number): void {
    const n = this.notificaciones()[index];
    this.notificaciones.update((lista) => lista.filter((_, i) => i !== index));
    if (!n) return;

    const entidadId = n.leadId ?? n.tareaId ?? n.eventoId;
    if (entidadId === null) return;

    this.notificacionesService.marcarLeida(n.tipo, entidadId, n.firma).catch(() => {
      // Si falla, en el peor caso vuelve a aparecer la próxima vez que se recarguen las notificaciones.
    });
  }
}
