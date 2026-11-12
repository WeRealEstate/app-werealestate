import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Amortizacion } from '../../core/models/amortizacion.model';
import { LineaReciboRequest, PagoVenta, TIPO_ABONO_LABELS, TipoAbono, Venta, esTipoMensual } from '../../core/models/venta.model';
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
  /** Solo MENSUALIDAD: pago fuera del plazo del plan; sin meses. */
  extemporaneo: boolean;
  /** Solo APARTADO: true = se aplica a la mensualidad de uno o varios meses; false = va a la deuda total. */
  aplicarAMes: boolean;
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

  /** Los cinco tipos siempre salen; los que esta venta no puede recibir se muestran deshabilitados
   * con la razón (por ejemplo, una venta de contado no tiene mensualidades). */
  readonly tiposDisponibles = computed(() => {
    const v = this.venta();
    const tienePlan = !!(v.plazoMeses && v.mensualidad);
    const tieneAportaciones = v.aportaciones.length > 0;
    return [
      { valor: 'MENSUALIDAD' as TipoAbono, label: TIPO_ABONO_LABELS.MENSUALIDAD, deshabilitado: !tienePlan, motivo: 'esta venta no tiene mensualidades' },
      { valor: 'A_CUENTA_COMISION' as TipoAbono, label: TIPO_ABONO_LABELS.A_CUENTA_COMISION, deshabilitado: !tienePlan, motivo: 'esta venta no tiene mensualidades' },
      { valor: 'ENGANCHE' as TipoAbono, label: TIPO_ABONO_LABELS.ENGANCHE, deshabilitado: false, motivo: '' },
      { valor: 'APARTADO' as TipoAbono, label: TIPO_ABONO_LABELS.APARTADO, deshabilitado: false, motivo: '' },
      { valor: 'APORTACION' as TipoAbono, label: TIPO_ABONO_LABELS.APORTACION, deshabilitado: !tieneAportaciones, motivo: 'esta venta no tiene aportaciones programadas' },
    ];
  });

  /** Todos los meses del plazo de la venta. */
  private readonly mesesDelPlan = computed<MesPlan[]>(() => mesesDelPlan(this.venta()));

  /** Meses ya cubiertos por una mensualidad registrada. */
  private readonly mesesCubiertos = computed(() => {
    const cubiertos = new Set<string>();
    for (const p of this.pagos()) {
      if ((esTipoMensual(p.tipo) || p.tipo === 'APARTADO') && !p.anulado) for (const m of p.meses) cubiertos.add(clave(m.anio, m.mes));
    }
    return cubiertos;
  });

  /** Tabla de amortización de la venta (la misma que ve el usuario en el detalle). Se vuelve a pedir cada vez que
   * cambian sus pagos. null mientras carga o si no se pudo pedir: entonces se usan los meses de los recibos. */
  private readonly amortizacion = signal<Amortizacion | null>(null);
  private pedidoAmortizacion = 0;

  constructor() {
    effect(() => {
      const id = this.venta().id;
      this.pagos();
      const pedido = ++this.pedidoAmortizacion;
      this.ventasService
        .amortizacion(id)
        .then((a) => {
          if (pedido === this.pedidoAmortizacion) this.amortizacion.set(a);
        })
        .catch(() => {
          if (pedido === this.pedidoAmortizacion) this.amortizacion.set(null);
        });
    });
  }

  /** Lo que aún falta de cada mensualidad según la tabla (clave 'yyyy-MM' → monto): las que no salen Pagadas.
   * null si la tabla no está disponible. Un mes que un recibo ya marcó puede seguir faltando si se pagó de menos. */
  private readonly faltantePorMes = computed<Map<string, number> | null>(() => {
    const a = this.amortizacion();
    if (!a) return null;
    const faltante = new Map<string, number>();
    for (const f of a.filas) {
      if (f.numero === null || f.fecha === null || f.concepto !== 'Mensualidad' || f.estado === 'PAGADO' || f.estado === 'PAGADO_CON_RETRASO') continue;
      faltante.set(f.fecha.slice(0, 7), Math.max(0, Math.round((f.monto - f.pagado) * 100) / 100));
    }
    return faltante;
  });

  /** Meses que se pueden cobrar: los que la tabla de amortización aún muestra sin cubrir del todo. Si la tabla no está
   * disponible, los que ningún recibo ha marcado como cubiertos. */
  readonly mesesPendientes = computed(() => {
    const faltante = this.faltantePorMes();
    // Con la tabla, sus meses tal cual: si hubo una pausa de mensualidades ya vienen recorridos (el plan calculado aquí
    // con los datos de la venta no la conoce).
    if (faltante) {
      return [...faltante.keys()].sort().map((k) => {
        const [anio, mes] = k.split('-').map(Number);
        return { clave: k, anio, mes, etiqueta: etiquetaMes(anio, mes) } satisfies MesPlan;
      });
    }
    return this.mesesDelPlan().filter((m) => !this.mesesCubiertos().has(m.clave));
  });

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
      l.extemporaneo = false;
      l.aplicarAMes = false;
      l.montoManual = false;
      l.monto = null;
      if (esTipoMensual(tipo)) {
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

  /** Pago extemporáneo: el cliente ya pasó el plazo (o ya tiene todos los meses cubiertos) y aún debe saldo. Sin meses,
   * solo monto; al desmarcarlo vuelve a pedir los meses. */
  alternarExtemporaneo(indice: number, valor: boolean): void {
    this.actualizarLinea(indice, (l) => {
      l.extemporaneo = valor;
      l.meses = [];
      l.montoManual = false;
      if (valor) {
        l.monto = null;
      } else {
        const primero = this.mesesPendientes().find((m) => !this.mesEnOtraLinea(indice, m.clave));
        if (primero) l.meses = [primero.clave];
        this.sugerirMonto(l);
      }
    });
  }

  /** Apartado: a la deuda total (por defecto) o a la mensualidad de uno o varios meses. El monto no cambia: es el
   * dinero que se recibió. Para la comisión sigue contando como apartado. */
  alternarAplicarAMes(indice: number, valor: boolean): void {
    this.actualizarLinea(indice, (l) => {
      l.aplicarAMes = valor;
      l.meses = [];
      if (valor) {
        const primero = this.mesesPendientes().find((m) => !this.mesEnOtraLinea(indice, m.clave));
        if (primero) l.meses = [primero.clave];
      }
    });
  }

  /** true si la línea debe elegir meses: mensualidad normal o apartado aplicado a un mes. */
  pideMeses(l: LineaForm): boolean {
    return (esTipoMensual(l.tipo) && !l.extemporaneo) || (l.tipo === 'APARTADO' && l.aplicarAMes);
  }

  /** El apartado solo puede ir a un mes si la venta tiene mensualidades. */
  readonly ventaConMensualidades = computed(() => !!(this.venta().plazoMeses && this.venta().mensualidad));

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
      if (esTipoMensual(l.tipo) && !l.extemporaneo && l.meses.length === 0) return `Elige el mes o los meses que cubre la mensualidad${n}.`;
      if (l.tipo === 'APARTADO' && l.aplicarAMes && l.meses.length === 0) return `Elige el mes o los meses a los que se aplica el apartado${n}.`;
      if (l.tipo === 'APORTACION' && l.aportacionId === null) return `Elige qué aportación programada paga${n}.`;
    }
    return null;
  }

  private lineaARequest(l: LineaForm): LineaReciboRequest {
    const base = { tipo: l.tipo as TipoAbono, monto: l.monto! };
    if (esTipoMensual(l.tipo) && l.extemporaneo) return { ...base, extemporaneo: true };
    if (esTipoMensual(l.tipo) || (l.tipo === 'APARTADO' && l.aplicarAMes)) {
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
    if (l.montoManual || l.tipo === 'APARTADO') return;
    const mensualidad = this.venta().mensualidad ?? 0;
    const faltante = this.faltantePorMes();
    // Con la tabla disponible se sugiere lo que falta de cada mes elegido (puede ser menos o más que la mensualidad).
    const porMes = (c: string) => faltante?.get(c) ?? mensualidad;
    const suma = l.meses.reduce((total, c) => total + porMes(c), 0);
    l.monto = l.meses.length > 0 ? Math.round(suma * 100) / 100 : null;
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
  return { uid: siguienteUid++, tipo: '', monto: null, meses: [], aportacionId: null, extemporaneo: false, aplicarAMes: false, montoManual: false, verTodosLosMeses: false };
}

function hoy(): string {
  return new Date().toISOString().slice(0, 10);
}
