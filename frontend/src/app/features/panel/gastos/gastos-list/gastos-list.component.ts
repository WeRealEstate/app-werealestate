import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideFileText, LucideTrash2 } from '@lucide/angular';
import { AuthService } from '../../../../core/services/auth.service';
import { ConfirmService } from '../../../../core/services/confirm.service';
import { GastosService } from '../../../../core/services/gastos.service';
import { TiposGastoService } from '../../../../core/services/tipos-gasto.service';
import { ToastService } from '../../../../core/services/toast.service';
import { Gasto, TipoGasto } from '../../../../core/models/gasto.model';

@Component({
  selector: 'app-gastos-list',
  standalone: true,
  imports: [FormsModule, RouterLink, LucideFileText, LucideTrash2],
  templateUrl: './gastos-list.component.html',
})
export class GastosListComponent {
  private readonly auth = inject(AuthService);
  private readonly gastosService = inject(GastosService);
  private readonly tiposGastoService = inject(TiposGastoService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  /** Gestionar tipos de gasto y eliminar un gasto ya registrado es exclusivo de admin (ver
   * GastoService/TipoGastoService); un líder de área solo puede listar y registrar. */
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly gastos = signal<Gasto[]>([]);
  readonly tiposActivos = signal<TipoGasto[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);
  readonly totalGastado = computed(() => this.gastos().reduce((suma, g) => suma + g.monto, 0));

  readonly mostrarModalCrear = signal(false);
  readonly nuevoTipoGastoId = signal<number | null>(null);
  readonly nuevaFecha = signal(new Date().toISOString().slice(0, 10));
  readonly nuevoMonto = signal<number | null>(null);
  readonly nuevoTicket = signal<File | null>(null);
  readonly isCreando = signal(false);
  readonly errorCreacion = signal<string | null>(null);

  readonly tipoSeleccionado = computed<TipoGasto | null>(
    () => this.tiposActivos().find((t) => t.id === this.nuevoTipoGastoId()) ?? null,
  );

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [gastos, tipos] = await Promise.all([this.gastosService.listar(), this.tiposGastoService.listarActivos()]);
      this.gastos.set(gastos);
      this.tiposActivos.set(tipos);
    } catch {
      this.errorMessage.set('No se pudieron cargar los gastos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  abrirModalCrear(): void {
    this.nuevoTipoGastoId.set(null);
    this.nuevaFecha.set(new Date().toISOString().slice(0, 10));
    this.nuevoMonto.set(null);
    this.nuevoTicket.set(null);
    this.errorCreacion.set(null);
    this.mostrarModalCrear.set(true);
  }

  cerrarModalCrear(): void {
    this.mostrarModalCrear.set(false);
  }

  onTicketChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.nuevoTicket.set(input.files?.[0] ?? null);
  }

  async crear(): Promise<void> {
    const tipoGastoId = this.nuevoTipoGastoId();
    const fecha = this.nuevaFecha();
    const monto = this.nuevoMonto();
    const tipo = this.tipoSeleccionado();
    if (!tipoGastoId || !fecha || !monto || monto <= 0 || this.isCreando()) {
      this.errorCreacion.set('Tipo, fecha y monto son obligatorios.');
      return;
    }
    if (tipo?.requiereTicket && !this.nuevoTicket()) {
      this.errorCreacion.set(`"${tipo.nombre}" exige subir un ticket/comprobante.`);
      return;
    }

    this.isCreando.set(true);
    this.errorCreacion.set(null);
    try {
      const creado = await this.gastosService.crear(tipoGastoId, fecha, monto, this.nuevoTicket());
      this.gastos.update((lista) => [creado, ...lista]);
      this.mostrarModalCrear.set(false);
      this.toast.success('Gasto registrado.');
    } catch (error) {
      this.errorCreacion.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar el gasto.',
      );
    } finally {
      this.isCreando.set(false);
    }
  }

  async verTicket(gasto: Gasto): Promise<void> {
    try {
      await this.gastosService.verTicket(gasto.id);
    } catch {
      this.toast.error('No se pudo abrir el ticket.');
    }
  }

  async eliminar(gasto: Gasto): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar gasto',
      mensaje: `¿Eliminar este gasto de ${gasto.tipoGasto.nombre} por $${this.money(gasto.monto)}? Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.savingId.set(gasto.id);
    try {
      await this.gastosService.eliminar(gasto.id);
      this.gastos.update((lista) => lista.filter((g) => g.id !== gasto.id));
      this.toast.success('Gasto eliminado.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar el gasto.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
