import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideFileText, LucidePencil, LucideTrash2 } from '@lucide/angular';
import {
  FRECUENCIAS_GASTO,
  FRECUENCIA_GASTO_LABELS,
  FrecuenciaGasto,
  Gasto,
  GastoRecurrente,
  GastoRecurrentePago,
  GastoResumen,
  ORIGEN_GASTO_CLASES,
  ORIGEN_GASTO_LABELS,
} from '../../../../core/models/gasto.model';
import { AuthService } from '../../../../core/services/auth.service';
import { ConfirmService } from '../../../../core/services/confirm.service';
import { GastosService } from '../../../../core/services/gastos.service';
import { ToastService } from '../../../../core/services/toast.service';
import { SkeletonKpisComponent } from '../../../../shared/skeleton-kpis/skeleton-kpis.component';
import { MonedaInputDirective } from '../../../../shared/moneda-input/moneda-input.directive';

/** Pestaña "Gastos" de Finanzas: resumen del mes, próximos pagos de los gastos recurrentes (renta,
 * luz, nómina...), el catálogo de recurrentes y el historial de todo lo gastado. "Registrar gasto"
 * es para compras de una sola vez (papelería, insumos). Ver backend GastoRecurrenteService. */
@Component({
  selector: 'app-gastos-list',
  standalone: true,
  imports: [MonedaInputDirective, SkeletonKpisComponent, FormsModule, LucideFileText, LucidePencil, LucideTrash2],
  templateUrl: './gastos-list.component.html',
})
export class GastosListComponent {
  private readonly auth = inject(AuthService);
  private readonly gastosService = inject(GastosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  /** Crear/editar/eliminar gastos recurrentes es solo de admin (ver GastoRecurrenteService); Administración registra
   * gastos, marca pagos y, como Admin, modifica y elimina egresos (eliminar pide su contraseña, ver GastoService). */
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly frecuencias = FRECUENCIAS_GASTO;
  readonly frecuenciaLabels = FRECUENCIA_GASTO_LABELS;
  readonly origenLabels = ORIGEN_GASTO_LABELS;
  readonly origenClases = ORIGEN_GASTO_CLASES;

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly gastos = signal<Gasto[]>([]);
  readonly recurrentes = signal<GastoRecurrente[]>([]);
  readonly sinPagar = signal<GastoRecurrentePago[]>([]);
  readonly resumen = signal<GastoResumen | null>(null);

  /** Mes ("yyyy-MM") del resumen y del historial; vacío = todo el historial. */
  readonly mes = signal(mesActual());
  readonly gastosDelMes = computed(() => {
    const mes = this.mes();
    return mes ? this.gastos().filter((g) => g.fecha.startsWith(mes)) : this.gastos();
  });
  /** Sábados con nómina por pagar (ya vencidos o el de esta semana), con cuántas personas y cuánto:
   * para el botón "Pagar nómina del sábado". Los sábados más lejanos se pagan cuando lleguen. */
  readonly sabadosNomina = computed(() => {
    const tope = proximoSabadoIso();
    const porFecha = new Map<string, { fecha: string; personas: number; total: number }>();
    for (const p of this.sinPagar()) {
      if (!p.esNomina || p.fechaVencimiento > tope) continue;
      const fila = porFecha.get(p.fechaVencimiento) ?? { fecha: p.fechaVencimiento, personas: 0, total: 0 };
      fila.personas++;
      fila.total += p.montoEstimado;
      porFecha.set(p.fechaVencimiento, fila);
    }
    return [...porFecha.values()].sort((a, b) => a.fecha.localeCompare(b.fecha));
  });
  readonly totalHistorial = computed(() => this.gastosDelMes().reduce((suma, g) => suma + g.monto, 0));

  // ---- Registrar gasto (una sola vez) ----
  readonly mostrarModalGasto = signal(false);
  readonly nuevoConcepto = signal('');
  readonly nuevaFecha = signal(hoyIso());
  readonly nuevoMonto = signal<number | null>(null);
  readonly nuevoTicket = signal<File | null>(null);
  /** Egreso que se está corrigiendo (null = el modal registra uno nuevo). */
  readonly editandoGasto = signal<Gasto | null>(null);
  readonly quitarTicketActual = signal(false);

  // ---- Eliminar un egreso (con contraseña) ----
  readonly eliminando = signal<Gasto | null>(null);
  readonly passwordEliminar = signal('');
  readonly errorEliminar = signal<string | null>(null);
  readonly eliminandoEnCurso = signal(false);

  // ---- Pagar un vencimiento ----
  readonly pagando = signal<GastoRecurrentePago | null>(null);
  readonly pagoMonto = signal<number | null>(null);
  readonly pagoFecha = signal(hoyIso());
  readonly pagoTicket = signal<File | null>(null);

  // ---- Crear / editar un gasto recurrente ----
  readonly mostrarModalRecurrente = signal(false);
  readonly editandoRecurrente = signal<GastoRecurrente | null>(null);
  readonly recNombre = signal('');
  readonly recMonto = signal<number | null>(null);
  readonly recFrecuencia = signal<FrecuenciaGasto>('MENSUAL');
  readonly recDia = signal<number | null>(1);
  readonly recDia2 = signal<number | null>(15);
  readonly recPrimer = signal(hoyIso());
  readonly recActivo = signal(true);

  readonly guardando = signal(false);
  readonly errorModal = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      await this.refrescar();
    } catch {
      this.errorMessage.set('No se pudieron cargar los gastos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async refrescar(): Promise<void> {
    const [gastos, recurrentes, sinPagar, resumen] = await Promise.all([
      this.gastosService.listar(),
      this.gastosService.listarRecurrentes(),
      this.gastosService.pagosSinPagar(),
      this.gastosService.resumen(this.mes() || null),
    ]);
    this.gastos.set(gastos);
    this.recurrentes.set(recurrentes);
    this.sinPagar.set(sinPagar);
    this.resumen.set(resumen);
  }

  async cambiarMes(valor: string): Promise<void> {
    this.mes.set(valor);
    try {
      this.resumen.set(await this.gastosService.resumen(valor || null));
    } catch {
      this.toast.error('No se pudo actualizar el resumen del mes.');
    }
  }

  // ---------------------------------------------------------------- Registrar gasto único

  abrirModalGasto(): void {
    this.editandoGasto.set(null);
    this.quitarTicketActual.set(false);
    this.nuevoConcepto.set('');
    this.nuevaFecha.set(hoyIso());
    this.nuevoMonto.set(null);
    this.nuevoTicket.set(null);
    this.errorModal.set(null);
    this.mostrarModalGasto.set(true);
  }

  /** Corrige un egreso ya registrado (de cualquier origen: único, recurrente o entrega de comisión). */
  abrirEditarGasto(gasto: Gasto): void {
    this.editandoGasto.set(gasto);
    this.quitarTicketActual.set(false);
    this.nuevoConcepto.set(gasto.concepto);
    this.nuevaFecha.set(gasto.fecha.slice(0, 10));
    this.nuevoMonto.set(gasto.monto);
    this.nuevoTicket.set(null);
    this.errorModal.set(null);
    this.mostrarModalGasto.set(true);
  }

  async crearGasto(): Promise<void> {
    const concepto = this.nuevoConcepto().trim();
    const monto = this.nuevoMonto();
    if (!concepto || !this.nuevaFecha() || !monto || monto <= 0) {
      this.errorModal.set('Concepto, fecha y monto son obligatorios.');
      return;
    }
    const editando = this.editandoGasto();
    await this.guardar(async () => {
      if (editando) {
        await this.gastosService.actualizar(
          editando.id, concepto, this.nuevaFecha(), monto, this.nuevoTicket(), this.quitarTicketActual(),
        );
        this.toast.success('Egreso actualizado.');
      } else {
        await this.gastosService.crear(concepto, this.nuevaFecha(), monto, this.nuevoTicket());
        this.toast.success('Gasto registrado.');
      }
      this.mostrarModalGasto.set(false);
    }, editando ? 'No se pudo actualizar el egreso.' : 'No se pudo registrar el gasto.');
  }

  // ---------------------------------------------------------------- Pagar un vencimiento

  abrirPago(p: GastoRecurrentePago): void {
    this.pagando.set(p);
    this.pagoMonto.set(p.montoEstimado);
    this.pagoFecha.set(hoyIso());
    this.pagoTicket.set(null);
    this.errorModal.set(null);
  }

  async confirmarPago(): Promise<void> {
    const p = this.pagando();
    const monto = this.pagoMonto();
    if (!p) return;
    if (!monto || monto <= 0 || !this.pagoFecha()) {
      this.errorModal.set('Escribe el monto real y la fecha de pago.');
      return;
    }
    await this.guardar(async () => {
      await this.gastosService.pagarVencimiento(p.id, monto, this.pagoFecha(), this.pagoTicket());
      this.pagando.set(null);
      this.toast.success(`${p.nombre} pagado.`);
    }, 'No se pudo registrar el pago.');
  }

  async pagarNomina(sabado: { fecha: string; personas: number; total: number }): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Pagar nómina',
      mensaje: `Se marcan como pagadas ${sabado.personas} ${sabado.personas === 1 ? 'nómina' : 'nóminas'} del sábado ${this.fecha(sabado.fecha)} por $${this.money(sabado.total)} (su monto estimado), con fecha de hoy. Si alguna lleva ajuste, págala por separado en la lista.`,
      textoConfirmar: 'Pagar nómina',
      peligroso: false,
    });
    if (!confirmado) return;
    this.guardando.set(true);
    try {
      const r = await this.gastosService.pagarNomina(sabado.fecha);
      await this.refrescar();
      this.toast.success(`Nómina pagada: ${r.pagados} ${r.pagados === 1 ? 'persona' : 'personas'}, $${this.money(r.total)}.`);
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo pagar la nómina.'));
    } finally {
      this.guardando.set(false);
    }
  }

  async omitir(p: GastoRecurrentePago): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Omitir este pago',
      mensaje: `Se salta el pago de ${p.nombre} del ${this.fecha(p.fechaVencimiento)}: dejará de aparecer como pendiente. ¿Continuar?`,
      textoConfirmar: 'Omitir',
      peligroso: false,
    });
    if (!confirmado) return;
    try {
      await this.gastosService.omitirVencimiento(p.id);
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo omitir el pago.'));
    }
  }

  async deshacerPago(g: Gasto): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Deshacer pago',
      mensaje: `Se borra este gasto de ${g.concepto} por $${this.money(g.monto)} y el vencimiento vuelve a quedar sin pagar.`,
      textoConfirmar: 'Deshacer pago',
      peligroso: true,
    });
    if (!confirmado) return;
    this.savingId.set(g.id);
    try {
      await this.gastosService.deshacerPago(g.id);
      await this.refrescar();
      this.toast.success('Pago deshecho.');
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo deshacer el pago.'));
    } finally {
      this.savingId.set(null);
    }
  }

  // ---------------------------------------------------------------- Gastos recurrentes

  abrirNuevoRecurrente(): void {
    this.editandoRecurrente.set(null);
    this.recNombre.set('');
    this.recMonto.set(null);
    this.recFrecuencia.set('MENSUAL');
    this.recDia.set(new Date().getDate());
    this.recDia2.set(15);
    this.recPrimer.set(hoyIso());
    this.recActivo.set(true);
    this.errorModal.set(null);
    this.mostrarModalRecurrente.set(true);
  }

  abrirEditarRecurrente(g: GastoRecurrente): void {
    this.editandoRecurrente.set(g);
    this.recNombre.set(g.nombre);
    this.recMonto.set(g.montoEstimado);
    this.recFrecuencia.set(g.frecuencia);
    this.recDia.set(g.dia ?? 1);
    this.recDia2.set(g.dia2 ?? 15);
    this.recPrimer.set(g.primerVencimiento);
    this.recActivo.set(g.activo);
    this.errorModal.set(null);
    this.mostrarModalRecurrente.set(true);
  }

  async guardarRecurrente(): Promise<void> {
    const nombre = this.recNombre().trim();
    const monto = this.recMonto();
    const frecuencia = this.recFrecuencia();
    if (!nombre || monto === null || monto < 0 || !this.recPrimer()) {
      this.errorModal.set('Nombre, monto estimado y primer vencimiento son obligatorios.');
      return;
    }
    if (frecuencia !== 'SEMANAL' && !this.recDia()) {
      this.errorModal.set('Indica el día del mes del vencimiento.');
      return;
    }
    if (frecuencia === 'QUINCENAL' && (!this.recDia2() || this.recDia2() === this.recDia())) {
      this.errorModal.set('Un pago quincenal necesita dos días del mes distintos.');
      return;
    }
    const editando = this.editandoRecurrente();
    const request = {
      nombre,
      montoEstimado: monto,
      frecuencia,
      dia: frecuencia === 'SEMANAL' ? null : this.recDia(),
      dia2: frecuencia === 'QUINCENAL' ? this.recDia2() : null,
      primerVencimiento: this.recPrimer(),
      ...(editando ? { activo: this.recActivo() } : {}),
    };
    await this.guardar(async () => {
      if (editando) await this.gastosService.editarRecurrente(editando.id, request);
      else await this.gastosService.crearRecurrente(request);
      this.mostrarModalRecurrente.set(false);
      this.toast.success(editando ? 'Gasto recurrente actualizado.' : 'Gasto recurrente creado.');
    }, 'No se pudo guardar el gasto recurrente.');
  }

  async alternarActivo(g: GastoRecurrente): Promise<void> {
    this.savingId.set(g.id);
    try {
      await this.gastosService.editarRecurrente(g.id, {
        nombre: g.nombre,
        montoEstimado: g.montoEstimado,
        frecuencia: g.frecuencia,
        dia: g.dia,
        dia2: g.dia2,
        primerVencimiento: g.primerVencimiento,
        activo: !g.activo,
      });
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo cambiar el gasto recurrente.'));
    } finally {
      this.savingId.set(null);
    }
  }

  async eliminarRecurrente(g: GastoRecurrente): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar gasto recurrente',
      mensaje: `¿Eliminar "${g.nombre}"? Si ya tiene pagos registrados no se puede eliminar: desactívalo.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;
    this.savingId.set(g.id);
    try {
      await this.gastosService.eliminarRecurrente(g.id);
      await this.refrescar();
      this.toast.success('Gasto recurrente eliminado.');
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo eliminar el gasto recurrente.'));
    } finally {
      this.savingId.set(null);
    }
  }

  /** "Mensual · día 5", "Quincenal · días 15 y 30", "Semanal · cada lunes"... */
  descripcionFrecuencia(g: GastoRecurrente): string {
    const nombre = FRECUENCIA_GASTO_LABELS[g.frecuencia];
    if (g.frecuencia === 'SEMANAL') return `${nombre} · cada ${DIAS_SEMANA[new Date(`${g.primerVencimiento}T12:00:00`).getDay()]}`;
    if (g.frecuencia === 'QUINCENAL') return `${nombre} · días ${g.dia} y ${g.dia2}`;
    return `${nombre} · día ${g.dia}`;
  }

  // ---------------------------------------------------------------- Historial

  async verTicket(gasto: Gasto): Promise<void> {
    try {
      await this.gastosService.verTicket(gasto.id);
    } catch {
      this.toast.error('No se pudo abrir el ticket.');
    }
  }

  /** Abre la confirmación con contraseña para eliminar un egreso. */
  pedirEliminar(gasto: Gasto): void {
    this.eliminando.set(gasto);
    this.passwordEliminar.set('');
    this.errorEliminar.set(null);
  }

  cancelarEliminar(): void {
    if (!this.eliminandoEnCurso()) this.eliminando.set(null);
  }

  async confirmarEliminar(): Promise<void> {
    const gasto = this.eliminando();
    if (!gasto || !this.passwordEliminar() || this.eliminandoEnCurso()) return;
    this.eliminandoEnCurso.set(true);
    this.errorEliminar.set(null);
    try {
      await this.gastosService.eliminar(gasto.id, this.passwordEliminar());
      this.eliminando.set(null);
      await this.refrescar();
      this.toast.success(gasto.origen === 'COMISION' ? 'Egreso eliminado y entrega anulada.' : 'Egreso eliminado.');
    } catch (error) {
      this.errorEliminar.set(mensajeDe(error, 'No se pudo eliminar el egreso.'));
    } finally {
      this.eliminandoEnCurso.set(false);
    }
  }

  onTicketChange(event: Event, destino: 'gasto' | 'pago'): void {
    const archivo = (event.target as HTMLInputElement).files?.[0] ?? null;
    (destino === 'gasto' ? this.nuevoTicket : this.pagoTicket).set(archivo);
  }

  cerrarModales(): void {
    this.mostrarModalGasto.set(false);
    this.mostrarModalRecurrente.set(false);
    this.pagando.set(null);
    this.cancelarEliminar();
  }

  fecha(iso: string | null): string {
    if (!iso) return '—';
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Corre una acción de guardado con el estado de "guardando" y el error del servidor en el modal. */
  private async guardar(accion: () => Promise<void>, errorPorDefecto: string): Promise<void> {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      await accion();
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, errorPorDefecto));
    } finally {
      this.guardando.set(false);
    }
  }
}

const DIAS_SEMANA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function proximoSabadoIso(): string {
  const d = new Date();
  d.setDate(d.getDate() + ((6 - d.getDay() + 7) % 7));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function mesActual(): string {
  return hoyIso().slice(0, 7);
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : porDefecto;
}
