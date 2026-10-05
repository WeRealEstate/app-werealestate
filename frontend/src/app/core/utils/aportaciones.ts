/** Aportaciones: varios pagos extra por año, cada uno en su propio mes y con su propio monto
 * (a diferencia de las anualidades, que son una por año en un mismo mes y del mismo monto).
 * Lo usan el Cotizador, Registrar venta y el PDF, para que los tres calculen igual. */

export interface Aportacion {
  anio: number;
  /** 1 = enero … 12 = diciembre. */
  mes: number;
  monto: number | null;
  /** true si el monto se capturó a mano: el autocompletado ya no lo pisa. */
  manual?: boolean;
}

export interface AnioElegible {
  anio: number;
  /** Meses (1-12) de ese año que caen dentro del plan y donde se puede poner una aportación. */
  meses: number[];
}

export interface FilaConAportaciones {
  paymentNumber: number;
  month: string;
  year: number;
  payment: number;
  balance: number;
  accumulatedPayment: number;
  esAportacion: boolean;
}

export const MESES_NOMBRE = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

export const MESES_ABREVIADO = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

export function claveAportacion(anio: number, mes: number): string {
  return `${anio}-${mes}`;
}

export function ordenarAportaciones<T extends { anio: number; mes: number }>(lista: T[]): T[] {
  return [...lista].sort((a, b) => a.anio - b.anio || a.mes - b.mes);
}

/** Los meses del calendario del plan: el 0 es el mes de `inicio` (el primer pago) y así hasta `meses`. */
export function mesesDelPlan(inicio: Date, meses: number): { anio: number; mes: number }[] {
  const resultado: { anio: number; mes: number }[] = [];
  for (let i = 0; i < meses; i++) {
    const fecha = new Date(inicio.getFullYear(), inicio.getMonth() + i, 1);
    resultado.push({ anio: fecha.getFullYear(), mes: fecha.getMonth() + 1 });
  }
  return resultado;
}

/** Dónde se pueden poner aportaciones: años calendario (enero a diciembre) del plan, solo con los
 * meses que caen dentro del plan, y SIN el último año calendario — la misma regla que ya tenían las
 * anualidades (un último tramo de mensualidades regulares antes de terminar de pagar). */
export function aniosElegibles(inicio: Date, meses: number): AnioElegible[] {
  const plan = mesesDelPlan(inicio, meses);
  if (plan.length === 0) return [];

  const ultimoAnio = plan[plan.length - 1].anio;
  const porAnio = new Map<number, number[]>();
  for (const { anio, mes } of plan) {
    if (anio === ultimoAnio) continue;
    porAnio.set(anio, [...(porAnio.get(anio) ?? []), mes]);
  }
  return [...porAnio.entries()].map(([anio, ms]) => ({ anio, meses: ms }));
}

export function esElegible(aportacion: { anio: number; mes: number }, anios: AnioElegible[]): boolean {
  return anios.some((a) => a.anio === aportacion.anio && a.meses.includes(aportacion.mes));
}

export function totalAportaciones(aportaciones: Aportacion[]): number {
  return aportaciones.reduce((suma, a) => suma + (a.monto ?? 0), 0);
}

/** Mensualidad regular = lo que falta después de las aportaciones, entre los meses que NO llevan una
 * (en un mes con aportación se paga solo la aportación). 0 si no queda saldo o meses regulares. */
export function mensualidadConAportaciones(total: number, aportaciones: Aportacion[], meses: number): number {
  const regulares = meses - aportaciones.length;
  const saldo = total - totalAportaciones(aportaciones);
  if (regulares <= 0 || saldo <= 0) return 0;
  return saldo / regulares;
}

/** true si las aportaciones se pueden usar: al menos una, todas con monto, y que sumadas dejen saldo
 * para las mensualidades regulares. */
export function aportacionesValidas(total: number, aportaciones: Aportacion[], meses: number): boolean {
  if (aportaciones.length === 0) return false;
  if (aportaciones.some((a) => a.monto === null || a.monto <= 0)) return false;
  return mensualidadConAportaciones(total, aportaciones, meses) > 0;
}

/** Tabla de pagos mes a mes: en cada mes con aportación se paga ese monto; en los demás, la
 * mensualidad. El último mes liquida lo que reste y nunca se paga más que el saldo. */
export function tablaConAportaciones(
  inicio: Date,
  meses: number,
  total: number,
  mensualidad: number,
  aportaciones: Aportacion[],
  nombresMes: string[],
): FilaConAportaciones[] {
  const montos = new Map(aportaciones.map((a) => [claveAportacion(a.anio, a.mes), a.monto ?? 0]));
  const filas: FilaConAportaciones[] = [];
  let saldo = total;
  let acumulado = 0;

  for (let i = 0; i < meses; i++) {
    const fecha = new Date(inicio.getFullYear(), inicio.getMonth() + i, 1);
    const clave = claveAportacion(fecha.getFullYear(), fecha.getMonth() + 1);
    const esAportacion = montos.has(clave);

    let pago = esAportacion ? (montos.get(clave) as number) : mensualidad;
    if (i === meses - 1 || pago > saldo) pago = saldo;

    saldo -= pago;
    if (saldo < 0.01) saldo = 0;
    acumulado += pago;

    filas.push({
      paymentNumber: i + 1,
      month: nombresMes[fecha.getMonth()],
      year: fecha.getFullYear(),
      payment: pago,
      balance: saldo,
      accumulatedPayment: acumulado,
      esAportacion,
    });

    if (saldo <= 0) break;
  }

  return filas;
}
