import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PromocionesService } from '../../../core/services/promociones.service';
import { Promocion, ProyectoPromocion } from '../../../core/models/promocion.model';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { PROJECTS_CONFIG } from '../../../core/data/proyectos-cotizador.config';
import {
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';

const PLAZO_PREVIEW_OPCIONES = [12, 24, 36, 48, 60] as const;

@Component({
  selector: 'app-promociones',
  standalone: true,
  imports: [RouterLink, DatePipe],
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

  /** Fecha y hora en las que la promoción deja de aplicar; vacía = sin vencimiento. */
  readonly fechaFin = signal('');
  readonly horaFin = signal(HORA_POR_DEFECTO);
  readonly minutoFin = signal(MINUTO_POR_DEFECTO);
  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;

  /** Plazo usado solo para la vista previa del cálculo, no se guarda con la promoción. */
  readonly plazoPreview = signal(60);
  readonly plazoPreviewOpciones = PLAZO_PREVIEW_OPCIONES;

  constructor() {
    this.cargar();
  }

  /** Precio de referencia de un lote estándar (200 m²) del proyecto, al plazo elegido para la
   * vista previa. Incluye el interés de Nanuu cuando el plazo coincide con uno de sus planes. */
  get precioReferenciaPreview(): number {
    const config = PROJECTS_CONFIG[this.proyecto()];
    const totalPrice = config.minimumArea * config.pricePerM2;

    if (this.proyecto() === 'nanuu') {
      const plan = config.financingPlans.find((p) => p.months === this.plazoPreview());
      const interesPorcentaje = plan?.interestPercentage ?? 0;
      return totalPrice + totalPrice * (interesPorcentaje / 100);
    }

    return totalPrice;
  }

  get aportacionesCountPreview(): number {
    return Math.max(Math.floor(this.plazoPreview() / 12) - 1, 0);
  }

  /** Mismo cálculo que usa el cotizador (mensualidad fija -> aportación anual requerida),
   * aplicado a un lote estándar de 200 m² para que el admin vea el efecto al capturar el monto. */
  get aportacionAnualPreview(): number | null {
    const count = this.aportacionesCountPreview;
    if (count <= 0 || this.mensualidadFija() <= 0) {
      return null;
    }

    const regularMonths = this.plazoPreview() - count;
    const cubiertoPorMensualidades = this.mensualidadFija() * regularMonths;
    return Math.max((this.precioReferenciaPreview - cubiertoPorMensualidades) / count, 0);
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

    if (promo.fechaFin) {
      const [fecha, horaMinuto] = promo.fechaFin.split('T');
      const [hora, minuto] = (horaMinuto ?? '').split(':');
      this.fechaFin.set(fecha);
      this.horaFin.set(hora || HORA_POR_DEFECTO);
      this.minutoFin.set(minuto || MINUTO_POR_DEFECTO);
    } else {
      this.fechaFin.set('');
      this.horaFin.set(HORA_POR_DEFECTO);
      this.minutoFin.set(MINUTO_POR_DEFECTO);
    }
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.proyecto.set('samai');
    this.mensualidadFija.set(0);
    this.mensualidadDisplay.set('');
    this.descripcion.set('');
    this.fechaFin.set('');
    this.horaFin.set(HORA_POR_DEFECTO);
    this.minutoFin.set(MINUTO_POR_DEFECTO);
  }

  async guardar(): Promise<void> {
    const nombre = this.nombre().trim();
    const monto = this.mensualidadFija();
    if (!nombre || monto <= 0 || this.isSaving()) return;

    const fechaFin = this.fechaFin() ? combinarFechaHora(this.fechaFin(), this.horaFin(), this.minutoFin()) : null;

    this.isSaving.set(true);
    try {
      const id = this.editandoId();
      if (id !== null) {
        const actualizada = await this.promocionesService.actualizar(id, {
          nombre,
          mensualidadFija: monto,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
        });
        this.promociones.update((lista) => lista.map((p) => (p.id === id ? actualizada : p)));
        this.toast.success('Promoción actualizada.');
      } else {
        const nueva = await this.promocionesService.crear({
          nombre,
          proyecto: this.proyecto(),
          mensualidadFija: monto,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
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
