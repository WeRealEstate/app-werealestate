import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { NotificacionesService } from '../../core/services/notificaciones.service';
import { ToastService } from '../../core/services/toast.service';
import { Notificacion } from '../../core/models/notificacion.model';

/** Cada cuánto se refresca en segundo plano mientras el usuario sigue en el panel, además de al
 * navegar (ver constructor): sin esto, la campana se queda con el conteo de la primera carga
 * hasta que se refresca la página a mano, aunque el usuario resuelva pendientes mientras tanto. */
const INTERVALO_REFRESCO_MS = 60_000;

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  private readonly notificacionesService = inject(NotificacionesService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly notificaciones = signal<Notificacion[]>([]);
  readonly isOpen = signal(false);
  readonly total = computed(() => this.notificaciones().length);

  constructor() {
    // Primera carga: sí avisa con un toast si hay pendientes. El componente vive dentro del
    // layout del panel y Angular no lo destruye/reconstruye al navegar entre rutas hijas, así que
    // sin esto el contador quedaría congelado desde el primer login hasta un refresh manual.
    this.cargar(true);

    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.cargar(false));

    const intervalo = setInterval(() => this.cargar(false), INTERVALO_REFRESCO_MS);
    this.destroyRef.onDestroy(() => clearInterval(intervalo));
  }

  /** mostrarAviso solo va en true en la primera carga: en los refrescos silenciosos (al navegar o
   * por el intervalo) mostrar el mismo toast una y otra vez sería más molesto que útil. */
  private async cargar(mostrarAviso: boolean): Promise<void> {
    try {
      const lista = await this.notificacionesService.listar();
      this.notificaciones.set(lista);
      if (mostrarAviso && lista.length > 0) {
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
