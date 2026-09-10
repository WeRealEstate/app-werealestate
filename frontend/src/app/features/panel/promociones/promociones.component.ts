import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PromocionesService } from '../../../core/services/promociones.service';
import { Promocion, ProyectoPromocion } from '../../../core/models/promocion.model';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';

@Component({
  selector: 'app-promociones',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './promociones.component.html',
})
export class PromocionesComponent {
  private readonly promocionesService = inject(PromocionesService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly promociones = signal<Promocion[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly isSaving = signal(false);

  /** Cuando no es null, el formulario edita esta promoción en vez de crear una nueva. */
  readonly editandoId = signal<number | null>(null);

  readonly nombre = signal('');
  readonly proyecto = signal<ProyectoPromocion>('samai');
  readonly mensualidadFija = signal(0);
  readonly mensualidadDisplay = signal('');
  readonly descripcion = signal('');

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.promociones.set(await this.promocionesService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar las promociones.');
    } finally {
      this.isLoading.set(false);
    }
  }

  onMensualidadInput(event: Event): void {
    const rawValue = (event.target as HTMLInputElement).value.replace(/\D/g, '');
    if (!rawValue) {
      this.mensualidadFija.set(0);
      this.mensualidadDisplay.set('');
      return;
    }
    this.mensualidadFija.set(Number(rawValue));
    this.mensualidadDisplay.set(Number(rawValue).toLocaleString('en-US'));
  }

  editar(promo: Promocion): void {
    this.editandoId.set(promo.id);
    this.nombre.set(promo.nombre);
    this.proyecto.set(promo.proyecto);
    this.mensualidadFija.set(promo.mensualidadFija);
    this.mensualidadDisplay.set(promo.mensualidadFija.toLocaleString('en-US'));
    this.descripcion.set(promo.descripcion ?? '');
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.proyecto.set('samai');
    this.mensualidadFija.set(0);
    this.mensualidadDisplay.set('');
    this.descripcion.set('');
  }

  async guardar(): Promise<void> {
    const nombre = this.nombre().trim();
    const monto = this.mensualidadFija();
    if (!nombre || monto <= 0 || this.isSaving()) return;

    this.isSaving.set(true);
    try {
      const id = this.editandoId();
      if (id !== null) {
        const actualizada = await this.promocionesService.actualizar(id, {
          nombre,
          mensualidadFija: monto,
          descripcion: this.descripcion().trim() || null,
        });
        this.promociones.update((lista) => lista.map((p) => (p.id === id ? actualizada : p)));
        this.toast.success('Promoción actualizada.');
      } else {
        const nueva = await this.promocionesService.crear({
          nombre,
          proyecto: this.proyecto(),
          mensualidadFija: monto,
          descripcion: this.descripcion().trim() || null,
        });
        // Crear una promoción activa desactiva cualquier otra del mismo proyecto (lo hace el backend).
        this.promociones.update((lista) => [
          nueva,
          ...lista.map((p) => (p.proyecto === nueva.proyecto ? { ...p, activa: false } : p)),
        ]);
        this.toast.success(`Promoción "${nueva.nombre}" creada.`);
      }
      this.cancelarEdicion();
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo guardar la promoción.';
      this.toast.error(mensaje);
    } finally {
      this.isSaving.set(false);
    }
  }

  async toggleActiva(promo: Promocion): Promise<void> {
    try {
      const actualizada = await this.promocionesService.cambiarEstado(promo.id, !promo.activa);
      this.promociones.update((lista) =>
        lista.map((p) => {
          if (p.id === actualizada.id) return actualizada;
          if (actualizada.activa && p.proyecto === actualizada.proyecto) return { ...p, activa: false };
          return p;
        }),
      );
      this.toast.success(
        actualizada.activa ? `"${actualizada.nombre}" ahora está activa.` : `"${actualizada.nombre}" fue desactivada.`,
      );
    } catch {
      this.toast.error('No se pudo cambiar el estado de la promoción.');
    }
  }

  async eliminar(promo: Promocion): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar promoción',
      mensaje: `¿Eliminar la promoción "${promo.nombre}"? Esto no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    try {
      await this.promocionesService.eliminar(promo.id);
      this.promociones.update((lista) => lista.filter((p) => p.id !== promo.id));
      this.toast.success(`Promoción "${promo.nombre}" eliminada.`);
    } catch {
      this.toast.error('No se pudo eliminar la promoción.');
    }
  }
}
