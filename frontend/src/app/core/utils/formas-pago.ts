import { Aportacion, aniosElegibles, esElegible, mesesDelPlan } from './aportaciones';

/** Formas de pago combinables: las usan el Cotizador, Registrar venta y We Forms para elegir, bloquear y
 * nombrar igual. Hay una base (Sin enganche, Con enganche, Pago inicial o Contado) y, opcional, un extra
 * (Con anualidades o Con aportaciones). Contado va solo. Lo que no se combina con lo ya marcado queda
 * bloqueado hasta desmarcarlo: la selección nunca cambia sola. Un extra sin base cuenta como sin enganche,
 * que es lo que siempre hizo "Con anualidades" sola. */
export type BasePago = 'msi' | 'downpayment' | 'initial' | 'cash';
export type ExtraPago = 'annualities' | 'aportaciones';
export type TipoPago = BasePago | ExtraPago;

export interface SeleccionPago {
  base: BasePago | null;
  extra: ExtraPago | null;
}

export const SIN_FORMA_PAGO: SeleccionPago = { base: null, extra: null };

export function esBase(tipo: TipoPago): tipo is BasePago {
  return tipo === 'msi' || tipo === 'downpayment' || tipo === 'initial' || tipo === 'cash';
}

export function estaMarcado(sel: SeleccionPago, tipo: TipoPago): boolean {
  return sel.base === tipo || sel.extra === tipo;
}

/** true si `tipo` no se puede marcar con lo que ya está marcado: otra base, el otro extra, o Contado con
 * cualquier otra cosa. Lo marcado nunca está bloqueado (siempre se puede desmarcar). */
export function estaBloqueado(sel: SeleccionPago, tipo: TipoPago): boolean {
  if (estaMarcado(sel, tipo)) return false;
  if (esBase(tipo)) return sel.base !== null || (tipo === 'cash' && sel.extra !== null);
  return sel.extra !== null || sel.base === 'cash';
}

/** Marca o desmarca `tipo`; si está bloqueado, deja la selección como estaba. */
export function alternarFormaPago(sel: SeleccionPago, tipo: TipoPago): SeleccionPago {
  if (estaBloqueado(sel, tipo)) return sel;
  if (esBase(tipo)) return { ...sel, base: sel.base === tipo ? null : tipo };
  return { ...sel, extra: sel.extra === tipo ? null : tipo };
}

export function hayFormaPago(sel: SeleccionPago): boolean {
  return sel.base !== null || sel.extra !== null;
}

/** Con enganche o pago inicial: hay un monto de entrada que se resta antes de repartir el saldo. */
export function llevaEntrada(sel: SeleccionPago): boolean {
  return sel.base === 'downpayment' || sel.base === 'initial';
}

/** 'Enganche' / 'Pago inicial', o null si no hay monto de entrada. */
export function etiquetaEntrada(sel: SeleccionPago): string | null {
  if (sel.base === 'downpayment') return 'Enganche';
  if (sel.base === 'initial') return 'Pago inicial';
  return null;
}

/** "MSI" solo para Sin enganche y Con enganche sin extra (como siempre); lo demás va en "meses". */
export function unidadPlazo(sel: SeleccionPago): 'MSI' | 'meses' {
  return !sel.extra && (sel.base === 'msi' || sel.base === 'downpayment') ? 'MSI' : 'meses';
}

/** Nombre de la forma de pago para el resumen, el PDF, el historial y la venta: "Con enganche · 36 MSI",
 * "Con enganche + anualidades · 36 meses", "Con aportaciones · 24 meses", "Contado". */
export function etiquetaFormaPago(sel: SeleccionPago, plazo: number | null): string {
  if (!hayFormaPago(sel)) return '';
  if (sel.base === 'cash') return 'Contado';
  const extra = sel.extra === 'annualities' ? 'anualidades' : sel.extra === 'aportaciones' ? 'aportaciones' : null;
  let nombre: string;
  if (sel.base === 'downpayment' || sel.base === 'initial') {
    nombre = (sel.base === 'downpayment' ? 'Con enganche' : 'Pago inicial') + (extra ? ` + ${extra}` : '');
  } else {
    nombre = extra ? `Con ${extra}` : 'Sin enganche';
  }
  return plazo ? `${nombre} · ${plazo} ${unidadPlazo(sel)}` : nombre;
}

/** Máximo de anualidades para un plazo: deja un último año de mensualidades regulares (la misma regla del
 * Cotizador desde siempre). */
export function maximoAnualidades(meses: number): number {
  return Math.max(Math.floor(meses / 12) - 1, 0);
}

/** Las anualidades como aportaciones programadas (así caben junto al enganche en la venta): una por año en
 * `mes`, en orden desde `inicio`, hasta `cantidad`, y solo en meses donde se puede poner una aportación
 * (nunca en el último año calendario del plazo, igual que valida el servidor). */
export function anualidadesComoAportaciones(
  inicio: Date,
  meses: number,
  mes: number,
  monto: number,
  cantidad: number,
): Aportacion[] {
  const anios = aniosElegibles(inicio, meses);
  return mesesDelPlan(inicio, meses)
    .filter((m) => m.mes === mes && esElegible(m, anios))
    .slice(0, Math.max(cantidad, 0))
    .map((m) => ({ anio: m.anio, mes: m.mes, monto }));
}
