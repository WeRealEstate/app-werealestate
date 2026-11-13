/** Tablas de amortización del Cotizador que no dependen de las aportaciones elegidas mes por mes (esas viven en
 * core/utils/aportaciones): el financiamiento normal y el esquema "mensualidad + aportación anual". */

export interface AmortizationRow {
  paymentNumber: number;
  month: string;
  year: number;
  payment: number;
  balance: number;
  accumulatedPayment: number;
  /** true en los meses que llevan una aportación (el PDF los resalta). */
  esAportacion?: boolean;
}

export const MESES_MAYUSCULAS = [
  'ENERO',
  'FEBRERO',
  'MARZO',
  'ABRIL',
  'MAYO',
  'JUNIO',
  'JULIO',
  'AGOSTO',
  'SEPTIEMBRE',
  'OCTUBRE',
  'NOVIEMBRE',
  'DICIEMBRE',
];

/** Mensualidades iguales; la última liquida lo que quede del saldo (absorbe el redondeo).
 * El enganche y el pago inicial se cubren de entrada, aparte del calendario de mensualidades: no cuentan como la
 * primera mensualidad, por eso con entrada el conteo de meses empieza el mes siguiente a `hoy`. */
export function tablaFinanciamientoNormal(
  hoy: Date,
  meses: number,
  saldo: number,
  mensualidad: number,
  llevaEntrada: boolean,
): AmortizationRow[] {
  const rows: AmortizationRow[] = [];
  let balance = saldo;
  let accumulatedPayment = 0;
  const startDate = new Date(hoy);

  for (let i = 1; i <= meses; i++) {
    const monthOffset = llevaEntrada ? i : i - 1;
    const paymentDate = new Date(startDate.getFullYear(), startDate.getMonth() + monthOffset, 1);
    const payment = i === meses ? balance : mensualidad;

    balance -= payment;
    if (balance < 0.01) {
      balance = 0;
    }
    accumulatedPayment += payment;

    rows.push({
      paymentNumber: i,
      month: paymentDate.toLocaleString('es-MX', { month: 'long' }).toUpperCase(),
      year: paymentDate.getFullYear(),
      payment,
      balance: Math.max(balance, 0),
      accumulatedPayment,
    });
  }
  return rows;
}

export interface PlanConAnualidad {
  hoy: Date;
  meses: number;
  total: number;
  anualidad: number;
  mensualidad: number;
  /** 1 = enero … 12 = diciembre. */
  mesAnualidad: number;
  numeroAnualidades: number;
  /** 1 cuando hay enganche o pago inicial: ese mes se paga la entrada y el calendario empieza el siguiente. */
  mesesDespues: number;
}

/** Tabla para cualquier esquema de "mensualidad + aportación anual" (Con anualidades y Promoción comparten esta
 * misma mecánica; solo cambia cuál de las dos cantidades es fija y cuál se calcula). Nunca se paga más que el
 * saldo restante y la tabla se corta en cuanto queda liquidado. */
export function tablaConAnualidad(plan: PlanConAnualidad): AmortizationRow[] {
  const rows: AmortizationRow[] = [];
  const startDate = new Date(plan.hoy);
  let balance = plan.total;
  let accumulatedPayment = 0;
  let annualitiesUsed = 0;

  for (let i = 0; i < plan.meses; i++) {
    const paymentDate = new Date(startDate.getFullYear(), startDate.getMonth() + i + plan.mesesDespues, 1);
    const monthNumber = paymentDate.getMonth() + 1;
    const isAnnuality = monthNumber === plan.mesAnualidad && annualitiesUsed < plan.numeroAnualidades;

    let payment = isAnnuality ? plan.anualidad : plan.mensualidad;
    if (payment > balance) {
      payment = balance;
    }

    balance -= payment;
    if (balance < 0.01) {
      balance = 0;
    }
    accumulatedPayment += payment;
    if (isAnnuality) {
      annualitiesUsed++;
    }

    rows.push({
      paymentNumber: i + 1,
      month: MESES_MAYUSCULAS[paymentDate.getMonth()],
      year: paymentDate.getFullYear(),
      payment,
      balance,
      accumulatedPayment,
    });

    if (balance <= 0) {
      break;
    }
  }
  return rows;
}
