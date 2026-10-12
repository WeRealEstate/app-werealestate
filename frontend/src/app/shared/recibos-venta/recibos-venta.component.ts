import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PagoVenta, ReciboHistorial, TIPO_ABONO_LABELS, TipoAbono, Venta } from '../../core/models/venta.model';
import { MesPlan, claveMes, etiquetaMes, mesesDelPlan } from '../../core/utils/plan-mensualidades';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

interface Recibo {
  id: number;
  fecha: string;
  folio: string | null;
  notas: string | null;
  registradoPor: string;
  fechaCreacion: string;
  anulado: boolean;
  anuladoMotivo: string | null;
  anuladoPor: string | null;
  anuladoFecha: string | null;
  total: number;
  lineas: PagoVenta[];
}

interface LineaEdicion {
  pagoId: number;
  monto: number;
  tipo: TipoAbono | '';
  meses: string[];
  aportacionId: number | null;
}

/** Historial de abonos de una venta, agrupado por recibo (un folio, una o más líneas): permite
 * editar lo no financiero de un recibo, anularlo con motivo y ver su bitácora. Los recibos anulados
 * no se borran: se muestran tachados con su motivo. */
@Component({
  selector: 'app-recibos-venta',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './recibos-venta.component.html',
})
export class RecibosVentaComponent {
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly venta = input.required<Venta>();
  readonly pagos = input.required<PagoVenta[]>();
  readonly historial = input<ReciboHistorial[]>([]);
  /** Se emite tras editar o anular para que el padre vuelva a cargar la venta, los pagos y la bitácora. */
  readonly cambio = output<void>();

  readonly tipoLabels = TIPO_ABONO_LABELS;
  readonly accionLabels = { CREADO: 'Creado', EDITADO: 'Editado', ANULADO: 'Anulado' } as const;

  readonly recibos = computed<Recibo[]>(() => {
    const porId = new Map<number, Recibo>();
    for (const p of this.pagos()) {
      let r = porId.get(p.reciboId);
      if (!r) {
        r = {
          id: p.reciboId,
          fecha: p.fecha,
          folio: p.folio,
          notas: p.notas,
          registradoPor: p.registradoPor.nombre,
          fechaCreacion: p.fechaCreacion,
          anulado: p.anulado,
          anuladoMotivo: p.anuladoMotivo,
          anuladoPor: p.anuladoPor?.nombre ?? null,
          anuladoFecha: p.anuladoFecha,
          total: 0,
          lineas: [],
        };
        porId.set(p.reciboId, r);
      }
      r.total += p.monto;
      r.lineas.push(p);
    }
    return [...porId.values()].sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id);
  });

  readonly historialAbiertoId = signal<number | null>(null);

  historialDe(reciboId: number): ReciboHistorial[] {
    return this.historial().filter((h) => h.reciboId === reciboId);
  }

  alternarHistorial(reciboId: number): void {
    this.historialAbiertoId.update((id) => (id === reciboId ? null : reciboId));
  }

  /** "nov 2026, dic 2026" o la aportación que paga; vacío si no aplica. */
  detalleConcepto(p: PagoVenta): string {
    if (p.tipo === 'MENSUALIDAD') return p.meses.map((m) => etiquetaMes(m.anio, m.mes)).join(', ');
    if (p.tipo === 'APORTACION') {
      const a = this.venta().aportaciones.find((x) => x.id === p.aportacionId);
      return a ? etiquetaMes(a.anio, a.mes) : '';
    }
    return '';
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  fechaHora(iso: string): string {
    const d = new Date(iso);
    return `${d.toLocaleDateString('es-MX')} ${d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })}`;
  }

  fecha(iso: string): string {
    return iso.split('-').reverse().join('/');
  }

  // ---------------------------------------------------------------- editar

  readonly editando = signal<Recibo | null>(null);
  readonly editFolio = signal('');
  readonly editNotas = signal('');
  readonly editLineas = signal<LineaEdicion[]>([]);
  readonly guardando = signal(false);
  readonly errorModal = signal<string | null>(null);

  /** Los cuatro tipos siempre salen; los que esta venta no puede recibir se muestran deshabilitados
   * con la razón (por ejemplo, una venta de contado no tiene mensualidades). */
  readonly tiposDisponibles = computed(() => {
    const v = this.venta();
    const tienePlan = !!(v.plazoMeses && v.mensualidad);
    const tieneAportaciones = v.aportaciones.length > 0;
    return [
      { valor: 'MENSUALIDAD' as TipoAbono, label: TIPO_ABONO_LABELS.MENSUALIDAD, deshabilitado: !tienePlan, motivo: 'esta venta no tiene mensualidades' },
      { valor: 'ENGANCHE' as TipoAbono, label: TIPO_ABONO_LABELS.ENGANCHE, deshabilitado: false, motivo: '' },
      { valor: 'APARTADO' as TipoAbono, label: TIPO_ABONO_LABELS.APARTADO, deshabilitado: false, motivo: '' },
      { valor: 'APORTACION' as TipoAbono, label: TIPO_ABONO_LABELS.APORTACION, deshabilitado: !tieneAportaciones, motivo: 'esta venta no tiene aportaciones programadas' },
    ];
  });

  private readonly planCompleto = computed<MesPlan[]>(() => mesesDelPlan(this.venta()));

  /** Meses que se pueden elegir al editar: los no cubiertos por OTROS recibos vigentes. */
  mesesElegibles(): MesPlan[] {
    const recibo = this.editando();
    if (!recibo) return [];
    const ocupados = new Set<string>();
    for (const p of this.pagos()) {
      if (p.tipo === 'MENSUALIDAD' && !p.anulado && p.reciboId !== recibo.id) {
        for (const m of p.meses) ocupados.add(claveMes(m.anio, m.mes));
      }
    }
    return this.planCompleto().filter((m) => !ocupados.has(m.clave));
  }

  mesEnOtraLinea(indice: number, clave: string): boolean {
    return this.editLineas().some((l, i) => i !== indice && l.meses.includes(clave));
  }

  readonly aportacionesOpciones = computed(() =>
    this.venta().aportaciones.map((a) => ({
      id: a.id!,
      etiqueta: `${etiquetaMes(a.anio, a.mes)} · $${a.monto.toLocaleString('en-US')}`,
    })),
  );

  abrirEdicion(r: Recibo): void {
    this.editando.set(r);
    this.editFolio.set(r.folio ?? '');
    this.editNotas.set(r.notas ?? '');
    this.editLineas.set(
      r.lineas.map((p) => ({
        pagoId: p.id,
        monto: p.monto,
        tipo: p.tipo ?? '',
        meses: p.meses.map((m) => claveMes(m.anio, m.mes)),
        aportacionId: p.aportacionId,
      })),
    );
    this.errorModal.set(null);
  }

  cerrarEdicion(): void {
    this.editando.set(null);
  }

  cambiarTipoEdicion(indice: number, tipo: TipoAbono | ''): void {
    this.editLineas.update((ls) =>
      ls.map((l, i) => {
        if (i !== indice) return l;
        const cambio: LineaEdicion = { ...l, tipo, meses: [], aportacionId: null };
        if (tipo === 'MENSUALIDAD') {
          const primero = this.mesesElegibles().find((m) => !this.mesEnOtraLinea(indice, m.clave));
          if (primero) cambio.meses = [primero.clave];
        } else if (tipo === 'APORTACION') {
          cambio.aportacionId = this.aportacionesOpciones()[0]?.id ?? null;
        }
        return cambio;
      }),
    );
  }

  alternarMesEdicion(indice: number, clave: string): void {
    this.editLineas.update((ls) =>
      ls.map((l, i) =>
        i === indice
          ? { ...l, meses: l.meses.includes(clave) ? l.meses.filter((c) => c !== clave) : [...l.meses, clave] }
          : l,
      ),
    );
  }

  cambiarAportacionEdicion(indice: number, id: number | null): void {
    this.editLineas.update((ls) => ls.map((l, i) => (i === indice ? { ...l, aportacionId: id } : l)));
  }

  async guardarEdicion(): Promise<void> {
    const recibo = this.editando();
    if (!recibo || this.guardando()) return;
    if (!this.editFolio().trim()) {
      this.errorModal.set('Ingresa el folio del abono.');
      return;
    }
    for (const l of this.editLineas()) {
      if (!l.tipo) return void this.errorModal.set('Elige el tipo de abono de cada concepto.');
      if (l.tipo === 'MENSUALIDAD' && l.meses.length === 0) {
        return void this.errorModal.set('Elige el mes o los meses que cubre la mensualidad.');
      }
      if (l.tipo === 'APORTACION' && l.aportacionId === null) {
        return void this.errorModal.set('Elige qué aportación programada paga.');
      }
    }

    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      await this.ventasService.editarRecibo(this.venta().id, recibo.id, {
        folio: this.editFolio().trim(),
        notas: this.editNotas().trim() || null,
        lineas: this.editLineas().map((l) => ({
          pagoId: l.pagoId,
          tipo: l.tipo as TipoAbono,
          meses:
            l.tipo === 'MENSUALIDAD'
              ? l.meses.map((c) => {
                  const [anio, mes] = c.split('-').map(Number);
                  return { anio, mes };
                })
              : undefined,
          aportacionId: l.tipo === 'APORTACION' ? l.aportacionId : null,
        })),
      });
      this.editando.set(null);
      this.toast.success('Recibo actualizado.');
      this.cambio.emit();
    } catch (e) {
      this.errorModal.set(mensajeDe(e, 'No se pudo actualizar el recibo.'));
    } finally {
      this.guardando.set(false);
    }
  }

  // ---------------------------------------------------------------- anular

  readonly anulando = signal<Recibo | null>(null);
  readonly motivo = signal('');

  abrirAnulacion(r: Recibo): void {
    this.anulando.set(r);
    this.motivo.set('');
    this.errorModal.set(null);
  }

  cerrarAnulacion(): void {
    this.anulando.set(null);
  }

  async confirmarAnulacion(): Promise<void> {
    const recibo = this.anulando();
    if (!recibo || this.guardando()) return;
    if (!this.motivo().trim()) {
      this.errorModal.set('Escribe el motivo de la anulación.');
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      await this.ventasService.anularRecibo(this.venta().id, recibo.id, this.motivo().trim());
      this.anulando.set(null);
      this.toast.success('Recibo anulado.');
      this.cambio.emit();
    } catch (e) {
      this.errorModal.set(mensajeDe(e, 'No se pudo anular el recibo.'));
    } finally {
      this.guardando.set(false);
    }
  }
}

function mensajeDe(e: unknown, defecto: string): string {
  if (e instanceof HttpErrorResponse) {
    if (typeof e.error?.message === 'string') return e.error.message;
    if (e.error && typeof e.error === 'object' && Object.keys(e.error).length > 0) return 'Revisa los datos del recibo.';
  }
  return defecto;
}
