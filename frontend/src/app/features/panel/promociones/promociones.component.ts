import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { LucidePencil, LucideTrash2 } from '@lucide/angular';
import { RouterLink } from '@angular/router';
import { PromocionesService } from '../../../core/services/promociones.service';
import { ModalidadPromocion, Promocion, ProyectoPromocion, TipoPrecioPromocion } from '../../../core/models/promocion.model';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { PROJECTS_CONFIG } from '../../../core/data/proyectos-cotizador.config';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';
import {
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';
import { formatearNumero, limpiarNumero, numeroDeTexto } from '../../../shared/moneda-input/moneda-input.directive';

const PLAZO_PREVIEW_OPCIONES = [12, 24, 36, 48, 60] as const;

@Component({
  selector: 'app-promociones',
  standalone: true,
  imports: [WeLoaderComponent, RouterLink, DatePipe, LucidePencil, LucideTrash2],
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
  readonly tipoPrecio = signal<TipoPrecioPromocion>('LOTE');
  readonly modalidad = signal<ModalidadPromocion>('MENSUALIDAD_FIJA');
  readonly mensualidadFija = signal(0);
  readonly mensualidadDisplay = signal('');
  readonly bonoDosLotes = signal(20000);
  readonly bonoDosLotesDisplay = signal('20,000');
  readonly bonoTresLotes = signal(30000);
  readonly bonoTresLotesDisplay = signal('30,000');
  readonly plazoFijo = signal(60);
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

  get formularioValido(): boolean {
    if (!this.nombre().trim()) return false;
    if (this.modalidad() === 'BONO_LOTES') {
      return this.bonoDosLotes() > 0 && this.bonoTresLotes() > 0 && this.plazoFijo() > 0;
    }
    return this.mensualidadFija() > 0;
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

  /** Vista previa para la modalidad BONO_LOTES con 2 y 3 lotes estándar de 200 m². */
  private precioLoteBaseBonoPreview(): number {
    const config = PROJECTS_CONFIG[this.proyecto()];
    return config.minimumArea * config.pricePerM2;
  }

  get precioListaDosLotesPreview(): number {
    return this.precioLoteBaseBonoPreview() * 2;
  }

  get totalConBonoDosLotesPreview(): number {
    return Math.max(this.precioListaDosLotesPreview - this.bonoDosLotes(), 0);
  }

  get mensualidadDosLotesPreview(): number {
    return this.plazoFijo() > 0 ? this.totalConBonoDosLotesPreview / this.plazoFijo() : 0;
  }

  get precioListaTresLotesPreview(): number {
    return this.precioLoteBaseBonoPreview() * 3;
  }

  get totalConBonoTresLotesPreview(): number {
    return Math.max(this.precioListaTresLotesPreview - this.bonoTresLotes(), 0);
  }

  get mensualidadTresLotesPreview(): number {
    return this.plazoFijo() > 0 ? this.totalConBonoTresLotesPreview / this.plazoFijo() : 0;
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

  /** Aldea Nanuu nunca tiene tarifa de macrolote, así que una promoción suya solo puede ser de tipo
   * "Lotes" — si el admin cambia el proyecto a Nanuu con "Hectáreas" ya elegido, se corrige solo. */
  onProyectoChange(valor: string): void {
    this.proyecto.set(valor as ProyectoPromocion);
    if (valor === 'nanuu') {
      this.tipoPrecio.set('LOTE');
    }
  }

  onMensualidadInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = limpiarNumero(input.value, 2);
    this.mensualidadFija.set(numeroDeTexto(limpio));
    this.mensualidadDisplay.set(formatearNumero(limpio, 2));
    input.value = this.mensualidadDisplay();
  }

  onBonoDosLotesInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = limpiarNumero(input.value, 2);
    this.bonoDosLotes.set(numeroDeTexto(limpio));
    this.bonoDosLotesDisplay.set(formatearNumero(limpio, 2));
    input.value = this.bonoDosLotesDisplay();
  }

  onBonoTresLotesInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = limpiarNumero(input.value, 2);
    this.bonoTresLotes.set(numeroDeTexto(limpio));
    this.bonoTresLotesDisplay.set(formatearNumero(limpio, 2));
    input.value = this.bonoTresLotesDisplay();
  }

  editar(promo: Promocion): void {
    this.editandoId.set(promo.id);
    this.nombre.set(promo.nombre);
    this.proyecto.set(promo.proyecto);
    this.tipoPrecio.set(promo.tipoPrecio);
    const mod: ModalidadPromocion = promo.modalidad ?? 'MENSUALIDAD_FIJA';
    this.modalidad.set(mod);
    if (mod === 'BONO_LOTES') {
      const b2 = promo.bonoDosLotes ?? 20000;
      const b3 = promo.bonoTresLotes ?? 30000;
      this.bonoDosLotes.set(b2);
      this.bonoDosLotesDisplay.set(formatearNumero(String(b2), 2));
      this.bonoTresLotes.set(b3);
      this.bonoTresLotesDisplay.set(formatearNumero(String(b3), 2));
      this.plazoFijo.set(promo.plazoFijo ?? 60);
    } else {
      const mens = promo.mensualidadFija ?? 0;
      this.mensualidadFija.set(mens);
      this.mensualidadDisplay.set(mens > 0 ? formatearNumero(String(mens), 2) : '');
    }
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
    this.tipoPrecio.set('LOTE');
    this.modalidad.set('MENSUALIDAD_FIJA');
    this.mensualidadFija.set(0);
    this.mensualidadDisplay.set('');
    this.bonoDosLotes.set(20000);
    this.bonoDosLotesDisplay.set('20,000');
    this.bonoTresLotes.set(30000);
    this.bonoTresLotesDisplay.set('30,000');
    this.plazoFijo.set(60);
    this.descripcion.set('');
    this.fechaFin.set('');
    this.horaFin.set(HORA_POR_DEFECTO);
    this.minutoFin.set(MINUTO_POR_DEFECTO);
  }

  async guardar(): Promise<void> {
    const nombre = this.nombre().trim();
    if (!this.formularioValido || this.isSaving()) return;

    const mod = this.modalidad();
    const fechaFin = this.fechaFin() ? combinarFechaHora(this.fechaFin(), this.horaFin(), this.minutoFin()) : null;

    this.isSaving.set(true);
    try {
      const id = this.editandoId();
      if (id !== null) {
        const actualizada = await this.promocionesService.actualizar(id, {
          nombre,
          modalidad: mod,
          mensualidadFija: mod === 'MENSUALIDAD_FIJA' ? this.mensualidadFija() : null,
          bonoDosLotes: mod === 'BONO_LOTES' ? this.bonoDosLotes() : null,
          bonoTresLotes: mod === 'BONO_LOTES' ? this.bonoTresLotes() : null,
          plazoFijo: mod === 'BONO_LOTES' ? this.plazoFijo() : null,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
          limpiarFechaFin: !fechaFin,
        });
        this.promociones.update((lista) => lista.map((p) => (p.id === id ? actualizada : p)));
        this.toast.success('Promoción actualizada.');
      } else {
        const nueva = await this.promocionesService.crear({
          nombre,
          proyecto: this.proyecto(),
          tipoPrecio: this.tipoPrecio(),
          modalidad: mod,
          mensualidadFija: mod === 'MENSUALIDAD_FIJA' ? this.mensualidadFija() : null,
          bonoDosLotes: mod === 'BONO_LOTES' ? this.bonoDosLotes() : null,
          bonoTresLotes: mod === 'BONO_LOTES' ? this.bonoTresLotes() : null,
          plazoFijo: mod === 'BONO_LOTES' ? this.plazoFijo() : null,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
        });
        // Crear una promoción activa desactiva otra del mismo proyecto, tarifa y modalidad.
        this.promociones.update((lista) => [
          nueva,
          ...lista.map((p) =>
            p.proyecto === nueva.proyecto &&
            p.tipoPrecio === nueva.tipoPrecio &&
            (p.modalidad ?? 'MENSUALIDAD_FIJA') === (nueva.modalidad ?? 'MENSUALIDAD_FIJA')
              ? { ...p, activa: false }
              : p,
          ),
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
          if (
            actualizada.activa &&
            p.proyecto === actualizada.proyecto &&
            p.tipoPrecio === actualizada.tipoPrecio &&
            (p.modalidad ?? 'MENSUALIDAD_FIJA') === (actualizada.modalidad ?? 'MENSUALIDAD_FIJA')
          ) {
            return { ...p, activa: false };
          }
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
