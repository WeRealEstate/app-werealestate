import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { LucidePencil, LucideTrash2 } from '@lucide/angular';
import { RouterLink } from '@angular/router';
import { PromocionesService } from '../../../core/services/promociones.service';
import { ModalidadPromocion, Promocion, ProyectoPromocion, TipoPrecioPromocion, MAX_LOTES_NIVEL, NivelBono } from '../../../core/models/promocion.model';
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

/** Un nivel mientras se edita: el número de lotes puede quedar vacío y el bono lleva su texto con comas. */
interface NivelEditable {
  lotes: number | null;
  bono: number;
  bonoDisplay: string;
}

/** Los niveles con que arranca una promoción nueva de bono: 2 y 3 lotes, como antes. */
function nivelesPorDefecto(): NivelEditable[] {
  return [
    { lotes: 2, bono: 20000, bonoDisplay: '20,000' },
    { lotes: 3, bono: 30000, bonoDisplay: '30,000' },
  ];
}

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
  /** 0 = la promoción no aplica a 1 lote. */
  /** Niveles del bono: cuántos lotes (exactos) y de cuánto es el bono; se agregan los que haga falta. */
  readonly niveles = signal<NivelEditable[]>(nivelesPorDefecto());
  readonly maxLotesNivel = MAX_LOTES_NIVEL;
  readonly plazoFijo = signal(60);
  readonly descripcion = signal('');
  /** Código de 6 dígitos que escribe el admin; vacío = al crear, uno al azar; al editar, el mismo. */
  readonly codigo = signal('');

  /** Fecha y hora desde las que aplica; vacía = al crearla (al editar, vacía = la que ya tiene). Si es futura, la
   * promoción queda programada y se activa sola ese día. */
  readonly fechaInicio = signal('');
  readonly horaInicio = signal(HORA_POR_DEFECTO);
  readonly minutoInicio = signal(MINUTO_POR_DEFECTO);

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
      return this.errorNiveles() === null && this.plazoFijo() > 0;
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

  /** Vista previa para la modalidad BONO_LOTES: cada nivel sobre lotes estándar de 200 m². */
  private precioLoteBaseBonoPreview(): number {
    const config = PROJECTS_CONFIG[this.proyecto()];
    return config.minimumArea * config.pricePerM2;
  }

  get nivelesPreview(): { lotes: number; lista: number; bono: number; total: number; mensualidad: number }[] {
    return this.niveles()
      .filter((n) => n.lotes !== null && n.lotes >= 1 && n.bono > 0)
      .sort((x, y) => (x.lotes ?? 0) - (y.lotes ?? 0))
      .map((n) => {
        const lista = this.precioLoteBaseBonoPreview() * (n.lotes ?? 0);
        const total = Math.max(lista - n.bono, 0);
        return { lotes: n.lotes ?? 0, lista, bono: n.bono, total, mensualidad: this.plazoFijo() > 0 ? total / this.plazoFijo() : 0 };
      });
  }

  /** Qué le falta a los niveles para poder guardar (null = están bien). */
  errorNiveles(): string | null {
    const niveles = this.niveles();
    if (niveles.length === 0) return 'Agrega al menos un nivel.';
    const vistos = new Set<number>();
    for (const n of niveles) {
      if (n.lotes === null || !Number.isInteger(n.lotes) || n.lotes < 1 || n.lotes > MAX_LOTES_NIVEL) {
        return `Cada nivel debe ser de 1 a ${MAX_LOTES_NIVEL} lotes.`;
      }
      if (vistos.has(n.lotes)) return `Hay dos niveles para ${n.lotes} lotes.`;
      vistos.add(n.lotes);
      if (!(n.bono > 0)) return `Escribe el bono para ${n.lotes} ${n.lotes === 1 ? 'lote' : 'lotes'}.`;
    }
    return null;
  }

  agregarNivel(): void {
    const usados = new Set(this.niveles().map((n) => n.lotes));
    let siguiente = Math.max(0, ...this.niveles().map((n) => n.lotes ?? 0)) + 1;
    while (usados.has(siguiente) && siguiente <= MAX_LOTES_NIVEL) siguiente++;
    this.niveles.update((l) => [...l, { lotes: siguiente <= MAX_LOTES_NIVEL ? siguiente : null, bono: 0, bonoDisplay: '' }]);
  }

  quitarNivel(i: number): void {
    this.niveles.update((l) => l.filter((_, k) => k !== i));
  }

  cambiarLotesNivel(i: number, valor: string): void {
    const n = valor === '' ? null : Number(valor);
    this.niveles.update((l) => l.map((x, k) => (k === i ? { ...x, lotes: n } : x)));
  }

  onBonoNivelInput(i: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = limpiarNumero(input.value, 2);
    const display = formatearNumero(limpio, 2);
    input.value = display;
    this.niveles.update((l) => l.map((x, k) => (k === i ? { ...x, bono: numeroDeTexto(limpio), bonoDisplay: display } : x)));
  }

  private nivelesParaGuardar(): NivelBono[] {
    return this.niveles()
      .map((n) => ({ lotes: n.lotes ?? 0, bono: n.bono }))
      .sort((x, y) => x.lotes - y.lotes);
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

  editar(promo: Promocion): void {
    this.editandoId.set(promo.id);
    this.nombre.set(promo.nombre);
    this.proyecto.set(promo.proyecto);
    this.tipoPrecio.set(promo.tipoPrecio);
    const mod: ModalidadPromocion = promo.modalidad ?? 'MENSUALIDAD_FIJA';
    this.modalidad.set(mod);
    if (mod === 'BONO_LOTES') {
      this.niveles.set(
        promo.bonos.length > 0
          ? promo.bonos.map((n) => ({ lotes: n.lotes, bono: n.bono, bonoDisplay: formatearNumero(String(n.bono), 2) }))
          : nivelesPorDefecto(),
      );
      this.plazoFijo.set(promo.plazoFijo ?? 60);
    } else {
      const mens = promo.mensualidadFija ?? 0;
      this.mensualidadFija.set(mens);
      this.mensualidadDisplay.set(mens > 0 ? formatearNumero(String(mens), 2) : '');
    }
    this.descripcion.set(promo.descripcion ?? '');
    this.codigo.set(promo.codigo ?? '');

    if (promo.fechaInicio) {
      const [fecha, horaMinuto] = promo.fechaInicio.split('T');
      const [hora, minuto] = (horaMinuto ?? '').split(':');
      this.fechaInicio.set(fecha);
      this.horaInicio.set(hora || HORA_POR_DEFECTO);
      this.minutoInicio.set(minuto || MINUTO_POR_DEFECTO);
    } else {
      this.fechaInicio.set('');
      this.horaInicio.set(HORA_POR_DEFECTO);
      this.minutoInicio.set(MINUTO_POR_DEFECTO);
    }

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
    this.niveles.set(nivelesPorDefecto());
    this.plazoFijo.set(60);
    this.descripcion.set('');
    this.codigo.set('');
    this.fechaInicio.set('');
    this.horaInicio.set(HORA_POR_DEFECTO);
    this.minutoInicio.set(MINUTO_POR_DEFECTO);
    this.fechaFin.set('');
    this.horaFin.set(HORA_POR_DEFECTO);
    this.minutoFin.set(MINUTO_POR_DEFECTO);
  }

  async guardar(): Promise<void> {
    const nombre = this.nombre().trim();
    if (!this.formularioValido || this.isSaving()) return;
    const codigo = this.codigo().trim();
    if (codigo && !/^[A-Z0-9]{6}$/.test(codigo)) {
      this.toast.error('El código de promoción debe tener exactamente 6 letras o números.');
      return;
    }

    const mod = this.modalidad();
    const fechaFin = this.fechaFin() ? combinarFechaHora(this.fechaFin(), this.horaFin(), this.minutoFin()) : null;
    const fechaInicio = this.fechaInicio()
      ? combinarFechaHora(this.fechaInicio(), this.horaInicio(), this.minutoInicio())
      : null;

    this.isSaving.set(true);
    try {
      const id = this.editandoId();
      if (id !== null) {
        const actualizada = await this.promocionesService.actualizar(id, {
          nombre,
          modalidad: mod,
          mensualidadFija: mod === 'MENSUALIDAD_FIJA' ? this.mensualidadFija() : null,
          bonos: mod === 'BONO_LOTES' ? this.nivelesParaGuardar() : null,
          plazoFijo: mod === 'BONO_LOTES' ? this.plazoFijo() : null,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
          limpiarFechaFin: !fechaFin,
          fechaInicio,
          codigo: codigo || null,
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
          bonos: mod === 'BONO_LOTES' ? this.nivelesParaGuardar() : null,
          plazoFijo: mod === 'BONO_LOTES' ? this.plazoFijo() : null,
          descripcion: this.descripcion().trim() || null,
          fechaFin,
          fechaInicio,
          codigo: codigo || null,
        });
        // Crear una promoción activa desactiva otra del mismo proyecto, tarifa y modalidad (una programada no, hasta
        // que llegue su fecha de inicio).
        this.promociones.update((lista) => [
          nueva,
          ...lista.map((p) =>
            nueva.activa &&
            p.proyecto === nueva.proyecto &&
            p.tipoPrecio === nueva.tipoPrecio &&
            (p.modalidad ?? 'MENSUALIDAD_FIJA') === (nueva.modalidad ?? 'MENSUALIDAD_FIJA')
              ? { ...p, activa: false }
              : p,
          ),
        ]);
        this.toast.success(
          nueva.programada
            ? `Promoción "${nueva.nombre}" creada: se activará sola al llegar su fecha de inicio.`
            : `Promoción "${nueva.nombre}" creada.`,
        );
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
      const actualizada = await this.promocionesService.cambiarEstado(promo.id, promo.programada ? false : !promo.activa);
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
        actualizada.programada
          ? `"${actualizada.nombre}" se activará sola al llegar su fecha de inicio.`
          : actualizada.activa
            ? `"${actualizada.nombre}" ahora está activa.`
            : `"${actualizada.nombre}" fue desactivada.`,
      );
    } catch {
      this.toast.error('No se pudo cambiar el estado de la promoción.');
    }
  }

  /** Letras y números en mayúsculas, máximo 6 (el código no distingue mayúsculas). */
  escribirCodigo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
    input.value = limpio;
    this.codigo.set(limpio);
  }

  async copiarCodigo(promo: Promocion): Promise<void> {
    if (!promo.codigo) return;
    try {
      await navigator.clipboard.writeText(promo.codigo);
      this.toast.success(`Código ${promo.codigo} copiado.`);
    } catch {
      this.toast.error('No se pudo copiar el código.');
    }
  }

  /** «Generar otro»: el código anterior deja de servir (por si se filtró). */
  async generarCodigo(promo: Promocion): Promise<void> {
    if (promo.codigo) {
      const confirmado = await this.confirmService.confirm({
        titulo: 'Generar otro código',
        mensaje: `El código ${promo.codigo} de "${promo.nombre}" dejará de servir y los asesores tendrán que usar el nuevo.`,
        textoConfirmar: 'Generar otro',
      });
      if (!confirmado) return;
    }
    try {
      const actualizada = await this.promocionesService.generarCodigo(promo.id);
      this.promociones.update((lista) => lista.map((p) => (p.id === promo.id ? actualizada : p)));
      if (this.editandoId() === promo.id) this.codigo.set(actualizada.codigo ?? '');
      this.toast.success(`Nuevo código de "${promo.nombre}": ${actualizada.codigo}.`);
    } catch {
      this.toast.error('No se pudo generar el código.');
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
