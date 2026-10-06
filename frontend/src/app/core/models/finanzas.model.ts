/** Cómo va el cliente con lo que le toca pagar este mes. */
export type EstadoPagoCliente = 'YA_ABONO' | 'ABONO_PARCIAL' | 'POR_VENCER' | 'SIN_ABONAR';

export const ESTADO_PAGO_CLIENTE_LABELS: Record<EstadoPagoCliente, string> = {
  YA_ABONO: 'Ya abonó este mes',
  ABONO_PARCIAL: 'Abono parcial',
  POR_VENCER: 'Aún no abona',
  SIN_ABONAR: 'Sin abonar',
};

export const ESTADO_PAGO_CLIENTE_CLASES: Record<EstadoPagoCliente, string> = {
  YA_ABONO: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  ABONO_PARCIAL: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  POR_VENCER: 'bg-surface-2 text-ink-muted',
  SIN_ABONAR: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
};

export type ModalidadComision = 'UNA_EXHIBICION' | 'PARCIALIDADES';
export type EstadoComision = 'PENDIENTE' | 'ACUMULANDO' | 'PARCIAL' | 'PAGADA' | 'CANCELADA';

export const ESTADO_COMISION_LABELS: Record<EstadoComision, string> = {
  PENDIENTE: 'Pendiente',
  ACUMULANDO: 'Acumulando',
  PARCIAL: 'Parcial',
  PAGADA: 'Pagada',
  CANCELADA: 'Cancelada',
};

export const ESTADO_COMISION_CLASES: Record<EstadoComision, string> = {
  PENDIENTE: 'bg-surface-2 text-ink-muted',
  ACUMULANDO: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  PARCIAL: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  PAGADA: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  CANCELADA: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
};

export const MODALIDAD_COMISION_LABELS: Record<ModalidadComision, string> = {
  UNA_EXHIBICION: 'Una exhibición',
  PARCIALIDADES: 'Parcialidades',
};

/** Comisión de una venta: 5% de su valor por defecto, editable (ver backend ComisionService). */
export interface Comision {
  id: number;
  /** null cuando la venta ya no existe: la comisión se queda en el historial. */
  ventaId: number | null;
  ventaNumero: number | null;
  ventaEliminada: boolean;
  cliente: string;
  asesorNombre: string;
  asesorExterno: boolean;
  base: number;
  /** Mensualidad pactada de la venta; null en una venta de contado o ya eliminada. */
  mensualidad: number | null;
  /** Lo que el cliente lleva abonado en total. */
  abonado: number;
  /** Mensualidades vencidas que el cliente aún no cubre (0 si va al corriente) y a cuánto equivalen. */
  mensualidadesAtrasadas: number;
  montoAtrasadoCliente: number;
  /** Cómo va el cliente este mes; null si este mes no le toca pagar nada. */
  estadoPagoMes: EstadoPagoCliente | null;
  esperadoMes: number;
  recibidoMes: number;
  fechaPagoMes: string | null;
  porcentaje: number;
  monto: number;
  montoManual: boolean;
  modalidad: ModalidadComision;
  estado: EstadoComision;
  cancelada: boolean;
  /** Ganado por los abonos de la venta / ya entregado / ganado y aún sin entregar. */
  devengado: number;
  entregado: number;
  porEntregar: number;
  /** Sábado (yyyy-MM-dd) en que toca entregar lo primero pendiente; null si no hay nada. */
  proximaEntrega: string | null;
  /** Ya pasó el sábado en que tocaba entregar algo ganado y sigue sin entregarse. */
  retrasada: boolean;
  /** Lo que se debe de esas entregas vencidas. */
  montoRetrasado: number;
  fechaCreacion: string;
}

export interface ComisionDevengo {
  fechaOrigen: string;
  fechaEntrega: string;
  monto: number;
}

export interface ComisionEntrega {
  id: number;
  fecha: string;
  monto: number;
  notas: string | null;
  gastoId: number | null;
  registradaPor: string;
  fechaCreacion: string;
}

export interface ComisionDetalle {
  comision: Comision;
  devengos: ComisionDevengo[];
  entregas: ComisionEntrega[];
}

export interface ComisionResumen {
  cantidad: number;
  total: number;
  devengado: number;
  entregado: number;
  porEntregar: number;
  pendienteDeDevengar: number;
  /** Comisiones con entregas vencidas y cuánto se debe por ellas. */
  retrasadas: number;
  montoRetrasado: number;
}

export interface ComisionesPorEntregar {
  sabado: string;
  total: number;
  items: { comision: Comision; montoAlSabado: number }[];
}

/** Valor vendido: suma del precio de los lotes vendidos (ver backend FinanzasService.valorVendido). */
export interface FinanzasValor {
  total: number;
  lotes: number;
  cobrado: number;
  saldoPendiente: number;
  porDesarrollo: { desarrolloId: number; desarrollo: string; lotes: number; valor: number }[];
  detalle: {
    desarrollo: string;
    manzana: string;
    numeroLote: string;
    ventaId: number;
    ventaNumero: number;
    cliente: string;
    precio: number;
  }[];
}

/** mes = "yyyy-MM". atrasoAcumulado = lo esperado hasta ese mes que aún no se recibe (nunca negativo). */
export interface IngresoMes {
  mes: string;
  esperado: number;
  recibido: number;
  diferencia: number;
  atrasoAcumulado: number;
  ventas: number;
  futuro: boolean;
}

export interface FinanzasIngresos {
  mesActual: IngresoMes;
  meses: IngresoMes[];
}

export interface IngresoConcepto {
  ventaId: number;
  ventaNumero: number;
  cliente: string;
  desarrollo: string;
  concepto: string;
  fechaEsperada: string;
  esperado: number;
}

export interface IngresoAbono {
  ventaId: number;
  ventaNumero: number;
  cliente: string;
  fecha: string;
  monto: number;
}

export interface IngresoDetalle {
  mes: string;
  esperados: IngresoConcepto[];
  recibidos: IngresoAbono[];
}
