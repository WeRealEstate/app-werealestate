import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucidePencil, LucideTrash2 } from '@lucide/angular';
import { ConfirmService } from '../../../../core/services/confirm.service';
import { TiposGastoService } from '../../../../core/services/tipos-gasto.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TipoGasto, TipoGastoUpdateRequest } from '../../../../core/models/gasto.model';

const ordenarPorNombre = (tipos: TipoGasto[]): TipoGasto[] =>
  [...tipos].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-MX', { sensitivity: 'base' }));

@Component({
  selector: 'app-tipos-gasto-list',
  standalone: true,
  imports: [FormsModule, RouterLink, LucidePencil, LucideTrash2],
  templateUrl: './tipos-gasto-list.component.html',
})
export class TiposGastoListComponent {
  private readonly tiposGastoService = inject(TiposGastoService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly tipos = signal<TipoGasto[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly mostrarModalCrear = signal(false);
  readonly nuevoNombre = signal('');
  readonly nuevoRequiereTicket = signal(false);
  readonly isCreando = signal(false);
  readonly errorCreacion = signal<string | null>(null);

  readonly editandoId = signal<number | null>(null);
  readonly nombreEnEdicion = signal('');

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.tipos.set(ordenarPorNombre(await this.tiposGastoService.listar()));
    } catch {
      this.errorMessage.set('No se pudieron cargar los tipos de gasto. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  abrirModalCrear(): void {
    this.nuevoNombre.set('');
    this.nuevoRequiereTicket.set(false);
    this.errorCreacion.set(null);
    this.mostrarModalCrear.set(true);
  }

  cerrarModalCrear(): void {
    this.mostrarModalCrear.set(false);
  }

  async crear(): Promise<void> {
    const nombre = this.nuevoNombre().trim();
    if (!nombre || this.isCreando()) {
      this.errorCreacion.set('El nombre es obligatorio.');
      return;
    }

    this.isCreando.set(true);
    this.errorCreacion.set(null);
    try {
      const creado = await this.tiposGastoService.crear({ nombre, requiereTicket: this.nuevoRequiereTicket() });
      this.tipos.update((lista) => ordenarPorNombre([...lista, creado]));
      this.mostrarModalCrear.set(false);
      this.toast.success(`${creado.nombre} fue agregado.`);
    } catch (error) {
      this.errorCreacion.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo agregar el tipo de gasto.',
      );
    } finally {
      this.isCreando.set(false);
    }
  }

  async toggleActivo(tipo: TipoGasto): Promise<void> {
    await this.guardar(tipo, { nombre: tipo.nombre, requiereTicket: tipo.requiereTicket, activo: !tipo.activo });
  }

  async toggleRequiereTicket(tipo: TipoGasto): Promise<void> {
    await this.guardar(tipo, { nombre: tipo.nombre, requiereTicket: !tipo.requiereTicket, activo: tipo.activo });
  }

  abrirEdicion(tipo: TipoGasto): void {
    this.editandoId.set(tipo.id);
    this.nombreEnEdicion.set(tipo.nombre);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
  }

  async guardarEdicion(tipo: TipoGasto): Promise<void> {
    const nombre = this.nombreEnEdicion().trim();
    if (!nombre) return;
    await this.guardar(tipo, { nombre, requiereTicket: tipo.requiereTicket, activo: tipo.activo });
    this.editandoId.set(null);
  }

  private async guardar(tipo: TipoGasto, cambios: TipoGastoUpdateRequest): Promise<void> {
    this.savingId.set(tipo.id);
    try {
      const actualizado = await this.tiposGastoService.actualizar(tipo.id, cambios);
      this.tipos.update((lista) => ordenarPorNombre(lista.map((t) => (t.id === actualizado.id ? actualizado : t))));
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo actualizar el tipo de gasto.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }

  async eliminar(tipo: TipoGasto): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar tipo de gasto',
      mensaje: `¿Eliminar "${tipo.nombre}" definitivamente? Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.savingId.set(tipo.id);
    try {
      await this.tiposGastoService.eliminar(tipo.id);
      this.tipos.update((lista) => lista.filter((t) => t.id !== tipo.id));
      this.toast.success(`${tipo.nombre} fue eliminado.`);
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar el tipo de gasto.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }
}
