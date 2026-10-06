import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Comision,
  ComisionDetalle,
  ComisionResumen,
  ComisionesPorEntregar,
  ESTADO_COMISION_CLASES,
  ESTADO_PAGO_CLIENTE_CLASES,
  ESTADO_PAGO_CLIENTE_LABELS,
  ESTADO_COMISION_LABELS,
  EstadoComision,
  MODALIDAD_COMISION_LABELS,
} from '../../../core/models/finanzas.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { FinanzasService } from '../../../core/services/finanzas.service';
import { ToastService } from '../../../core/services/toast.service';
import { FinanzasIngresosComponent } from './finanzas-ingresos.component';
import { FinanzasValorComponent } from './finanzas-valor.component';

const ESTADOS: EstadoComision[] = ['PENDIENTE', 'ACUMULANDO', 'PARCIAL', 'PAGADA', 'CANCELADA'];

/** Finanzas — por ahora, comisiones de venta: el 5% de cada venta (editable), lo que ya ganó cada
 * una con los abonos del cliente y lo que se entrega los sábados. Ver backend ComisionService. */
@Component({
  selector: 'app-finanzas',
  standalone: true,
  imports: [FormsModule, RouterLink, FinanzasIngresosComponent, FinanzasValorComponent],
  templateUrl: './finanzas.component.html',
})
export class FinanzasComponent {
  private readonly finanzasService = inject(FinanzasService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  /** Pestaña visible; las de ingresos y valor cargan sus datos solo al abrirse. */
  readonly pestana = signal<'comisiones' | 'ingresos' | 'valor'>('comisiones');

  readonly estados = ESTADOS;
  readonly estadoLabels = ESTADO_COMISION_LABELS;
  readonly estadoClases = ESTADO_COMISION_CLASES;
  readonly modalidadLabels = MODALIDAD_COMISION_LABELS;
  readonly pagoClienteLabels = ESTADO_PAGO_CLIENTE_LABELS;
  readonly pagoClienteClases = ESTADO_PAGO_CLIENTE_CLASES;

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly comisiones = signal<Comision[]>([]);
  readonly resumen = signal<ComisionResumen | null>(null);
  readonly porEntregar = signal<ComisionesPorEntregar | null>(null);

  /** 'RETRASADA' no es un estado: filtra las que tienen entregas vencidas por pagar. */
  readonly filtroEstado = signal<'' | EstadoComision | 'RETRASADA'>('');
  readonly busqueda = signal('');
  readonly comisionesFiltradas = computed(() => {
    const estado = this.filtroEstado();
    const texto = this.busqueda().trim().toLowerCase();
    return this.comisiones().filter((c) => {
      if (estado === 'RETRASADA' ? !c.retrasada : estado && c.estado !== estado) return false;
      if (!texto) return true;
      return (
        c.cliente.toLowerCase().includes(texto) ||
        c.asesorNombre.toLowerCase().includes(texto) ||
        String(c.ventaNumero ?? '').includes(texto)
      );
    });
  });

  // Detalle desplegable de una comisión (lo ganado por abono y las entregas hechas).
  readonly expandidaId = signal<number | null>(null);
  readonly detalle = signal<ComisionDetalle | null>(null);

  // Editar monto/porcentaje: cambiar uno recalcula el otro sobre el valor de la venta.
  readonly editando = signal<Comision | null>(null);
  readonly editPorcentaje = signal<number | null>(null);
  readonly editMonto = signal<number | null>(null);
  private editCampo: 'porcentaje' | 'monto' = 'porcentaje';

  // Entregar: monto (hasta lo ganado sin entregar), fecha y nota.
  readonly entregando = signal<Comision | null>(null);
  readonly entregaMonto = signal<number | null>(null);
  readonly entregaFecha = signal(hoyIso());
  readonly entregaNotas = signal('');
  readonly entregaMax = signal(0);

  readonly guardando = signal(false);
  readonly errorModal = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [comisiones, resumen, porEntregar] = await Promise.all([
        this.finanzasService.listarComisiones(),
        this.finanzasService.resumenComisiones(),
        this.finanzasService.comisionesPorEntregar(this.porEntregar()?.sabado),
      ]);
      this.comisiones.set(comisiones);
      this.resumen.set(resumen);
      this.porEntregar.set(porEntregar);
    } catch {
      this.errorMessage.set('No se pudieron cargar las comisiones. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Recarga lo que cambia al editar/entregar sin parpadeo de pantalla completa. */
  private async refrescar(): Promise<void> {
    try {
      const [comisiones, resumen, porEntregar] = await Promise.all([
        this.finanzasService.listarComisiones(),
        this.finanzasService.resumenComisiones(),
        this.finanzasService.comisionesPorEntregar(this.porEntregar()?.sabado),
      ]);
      this.comisiones.set(comisiones);
      this.resumen.set(resumen);
      this.porEntregar.set(porEntregar);
    } catch {
      this.toast.error('No se pudieron actualizar los totales. Recarga la página.');
    }
  }

  async cambiarSabado(dias: number): Promise<void> {
    const actual = this.porEntregar()?.sabado;
    if (!actual) return;
    const fecha = new Date(`${actual}T12:00:00`);
    fecha.setDate(fecha.getDate() + dias);
    try {
      this.porEntregar.set(await this.finanzasService.comisionesPorEntregar(fecha.toISOString().slice(0, 10)));
    } catch {
      this.toast.error('No se pudo cargar ese sábado.');
    }
  }

  // ---- Detalle ----

  async alternarDetalle(c: Comision): Promise<void> {
    if (this.expandidaId() === c.id) {
      this.expandidaId.set(null);
      this.detalle.set(null);
      return;
    }
    this.expandidaId.set(c.id);
    this.detalle.set(null);
    try {
      this.detalle.set(await this.finanzasService.detalleComision(c.id));
    } catch {
      this.toast.error('No se pudo cargar el detalle de la comisión.');
    }
  }

  // ---- Editar ----

  abrirEdicion(c: Comision): void {
    this.editando.set(c);
    this.editPorcentaje.set(c.porcentaje);
    this.editMonto.set(c.monto);
    this.editCampo = 'porcentaje';
    this.errorModal.set(null);
  }

  cerrarEdicion(): void {
    this.editando.set(null);
  }

  onPorcentajeChange(valor: number | null): void {
    this.editPorcentaje.set(valor);
    this.editCampo = 'porcentaje';
    const c = this.editando();
    if (c && valor !== null) this.editMonto.set(Math.round(c.base * valor) / 100);
  }

  onMontoChange(valor: number | null): void {
    this.editMonto.set(valor);
    this.editCampo = 'monto';
    const c = this.editando();
    if (c && valor !== null && c.base > 0) this.editPorcentaje.set(Math.round((valor / c.base) * 1_000_000) / 10_000);
  }

  async guardarEdicion(): Promise<void> {
    const c = this.editando();
    if (!c || this.guardando()) return;
    const porcentaje = this.editPorcentaje();
    const monto = this.editMonto();
    if (porcentaje === null || monto === null || porcentaje < 0 || porcentaje > 100 || monto < 0) {
      this.errorModal.set('Escribe un porcentaje entre 0 y 100 y un monto válido.');
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      // Se manda solo el campo que se tocó: el servidor calcula el otro con la misma base.
      const cambio = this.editCampo === 'monto' ? { monto } : { porcentaje };
      const actualizado = await this.finanzasService.editarComision(c.id, cambio);
      this.editando.set(null);
      this.toast.success('Comisión actualizada.');
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, 'No se pudo actualizar la comisión.'));
    } finally {
      this.guardando.set(false);
    }
  }

  // ---- Entregar ----

  abrirEntrega(c: Comision, sugerido?: number): void {
    this.entregando.set(c);
    this.entregaMax.set(c.porEntregar);
    this.entregaMonto.set(sugerido ?? c.porEntregar);
    this.entregaFecha.set(hoyIso());
    this.entregaNotas.set('');
    this.errorModal.set(null);
  }

  cerrarEntrega(): void {
    this.entregando.set(null);
  }

  async confirmarEntrega(): Promise<void> {
    const c = this.entregando();
    const monto = this.entregaMonto();
    if (!c || this.guardando()) return;
    if (monto === null || monto <= 0) {
      this.errorModal.set('Escribe el monto a entregar.');
      return;
    }
    if (monto > this.entregaMax()) {
      this.errorModal.set(`Solo hay $${dinero(this.entregaMax())} ganados por entregar: el cliente no ha pagado más.`);
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      const actualizado = await this.finanzasService.entregarComision(
        c.id,
        monto,
        this.entregaFecha() || null,
        this.entregaNotas().trim() || null,
      );
      this.entregando.set(null);
      this.toast.success('Entrega registrada y agregada a Gastos.');
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, 'No se pudo registrar la entrega.'));
    } finally {
      this.guardando.set(false);
    }
  }

  async anularEntrega(c: Comision, entregaId: number): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Anular entrega',
      mensaje: 'Se anula la entrega y también se elimina el gasto que generó. ¿Continuar?',
      textoConfirmar: 'Anular',
      peligroso: true,
    });
    if (!confirmado) return;
    try {
      const actualizado = await this.finanzasService.anularEntrega(c.id, entregaId);
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      this.toast.success('Entrega anulada.');
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo anular la entrega.'));
    }
  }

  async alternarCancelada(c: Comision): Promise<void> {
    const cancelar = !c.cancelada;
    const confirmado = await this.confirmService.confirm({
      titulo: cancelar ? 'Cancelar comisión' : 'Reactivar comisión',
      mensaje: cancelar
        ? `Se deja de acumular la comisión de ${c.cliente}. Lo ya ganado todavía se puede entregar.`
        : `La comisión de ${c.cliente} vuelve a acumular con los abonos del cliente.`,
      textoConfirmar: cancelar ? 'Cancelar comisión' : 'Reactivar',
      peligroso: cancelar,
    });
    if (!confirmado) return;
    try {
      const actualizado = cancelar
        ? await this.finanzasService.cancelarComision(c.id)
        : await this.finanzasService.reactivarComision(c.id);
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo cambiar la comisión.'));
    }
  }

  fecha(iso: string | null): string {
    if (!iso) return '—';
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }

  money(valor: number): string {
    return dinero(valor);
  }
}

function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dinero(valor: number): string {
  return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : porDefecto;
}
