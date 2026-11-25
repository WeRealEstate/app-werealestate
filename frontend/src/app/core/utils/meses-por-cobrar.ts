import { FilaAmortizacion } from '../models/amortizacion.model';

/**
 * Qué mensualidades puede cobrar el formulario de abonos y cuánto sugerir. Sale de la tabla mes con mes
 * ({@code detalleMensual}), la misma que se ve en el detalle de la venta: cada abono cuenta en el mes que cubre, así que
 * lo pagado de más en un mes NO da por cubiertos los meses que siguen (se descuenta de las últimas mensualidades).
 */

const TOLERANCIA = 0.01;
const EXTEMPORANEO = 'Pago extemporáneo';

const centavos = (n: number) => Math.round(n * 100) / 100;

/** Renglones del plan: fuera quedan el «Sin fecha en el plan» y los pagos extemporáneos hechos después del plazo. */
function renglonesDelPlan(tabla: FilaAmortizacion[]): FilaAmortizacion[] {
  return tabla.filter((f) => f.fecha !== null && f.concepto !== EXTEMPORANEO);
}

function falta(f: FilaAmortizacion): number {
  if (f.estado === 'PAGADO' || f.estado === 'PAGADO_CON_RETRASO') return 0;
  const resto = centavos(f.monto - f.pagado);
  return resto > TOLERANCIA ? resto : 0;
}

const esMensualidad = (f: FilaAmortizacion) => f.numero !== null && f.concepto === 'Mensualidad';
const claveDe = (f: FilaAmortizacion) => f.fecha!.slice(0, 7);

/**
 * Meses ('yyyy-MM') cuya mensualidad aún se debe.
 *
 * Lo que le falta a un mes vencido pasa a la mensualidad siguiente. Si más adelante esa cadena quedó pagada, la deuda
 * del mes ya se saldó (aunque la tabla lo siga mostrando Abonado o Atrasado) y no se vuelve a ofrecer.
 *
 * @param corte fecha del estado de cuenta (yyyy-MM-dd): son vencidos los meses anteriores a ella
 * @param saldo saldo de la venta: liquidada, no queda ningún mes por cobrar
 */
export function mensualidadesPorCobrar(tabla: FilaAmortizacion[], corte: string, saldo: number): string[] {
  if (saldo <= TOLERANCIA) return [];
  const filas = renglonesDelPlan(tabla);
  const claves = new Set<string>();
  let saldadaDespues = false; // la deuda del renglón siguiente ya quedó cubierta (por él o por uno posterior)
  let siguiente: FilaAmortizacion | null = null;
  for (let i = filas.length - 1; i >= 0; i--) {
    const f = filas[i];
    const saldada: boolean = f.fecha! < corte && siguiente !== null && (falta(siguiente) === 0 || saldadaDespues);
    if (esMensualidad(f) && falta(f) > 0 && !saldada) claves.add(claveDe(f));
    saldadaDespues = saldada;
    siguiente = f;
  }
  return [...claves].sort();
}

/**
 * Lo que falta para cubrir los meses elegidos. Como el faltante de un mes vencido ya va sumado en el siguiente, no se
 * cuenta dos veces cuando se eligen ambos.
 *
 * @param elegidos meses de la línea que se está llenando
 * @param enOtrasLineas meses que ya cobran otras líneas del mismo recibo
 */
export function montoPorMeses(
  tabla: FilaAmortizacion[],
  corte: string,
  elegidos: readonly string[],
  enOtrasLineas: readonly string[] = [],
): number {
  let suma = 0;
  let incluido = 0; // faltante ya contado que viene arrastrado en los renglones siguientes
  for (const f of renglonesDelPlan(tabla)) {
    const debe = falta(f);
    if (esMensualidad(f) && debe > 0) {
      const propio = elegidos.includes(claveDe(f));
      if (propio) suma += Math.max(0, debe - incluido);
      if (propio || enOtrasLineas.includes(claveDe(f))) incluido = Math.max(incluido, debe);
    }
    if (debe === 0 || f.fecha! >= corte) incluido = 0;
  }
  return centavos(suma);
}
