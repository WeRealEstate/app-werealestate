import { Venta } from '../models/venta.model';

/** Un mes del plazo de una venta. clave = 'yyyy-MM'. */
export interface MesPlan {
  clave: string;
  anio: number;
  mes: number;
  etiqueta: string;
}

export const claveMes = (anio: number, mes: number): string => `${anio}-${String(mes).padStart(2, '0')}`;

/** "nov 2026" */
export const etiquetaMes = (anio: number, mes: number): string =>
  new Date(anio, mes - 1, 1).toLocaleDateString('es-MX', { month: 'short', year: 'numeric' }).replace('.', '');

/** Todos los meses del plazo de la venta (la primera mensualidad cae el mes de la venta o el
 * siguiente, igual que el calendario de cobros de FinanzasService); vacío si no tiene mensualidades. */
export function mesesDelPlan(venta: Venta): MesPlan[] {
  if (!venta.plazoMeses || !venta.mensualidad) return [];
  const [anio, mes] = venta.fechaVenta.split('-').map(Number);
  const resultado: MesPlan[] = [];
  for (let k = 0; k < venta.plazoMeses; k++) {
    const indice = mes - 1 + (venta.primeraMensualidadMesVenta ? 0 : 1) + k;
    const a = anio + Math.floor(indice / 12);
    const m = (indice % 12) + 1;
    resultado.push({ clave: claveMes(a, m), anio: a, mes: m, etiqueta: etiquetaMes(a, m) });
  }
  return resultado;
}
