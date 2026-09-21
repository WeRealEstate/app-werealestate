import { Lote } from './lote.model';

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
  notas: string | null;
  fechaCreacion: string;
}

export interface VentaCreateRequest {
  loteId: number;
  cliente: string;
  asesor: string;
  precioVenta: number;
  formaPago: string;
  fechaVenta: string;
  notas: string | null;
  marcarLoteVendido: boolean;
}
