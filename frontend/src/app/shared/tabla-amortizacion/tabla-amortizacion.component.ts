import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, effect, inject, input, model, output, signal, viewChild } from '@angular/core';
import { LucideChevronDown, LucideChevronRight, LucideChevronUp, LucideDownload, LucidePencil } from '@lucide/angular';
import {
  Amortizacion,
  CONCEPTO_SIN_FECHA,
  ESTADO_PAGO_PLAN_CLASES,
  etiquetaEstadoPago,
  EstadoPagoPlan,
  cuentaAtraso,
  fechaCorta,
  FilaAmortizacion,
  PagoMes,
  ReestructuracionPlan,
} from '../../core/models/amortizacion.model';
import { AuthService } from '../../core/services/auth.service';
import { VentasService } from '../../core/services/ventas.service';
import { ConfirmacionPausa, PausaMensualidadesComponent } from '../pausa-mensualidades/pausa-mensualidades.component';
import { PdfAmortizacionService } from '../../core/services/pdf-amortizacion.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Tabla de amortización de una venta con su estado de cuenta a hoy (ver backend service/CalendarioPagos): un bloque
 * plegable con el estado de la compra, el botón para descargarla en PDF y la tabla en una caja con scroll. La usan la
 * ficha del cliente (una por compra) y el detalle de la venta.
 */
@Component({
  selector: 'app-tabla-amortizacion',
  imports: [LucideChevronDown, LucideChevronRight, LucideChevronUp, LucideDownload, LucidePencil, PausaMensualidadesComponent],
  templateUrl: './tabla-amortizacion.component.html',
})
export class TablaAmortizacionComponent {
  private readonly pdf = inject(PdfAmortizacionService);
  private readonly toast = inject(ToastService);
  private readonly ventas = inject(VentasService);
  private readonly auth = inject(AuthService);

  readonly amortizacion = input.required<Amortizacion>();
  /** Encabezado del bloque; sin él: "Venta S-13 · SAMAI Campestre · Mz 3 / Lote 1". */
  readonly titulo = input<string | null>(null);
  readonly abierta = model(false);
  /** Se movió o se deshizo una pausa de mensualidades: el padre vuelve a pedir la tabla. */
  readonly actualizada = output<void>();

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
    return this.titulo() ?? [`Venta ${a.ventaNumero}`, a.desarrollos.join(', '), a.lotes.join(', ')].filter(Boolean).join(' · ');
  });

  readonly cuentaAtrasados = computed(() => cuentaAtraso(this.amortizacion().pagosAtrasados, this.amortizacion().mensualidadesAtrasadas));
  readonly liquidada = computed(() => this.amortizacion().saldo <= 0.005);
  /** La reestructuración que empieza en el renglón {@code i} (el primero de su tramo), para poner el separador. */
  reestructuracionAntesDe(i: number): ReestructuracionPlan | null {
    const filas = this.amortizacion().detalleMensual;
    const tramo = filas[i]?.tramo ?? 0;
    if (tramo === 0 || (i > 0 && filas[i - 1].tramo === tramo)) return null;
    return this.amortizacion().reestructuraciones?.find((r) => r.tramo === tramo) ?? null;
  }

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
  readonly indiceActual = computed(() => this.amortizacion().detalleMensual.findIndex((f) => f.estado !== 'PAGADO' && f.estado !== 'PAGADO_CON_RETRASO'));

  /** Fondo del renglón: rojo tenue si está atrasado, ámbar tenue si es el que le toca. */
  claseFila(estado: EstadoPagoPlan): string {
    if (estado === 'ATRASADO') return 'bg-red-50 dark:bg-red-500/[0.07]';
    if (estado === 'LE_TOCA') return 'bg-amber-50 dark:bg-amber-500/[0.07]';
    if (estado === 'ABONADO') return 'bg-sky-50 dark:bg-sky-500/[0.06]';
    return '';
  }

  // ---- Pausa de mensualidades (ver backend PausaMensualidadService) ----

  /** Mover fechas usa la API de Ventas: en la ficha del cliente solo si también tiene ese módulo. */
  readonly puedeMoverFechas = computed(() => this.auth.tieneModulo('VENTAS'));
  private readonly ultimoTramo = computed(() => Math.max(0, ...this.amortizacion().detalleMensual.map((f) => f.tramo)));
  /** Desde qué renglón se puede mover: después del último con pagos (los recibos guardan el mes que cubren) y no antes
   * de donde empieza la pausa más reciente. */
  private readonly primerMovible = computed(() => {
    const filas = this.amortizacion().detalleMensual;
    let desde = 0;
    filas.forEach((f, i) => {
      if (f.pagos.length > 0) desde = i + 1;
    });
    return desde;
  });
  private readonly numeroMinimo = computed(() => this.amortizacion().pausas?.at(-1)?.numero ?? 1);
  readonly ultimoNumero = computed(() =>
    Math.max(0, ...this.amortizacion().detalleMensual.filter((f) => f.tramo === this.ultimoTramo()).map((f) => f.numero ?? 0)),
  );

  movible(f: FilaAmortizacion, i: number): boolean {
    return (
      this.puedeMoverFechas() &&
      f.numero !== null &&
      !!f.fecha &&
      f.tramo === this.ultimoTramo() &&
      i >= this.primerMovible() &&
      f.numero >= this.numeroMinimo()
    );
  }

  /** La ventana abierta: mover la fecha de una mensualidad o deshacer la última pausa. */
  readonly pausa = signal<{ modo: 'mover' | 'deshacer'; numero: number; fecha: string } | null>(null);
  readonly guardandoPausa = signal(false);
  readonly errorPausa = signal<string | null>(null);

  abrirMover(f: FilaAmortizacion): void {
    if (f.numero === null || !f.fecha) return;
    this.errorPausa.set(null);
    this.pausa.set({ modo: 'mover', numero: f.numero, fecha: f.fecha });
  }

  abrirDeshacer(): void {
    const ultima = this.amortizacion().pausas?.at(-1);
    if (!ultima) return;
    this.errorPausa.set(null);
    this.pausa.set({ modo: 'deshacer', numero: ultima.numero, fecha: ultima.fechaAnterior });
  }

  async confirmarPausa(c: ConfirmacionPausa): Promise<void> {
    const p = this.pausa();
    if (!p || this.guardandoPausa()) return;
    this.guardandoPausa.set(true);
    this.errorPausa.set(null);
    try {
      const ventaId = this.amortizacion().ventaId;
      if (p.modo === 'mover' && c.fecha) {
        await this.ventas.pausar(ventaId, { password: c.password, motivo: c.motivo, numero: p.numero, fecha: c.fecha });
        this.toast.success(`La mensualidad N.º ${p.numero} y las siguientes se recorrieron.`);
      } else {
        await this.ventas.deshacerPausa(ventaId, { password: c.password, motivo: c.motivo });
        this.toast.success('Se deshizo la pausa.');
      }
      this.pausa.set(null);
      this.actualizada.emit();
    } catch (error) {
      this.errorPausa.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo guardar. Intenta de nuevo.',
      );
    } finally {
      this.guardandoPausa.set(false);
    }
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

  /** Alto (px) del bloque de un abono: igual en Monto pagado, Fecha del pago y Folio para que queden alineados. */
  alturaPago(p: PagoMes): number {
    return 16 * (1 + (p.parteDe ? 1 : 0) + (p.aCuentaComision ? 1 : 0));
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

function redondear(valor: number): number {
  return Math.round(valor * 100) / 100;
}
