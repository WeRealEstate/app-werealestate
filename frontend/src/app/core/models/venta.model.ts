import { Lote } from './lote.model';
import { UsuarioResumen } from './lead.model';

/**
 * Registro de una venta cerrada. cliente y asesor son texto libre a propósito (ver
 * backend Venta): no todo comprador pasó por el CRM como lead y no todo asesor que vende tiene
 * cuenta en el sistema (hay asesores externos).
 */
export interface Venta {
  id: number;
  lote: Lote;
  cliente: string;
  asesor: string;
  precioVenta: number;
  formaPago: string;
  fechaVenta: string;
  /** Términos de financiamiento; null en ventas de contado. */
  mensualidad: number | null;
  plazoMeses: number | null;
  notas: string | null;
  fechaCreacion: string;
  /** Se calculan a partir de los pagos de la venta (ver PagoVenta), nunca se capturan a mano. */
  totalAbonado: number;
  saldoPendiente: number;
}

export interface VentaCreateRequest {
  loteId: number;
  cliente: string;
  asesor: string;
  precioVenta: number;
  formaPago: string;
  fechaVenta: string;
  mensualidad: number | null;
  plazoMeses: number | null;
  notas: string | null;
  marcarLoteVendido: boolean;
}

/** Un abono registrado contra una venta. */
export interface PagoVenta {
  id: number;
  fecha: string;
  monto: number;
  notas: string | null;
  registradoPor: UsuarioResumen;
  fechaCreacion: string;
}

export interface PagoVentaCreateRequest {
  fecha: string;
  monto: number;
  notas: string | null;
}
