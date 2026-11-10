/** Cómo va un pago de la tabla de amortización (ver backend service/CalendarioPagos y TablaMensual): cubierto,
 * vencido sin ningún pago (desde el día siguiente a su fecha), el siguiente que toca, uno de los que siguen o, solo en
 * la tabla mes con mes, ABONADO (recibió algún pago pero no toda la mensualidad). */
export type EstadoPagoPlan = 'PAGADO' | 'ATRASADO' | 'LE_TOCA' | 'PENDIENTE' | 'ABONADO';

export const ESTADO_PAGO_PLAN_LABELS: Record<EstadoPagoPlan, string> = {
  PAGADO: 'Pagado',
  ATRASADO: 'Atrasado',
  LE_TOCA: 'Paga este mes',
  PENDIENTE: 'Pendiente',
  ABONADO: 'Abonado',
};

/** Etiqueta del estado de una fila. «Paga este mes» solo si el pago es del mes en curso (el del día del estado de
 * cuenta, `corte`); si el siguiente pago sin cubrir cae en un mes posterior se dice «Próximo pago». */
export function etiquetaEstadoPago(estado: EstadoPagoPlan, fecha: string | null, corte: string): string {
  if (estado === 'LE_TOCA' && fecha && fecha.slice(0, 7) !== corte.slice(0, 7)) return 'Próximo pago';
  return ESTADO_PAGO_PLAN_LABELS[estado];
}

export const ESTADO_PAGO_PLAN_CLASES: Record<EstadoPagoPlan, string> = {
  PAGADO: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  ATRASADO: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  LE_TOCA: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300',
  PENDIENTE: 'bg-surface-2 text-ink-muted',
  ABONADO: 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300',
};

/** Un pago del plan. numero: su lugar en el plazo (null en enganche, contado, aportación fuera del plazo y lo que
 * no tiene fecha); fecha null = parte del precio sin fecha en el plan. saldo: lo que falta del precio después de
 * este pago según el plan. */
export interface FilaAmortizacion {
  numero: number | null;
  fecha: string | null;
  concepto: string;
  monto: number;
  pagado: number;
  saldo: number;
  estado: EstadoPagoPlan;
  /** Faltante de una mensualidad vencida pagada en parte que se pasó al pago siguiente: negativo en la fila que lo cedió
   * (su monto es lo que se alcanzó a pagar) y positivo en la que lo recibe (su monto lo incluye). */
  arrastre: number;
  /** Dinero realmente recibido en el mes de este pago (por la fecha de cada recibo vigente); informativo. */
  abonadoMes: number;
  /** Cada abono vigente de ese mes con su fecha (solo en detalleMensual). */
  pagos: PagoMes[];
}

/** Un abono con su fecha. */
export interface PagoMes {
  fecha: string;
  monto: number;
  /** true si el abono es del tipo «A cuenta de comisión» (solo se distingue con una etiqueta). */
  aCuentaComision: boolean;
}

/** Respuesta de GET /api/ventas/{id}/amortizacion y de cada elemento de GET /api/clientes/{id}/amortizaciones. */
export interface Amortizacion {
  ventaId: number;
  ventaNumero: number;
  fechaVenta: string;
  /** El titular: nombre, su número en el desarrollo de la venta ("S-40") y teléfono. */
  clienteId: number | null;
  cliente: string;
  clienteNumeros: string[];
  clienteTelefono: string | null;
  copropietarios: string[];
  /** true si se pidió desde la ficha de un copropietario. */
  copropietario: boolean;
  desarrollos: string[];
  lotes: string[];
  formaPago: string;
  numeroContrato: string | null;
  precio: number;
  engancheLabel: string | null;
  enganche: number | null;
  plazoMeses: number | null;
  mensualidad: number | null;
  /** 0 en contado. */
  diaPago: number;
  /** Fecha del estado de cuenta (hoy). */
  corte: string;
  pagado: number;
  saldo: number;
  atrasoMonto: number;
  pagosAtrasados: number;
  mensualidadesAtrasadas: number;
  atrasadoDesde: string | null;
  proximaFecha: string | null;
  proximoMonto: number | null;
  /** Reparto del dinero del más antiguo al más nuevo (lo usa el formulario de abonos). */
  filas: FilaAmortizacion[];
  /** La tabla mes con mes (la que se muestra y va al PDF): mensualidad esperada más lo atrasado, y los abonos de cada mes. */
  detalleMensual: FilaAmortizacion[];
}

/** Concepto del renglón que junta la parte del precio sin fecha en el plan (anualidades de ventas anteriores). */
export const CONCEPTO_SIN_FECHA = 'Sin fecha en el plan';

/** "2 mensualidades" si todo lo atrasado son mensualidades; "2 pagos" si hay enganche, aportación o contado. */
export function cuentaAtraso(pagos: number, mensualidades: number): string {
  if (pagos <= 0) return '';
  const soloMensualidades = pagos === mensualidades;
  const palabra = soloMensualidades ? (pagos === 1 ? 'mensualidad' : 'mensualidades') : pagos === 1 ? 'pago' : 'pagos';
  return `${pagos} ${palabra}`;
}

/** "2 mensualidades atrasadas" / "1 pago atrasado". */
export function frasePagosAtrasados(pagos: number, mensualidades: number): string {
  if (pagos <= 0) return '';
  const adjetivo = pagos === mensualidades ? (pagos === 1 ? 'atrasada' : 'atrasadas') : pagos === 1 ? 'atrasado' : 'atrasados';
  return `${cuentaAtraso(pagos, mensualidades)} ${adjetivo}`;
}

/** dd/mm/aaaa a partir de "aaaa-mm-dd" (sin pasar por Date: así no se corre un día por la zona horaria). */
export function fechaCorta(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}
