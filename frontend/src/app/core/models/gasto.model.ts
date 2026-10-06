/** De dónde viene un gasto: UNICO (compra de una sola vez), RECURRENTE (pago de un gasto con
 * vencimiento como renta o luz) o COMISION (entrega de una comisión de venta). */
export type OrigenGasto = 'UNICO' | 'RECURRENTE' | 'COMISION';

export const ORIGEN_GASTO_LABELS: Record<OrigenGasto, string> = {
  UNICO: 'Único',
  RECURRENTE: 'Recurrente',
  COMISION: 'Comisión',
};

export const ORIGEN_GASTO_CLASES: Record<OrigenGasto, string> = {
  UNICO: 'bg-surface-2 text-ink-muted',
  RECURRENTE: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  COMISION: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
};

/** fecha en formato ISO (yyyy-MM-dd). tieneTicket solo indica si hay un comprobante subido — el
 * archivo en sí se trae con GastosService.verTicket (autenticado, no es una URL pública). */
export interface Gasto {
  id: number;
  concepto: string;
  origen: OrigenGasto;
  fecha: string;
  monto: number;
  tieneTicket: boolean;
  registradoPor: { id: number; nombre: string };
  fechaCreacion: string;
}

export type FrecuenciaGasto = 'SEMANAL' | 'QUINCENAL' | 'MENSUAL' | 'BIMESTRAL' | 'ANUAL';

export const FRECUENCIAS_GASTO: FrecuenciaGasto[] = ['SEMANAL', 'QUINCENAL', 'MENSUAL', 'BIMESTRAL', 'ANUAL'];

export const FRECUENCIA_GASTO_LABELS: Record<FrecuenciaGasto, string> = {
  SEMANAL: 'Semanal',
  QUINCENAL: 'Quincenal',
  MENSUAL: 'Mensual',
  BIMESTRAL: 'Bimestral',
  ANUAL: 'Anual',
};

/** Gasto con vencimiento que se repite (renta, luz, agua, nómina...). dia = día del mes (1-31) salvo
 * en SEMANAL; dia2 = segundo día del mes, solo en QUINCENAL. */
export interface GastoRecurrente {
  id: number;
  nombre: string;
  montoEstimado: number;
  frecuencia: FrecuenciaGasto;
  dia: number | null;
  dia2: number | null;
  primerVencimiento: string;
  activo: boolean;
  /** Vencimiento más próximo aún sin pagar (puede estar en el pasado: vencido); null si no hay. */
  proximoVencimiento: string | null;
  sinPagar: number;
}

export interface GastoRecurrenteRequest {
  nombre: string;
  montoEstimado: number;
  frecuencia: FrecuenciaGasto;
  dia: number | null;
  dia2: number | null;
  primerVencimiento: string;
  /** Omitido = sin cambios al editar. */
  activo?: boolean;
}

/** Un vencimiento sin pagar de un gasto recurrente. */
export interface GastoRecurrentePago {
  id: number;
  recurrenteId: number;
  nombre: string;
  fechaVencimiento: string;
  montoEstimado: number;
  estado: 'PENDIENTE' | 'VENCIDO';
}

/** Resumen del mes (mes = "yyyy-MM"): abonos recibidos, gastos hechos y la diferencia. */
export interface GastoResumen {
  mes: string;
  ingresosMes: number;
  gastadoMes: number;
  gananciaMes: number;
  pendientePorPagar: number;
  vencidoMonto: number;
  vencidosCantidad: number;
}
