import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LineaReciboRequest, PagoVenta, TIPO_ABONO_LABELS, TipoAbono, Venta } from '../../core/models/venta.model';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';
import { MesPlan, claveMes as clave, etiquetaMes, mesesDelPlan } from '../../core/utils/plan-mensualidades';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

interface LineaForm {
  uid: number;
  tipo: TipoAbono | '';
  monto: number | null;
  /** Claves 'yyyy-MM' de los meses que cubre (solo MENSUALIDAD). */
  meses: string[];
  aportacionId: number | null;
  /** true cuando el monto se escribió a mano: deja de sugerirse solo. */
  montoManual: boolean;
  verTodosLosMeses: boolean;
}

/** Cuántos meses pendientes se muestran antes de pedir "Ver todos". */
const MESES_VISIBLES = 12;

/** Formulario para registrar un recibo de abono de una venta: folio, fecha, nota y una o más líneas
 * (mensualidad, enganche, apartado o aportación). Lo usan el detalle de la venta y el modal del
 * lote; al guardar avisa con `registrado` para que el padre vuelva a cargar la venta y sus pagos. */
@Component({
  selector: 'app-recibo-form',
  standalone: true,
  imports: [FormsModule, MonedaInputDirective],
  templateUrl: './recibo-form.component.html',
})
export class ReciboFormComponent {
  private readonly ventasService = inject(VentasService);
  private readonly toast = inject(ToastService);

  readonly venta = input.required<Venta>();
  readonly pagos = input.required<PagoVenta[]>();
  readonly registrado = output<void>();

  readonly fecha = signal(hoy());
  readonly folio = signal('');
  readonly notas = signal('');
  readonly lineas = signal<LineaForm[]>([nuevaLinea()]);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  readonly tiposDisponibles = computed(() => {
    const v = this.venta();
    const tipos: TipoAbono[] = [];
    if (v.plazoMeses && v.mensualidad) tipos.push('MENSUALIDAD');
    if (v.enganche) tipos.push('ENGANCHE');
    tipos.push('APARTADO');
    if (v.aportaciones.length > 0) tipos.push('APORTACION');
    return tipos.map((valor) => ({ valor, label: TIPO_ABONO_LABELS[valor] }));
  });

  /** Todos los meses del plazo de la venta. */
  private readonly mesesDelPlan = computed<MesPlan[]>(() => mesesDelPlan(this.venta()));

  /** Meses ya cubiertos por una mensualidad registrada. */
  private readonly mesesCubiertos = computed(() => {
    const cubiertos = new Set<string>();
    for (const p of this.pagos()) {
      if (p.tipo === 'MENSUALIDAD' && !p.anulado) for (const m of p.meses) cubiertos.add(clave(m.anio, m.mes));
    }
    return cubiertos;
  });

  readonly mesesPendientes = computed(() => this.mesesDelPlan().filter((m) => !this.mesesCubiertos().has(m.clave)));

  /** Aportaciones programadas con su estado (si ya tienen un pago registrado). */
  readonly aportacionesOpciones = computed(() => {
    const pagadas = new Set(this.pagos().filter((p) => p.tipo === 'APORTACION' && !p.anulado).map((p) => p.aportacionId));
    return this.venta().aportaciones.map((a) => ({
      id: a.id!,
      monto: a.monto,
      etiqueta: `${etiquetaMes(a.anio, a.mes)} · $${a.monto.toLocaleString('en-US')}${pagadas.has(a.id!) ? ' (ya pagada)' : ''}`,
    }));
  });

  readonly totalRecibo = computed(() => this.lineas().reduce((suma, l) => suma + (l.monto ?? 0), 0));

  mesesVisibles(linea: LineaForm): MesPlan[] {
    const pendientes = this.mesesPendientes();
    if (linea.verTodosLosMeses) return pendientes;
    // Los ya elegidos siempre se muestran, aunque queden fuera de la ventana de 12.
    return pendientes.filter((m, i) => i < MESES_VISIBLES || linea.meses.includes(m.clave));
  }

  hayMasMeses(linea: LineaForm): boolean {
    return !linea.verTodosLosMeses && this.mesesPendientes().length > MESES_VISIBLES;
  }

  /** Un mes ya elegido en otra línea de este mismo recibo no se puede elegir otra vez. */
  mesEnOtraLinea(indice: number, claveMes: string): boolean {
    return this.lineas().some((l, i) => i !== indice && l.meses.includes(claveMes));
  }

  agregarLinea(): void {
    this.lineas.update((ls) => [...ls, nuevaLinea()]);
  }

  quitarLinea(indice: number): void {
    this.lineas.update((ls) => (ls.length > 1 ? ls.filter((_, i) => i !== indice) : ls));
  }

  cambiarTipo(indice: number, tipo: TipoAbono | ''): void {
    this.actualizarLinea(indice, (l) => {
      l.tipo = tipo;
      l.meses = [];
      l.aportacionId = null;
      l.montoManual = false;
      l.monto = null;
      if (tipo === 'MENSUALIDAD') {
        // Por defecto, el primer mes pendiente que no esté ya elegido en otra línea.
        const primero = this.mesesPendientes().find((m) => !this.mesEnOtraLinea(indice, m.clave));
        if (primero) l.meses = [primero.clave];
        this.sugerirMonto(l);
      } else if (tipo === 'APORTACION') {
        const sinPagar = this.aportacionesOpciones().find((o) => !o.etiqueta.endsWith('(ya pagada)'));
        if (sinPagar) {
          l.aportacionId = sinPagar.id;
          l.monto = sinPagar.monto;
        }
      }
    });
  }

  alternarMes(indice: number, claveMes: string): void {
    this.actualizarLinea(indice, (l) => {
      l.meses = l.meses.includes(claveMes) ? l.meses.filter((c) => c !== claveMes) : [...l.meses, claveMes];
      this.sugerirMonto(l);
    });
  }

  verTodos(indice: number): void {
    this.actualizarLinea(indice, (l) => (l.verTodosLosMeses = true));
  }

  cambiarAportacion(indice: number, id: number | null): void {
    this.actualizarLinea(indice, (l) => {
      l.aportacionId = id;
      const opcion = this.aportacionesOpciones().find((o) => o.id === id);
      if (opcion && !l.montoManual) l.monto = opcion.monto;
    });
  }

  cambiarMonto(indice: number, monto: number | null): void {
    this.actualizarLinea(indice, (l) => {
      l.monto = monto;
      l.montoManual = true;
    });
  }

  async registrar(): Promise<void> {
    if (this.guardando()) return;
    const mensaje = this.validar();
    if (mensaje) {
      this.error.set(mensaje);
      return;
    }

    this.guardando.set(true);
    this.error.set(null);
    try {
      await this.ventasService.registrarPago(this.venta().id, {
        fecha: this.fecha(),
        folio: this.folio().trim(),
        notas: this.notas().trim() || null,
        lineas: this.lineas().map((l) => this.lineaARequest(l)),
      });
      this.reiniciar();
      this.toast.success('Abono registrado.');
      this.registrado.emit();
    } catch (e) {
      const texto =
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : e instanceof HttpErrorResponse && e.error && typeof e.error === 'object' && Object.keys(e.error).length > 0
            ? 'Revisa los datos del abono.'
            : 'No se pudo registrar el abono.';
      this.error.set(texto);
      this.toast.error(texto);
    } finally {
      this.guardando.set(false);
    }
  }

  private validar(): string | null {
    if (!this.fecha()) return 'Indica la fecha del abono.';
    if (!this.folio().trim()) return 'Ingresa el folio del abono.';
    for (const [i, l] of this.lineas().entries()) {
      const n = this.lineas().length > 1 ? ` (concepto ${i + 1})` : '';
      if (!l.tipo) return `Elige el tipo de abono${n}.`;
      if (!l.monto || l.monto <= 0) return `Ingresa un monto válido${n}.`;
      if (l.tipo === 'MENSUALIDAD' && l.meses.length === 0) return `Elige el mes o los meses que cubre la mensualidad${n}.`;
      if (l.tipo === 'APORTACION' && l.aportacionId === null) return `Elige qué aportación programada paga${n}.`;
    }
    return null;
  }

  private lineaARequest(l: LineaForm): LineaReciboRequest {
    const base = { tipo: l.tipo as TipoAbono, monto: l.monto! };
    if (l.tipo === 'MENSUALIDAD') {
      return {
        ...base,
        meses: l.meses.map((c) => {
          const [anio, mes] = c.split('-').map(Number);
          return { anio, mes };
        }),
      };
    }
    if (l.tipo === 'APORTACION') return { ...base, aportacionId: l.aportacionId };
    return base;
  }

  /** Sugiere mensualidad × meses elegidos mientras el monto no se haya escrito a mano. */
  private sugerirMonto(l: LineaForm): void {
    if (l.montoManual) return;
    const mensualidad = this.venta().mensualidad ?? 0;
    l.monto = l.meses.length > 0 ? Math.round(mensualidad * l.meses.length * 100) / 100 : null;
  }

  private actualizarLinea(indice: number, cambio: (l: LineaForm) => void): void {
    this.lineas.update((ls) =>
      ls.map((l, i) => {
        if (i !== indice) return l;
        const copia = { ...l, meses: [...l.meses] };
        cambio(copia);
        return copia;
      }),
    );
  }

  private reiniciar(): void {
    this.fecha.set(hoy());
    this.folio.set('');
    this.notas.set('');
    this.lineas.set([nuevaLinea()]);
  }
}

let siguienteUid = 0;

function nuevaLinea(): LineaForm {
  return { uid: siguienteUid++, tipo: '', monto: null, meses: [], aportacionId: null, montoManual: false, verTodosLosMeses: false };
}

function hoy(): string {
  return new Date().toISOString().slice(0, 10);
}
