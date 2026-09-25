import { Lote } from './lote.model';
import { UsuarioResumen } from './lead.model';

/** Un lote dentro de una venta, con el precio negociado para ese lote en particular. */
export interface VentaLote {
  id: number;
  lote: Lote;
  precio: number;
}

/** Asesor de una venta, sea interno (usuario real del sistema) o externo (ver AsesorExterno). */
export interface VentaAsesor {
  id: number;
  nombre: string;
  externo: boolean;
}

/**
 * Registro de una venta cerrada. cliente es texto libre a propósito (ver backend Venta): no todo
 * comprador pasó por el CRM como lead. El asesor sí es una relación real, a un usuario interno o a
 * un asesor externo registrado. Puede incluir varios lotes (misma operación, una sola
 * mensualidad/plazo/saldo combinado — ver VentaLote).
 */
export interface Venta {
  id: number;
  lotes: VentaLote[];
  cliente: string;
  asesor: VentaAsesor;
  formaPago: string;
  fechaVenta: string;
  /** Términos de financiamiento; null en ventas de contado. */
  mensualidad: number | null;
  plazoMeses: number | null;
  /** "Enganche" / "Pago inicial" / "Aportación anual" (mismo concepto que ya usa Cotización);
   * ambos null cuando no aplica (Sin enganche / Contado). */
  engancheLabel: string | null;
  enganche: number | null;
  notas: string | null;
  fechaCreacion: string;
  /** Se calculan a partir de sus lotes y sus pagos (ver PagoVenta), nunca se capturan a mano. */
  precioVenta: number;
  totalAbonado: number;
  saldoPendiente: number;
}

export interface VentaLoteItemRequest {
  loteId: number;
  precio: number;
}

export interface VentaCreateRequest {
  lotes: VentaLoteItemRequest[];
  cliente: string;
  // Exactamente uno de los dos (ver backend VentaService.resolverAsesor).
  usuarioAsesorId: number | null;
  asesorExternoId: number | null;
  formaPago: string;
  fechaVenta: string;
  mensualidad: number | null;
  plazoMeses: number | null;
  engancheLabel: string | null;
  enganche: number | null;
  notas: string | null;
  marcarLoteVendido: boolean;
}

/** Igual que VentaCreateRequest pero sin lotes ni marcarLoteVendido: no toca qué lotes incluye la
 * venta, solo sus datos capturados. Temporal: el botón "Modificar venta" que usa esto se va a
 * quitar más adelante. */
export interface VentaUpdateRequest {
  cliente: string;
  usuarioAsesorId: number | null;
  asesorExternoId: number | null;
  formaPago: string;
  fechaVenta: string;
  mensualidad: number | null;
  plazoMeses: number | null;
  engancheLabel: string | null;
  enganche: number | null;
  notas: string | null;
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
