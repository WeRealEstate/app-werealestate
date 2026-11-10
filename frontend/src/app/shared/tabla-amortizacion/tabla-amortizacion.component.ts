import { Component, ElementRef, computed, effect, inject, input, model, signal, viewChild } from '@angular/core';
import { LucideChevronDown, LucideChevronRight, LucideChevronUp, LucideDownload } from '@lucide/angular';
import {
  Amortizacion,
  CONCEPTO_SIN_FECHA,
  ESTADO_PAGO_PLAN_CLASES,
  etiquetaEstadoPago,
  EstadoPagoPlan,
  cuentaAtraso,
  fechaCorta,
} from '../../core/models/amortizacion.model';
import { PdfAmortizacionService } from '../../core/services/pdf-amortizacion.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Tabla de amortización de una venta con su estado de cuenta a hoy (ver backend service/CalendarioPagos): un bloque
 * plegable con el estado de la compra, el botón para descargarla en PDF y la tabla en una caja con scroll. La usan la
 * ficha del cliente (una por compra) y el detalle de la venta.
 */
@Component({
  selector: 'app-tabla-amortizacion',
  imports: [LucideChevronDown, LucideChevronRight, LucideChevronUp, LucideDownload],
  templateUrl: './tabla-amortizacion.component.html',
})
export class TablaAmortizacionComponent {
  private readonly pdf = inject(PdfAmortizacionService);
  private readonly toast = inject(ToastService);

  readonly amortizacion = input.required<Amortizacion>();
  /** Encabezado del bloque; sin él: "Venta #13 · SAMAI Campestre · Mz 3 / Lote 1". */
  readonly titulo = input<string | null>(null);
  readonly abierta = model(false);

  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  readonly generando = signal(false);
  /** true mientras la caja tenga renglones por ver más abajo (muestra el aviso de seguir bajando). */
  readonly hayMas = signal(false);
  /** true si la caja ya se desplazó y quedan renglones por ver arriba; alturaEncabezado: para ponerlos debajo del
   * encabezado fijo de la tabla. */
  readonly hayArriba = signal(false);
  readonly alturaEncabezado = signal(44);

  readonly etiquetaEstado = etiquetaEstadoPago;
  readonly estadoClases = ESTADO_PAGO_PLAN_CLASES;
  readonly sinFecha = CONCEPTO_SIN_FECHA;
  readonly fecha = fechaCorta;

  readonly encabezado = computed(() => {
    const a = this.amortizacion();
    return this.titulo() ?? [`Venta #${a.ventaNumero}`, a.desarrollos.join(', '), a.lotes.join(', ')].filter(Boolean).join(' · ');
  });

  readonly cuentaAtrasados = computed(() => cuentaAtraso(this.amortizacion().pagosAtrasados, this.amortizacion().mensualidadesAtrasadas));
  readonly liquidada = computed(() => this.amortizacion().saldo <= 0.005);
  readonly conSinFecha = computed(() => this.amortizacion().detalleMensual.some((f) => f.concepto === CONCEPTO_SIN_FECHA));
  readonly totalAbonadoMes = computed(() => redondear(this.amortizacion().detalleMensual.reduce((s, f) => s + f.abonadoMes, 0)));
  readonly idTabla = computed(() => `tabla-amortizacion-${this.amortizacion().ventaId}`);

  constructor() {
    // Cada vez que se abre (también si ya llega abierta), la caja se coloca en el pago actual.
    effect(() => {
      if (this.abierta()) setTimeout(() => this.irAlPagoActual());
    });
  }

  alternar(): void {
    this.abierta.update((v) => !v);
  }

  /** Al abrir, la caja se desplaza al primer pago sin cubrir (dejando dos renglones de contexto arriba): en una
   * tabla de 60 renglones es lo que interesa ver. */
  private irAlPagoActual(): void {
    const caja = this.caja()?.nativeElement;
    const fila = caja?.querySelector<HTMLElement>('tr[data-actual]');
    if (!caja) return;
    if (!fila) return this.actualizarIndicador();
    const encabezado = caja.querySelector('thead')?.getBoundingClientRect().height ?? 0;
    const anterior = (fila.previousElementSibling?.previousElementSibling as HTMLElement | null) ?? fila;
    caja.scrollTop = Math.max(0, anterior.offsetTop - encabezado);
    this.actualizarIndicador();
  }

  /** Recalcula si quedan renglones por ver debajo de lo visible (se llama al abrir y en cada scroll). */
  actualizarIndicador(): void {
    const caja = this.caja()?.nativeElement;
    this.hayMas.set(!!caja && caja.scrollTop + caja.clientHeight < caja.scrollHeight - 4);
    this.hayArriba.set(!!caja && caja.scrollTop > 4);
    const encabezado = caja?.querySelector('thead')?.getBoundingClientRect().height;
    if (encabezado) this.alturaEncabezado.set(encabezado);
  }

  /** El aviso de arriba también es un botón: sube un buen tramo. */
  subir(): void {
    const caja = this.caja()?.nativeElement;
    caja?.scrollBy({ top: -caja.clientHeight * 0.8, behavior: 'smooth' });
  }

  /** El aviso también es un botón: baja un buen tramo de la tabla. */
  bajar(): void {
    const caja = this.caja()?.nativeElement;
    caja?.scrollBy({ top: caja.clientHeight * 0.8, behavior: 'smooth' });
  }

  /** Índice del primer renglón que todavía no está pagado (el que se marca como actual). */
  readonly indiceActual = computed(() => this.amortizacion().detalleMensual.findIndex((f) => f.estado !== 'PAGADO'));

  /** Fondo del renglón: rojo tenue si está atrasado, ámbar tenue si es el que le toca. */
  claseFila(estado: EstadoPagoPlan): string {
    if (estado === 'ATRASADO') return 'bg-red-50 dark:bg-red-500/[0.07]';
    if (estado === 'LE_TOCA') return 'bg-amber-50 dark:bg-amber-500/[0.07]';
    if (estado === 'ABONADO') return 'bg-sky-50 dark:bg-sky-500/[0.06]';
    return '';
  }

  async descargarPdf(): Promise<void> {
    if (this.generando()) return;
    this.generando.set(true);
    try {
      await this.pdf.descargar(this.amortizacion());
    } catch {
      this.toast.error('No se pudo generar el PDF. Intenta de nuevo.');
    } finally {
      this.generando.set(false);
    }
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

function redondear(valor: number): number {
  return Math.round(valor * 100) / 100;
}
