import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { AsesoresExternosService } from '../../../core/services/asesores-externos.service';
import { AsesorExterno } from '../../../core/models/asesor-externo.model';

@Component({
  selector: 'app-asesores-externos-list',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './asesores-externos-list.component.html',
})
export class AsesoresExternosListComponent {
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly asesores = signal<AsesorExterno[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly nuevoNombre = signal('');
  readonly isCreando = signal(false);

  readonly editandoId = signal<number | null>(null);
  readonly nombreEnEdicion = signal('');

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.asesores.set(await this.asesoresExternosService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar los asesores externos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async crear(): Promise<void> {
    const nombre = this.nuevoNombre().trim();
    if (!nombre || this.isCreando()) return;

    this.isCreando.set(true);
    try {
      const creado = await this.asesoresExternosService.crear({ nombre });
      this.asesores.update((lista) => [...lista, creado].sort((a, b) => a.nombre.localeCompare(b.nombre)));
      this.nuevoNombre.set('');
      this.toast.success(`${creado.nombre} fue agregado.`);
    } catch {
      this.toast.error('No se pudo agregar el asesor externo.');
    } finally {
      this.isCreando.set(false);
    }
  }

  async toggleActivo(asesor: AsesorExterno): Promise<void> {
    await this.guardar(asesor, { nombre: asesor.nombre, activo: !asesor.activo });
  }

  abrirEdicion(asesor: AsesorExterno): void {
    this.editandoId.set(asesor.id);
    this.nombreEnEdicion.set(asesor.nombre);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
  }

  async guardarNombre(asesor: AsesorExterno): Promise<void> {
    const nombre = this.nombreEnEdicion().trim();
    if (!nombre) return;
    await this.guardar(asesor, { nombre, activo: asesor.activo });
    this.editandoId.set(null);
  }

  private async guardar(asesor: AsesorExterno, cambios: { nombre: string; activo: boolean }): Promise<void> {
    this.savingId.set(asesor.id);
    try {
      const actualizado = await this.asesoresExternosService.actualizar(asesor.id, cambios);
      this.asesores.update((lista) => lista.map((a) => (a.id === actualizado.id ? actualizado : a)));
    } catch {
      this.toast.error('No se pudo actualizar el asesor externo.');
    } finally {
      this.savingId.set(null);
    }
  }

  async eliminar(asesor: AsesorExterno): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar asesor externo',
      mensaje: `¿Eliminar a ${asesor.nombre} definitivamente? Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.savingId.set(asesor.id);
    try {
      await this.asesoresExternosService.eliminar(asesor.id);
      this.asesores.update((lista) => lista.filter((a) => a.id !== asesor.id));
      this.toast.success(`${asesor.nombre} fue eliminado.`);
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar el asesor externo.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }
}
