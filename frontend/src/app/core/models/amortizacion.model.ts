/** Cómo va un pago de la tabla de amortización (ver backend service/CalendarioPagos): ya cubierto por lo abonado,
 * vencido sin cubrir (desde el día siguiente a su fecha), el siguiente que toca o uno de los que siguen. */
export type EstadoPagoPlan = 'PAGADO' | 'ATRASADO' | 'LE_TOCA' | 'PENDIENTE';

export const ESTADO_PAGO_PLAN_LABELS: Record<EstadoPagoPlan, string> = {
  PAGADO: 'Pagado',
  ATRASADO: 'Atrasado',
  LE_TOCA: 'Le toca',
  PENDIENTE: 'Pendiente',
};

export const ESTADO_PAGO_PLAN_CLASES: Record<EstadoPagoPlan, string> = {
  PAGADO: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  ATRASADO: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  LE_TOCA: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300',
  PENDIENTE: 'bg-surface-2 text-ink-muted',
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
  filas: FilaAmortizacion[];
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
