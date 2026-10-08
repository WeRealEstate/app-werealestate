export type TipoNotificacion =
  | 'SEGUIMIENTO_PENDIENTE'
  | 'TAREA_PENDIENTE'
  | 'EVENTO_PENDIENTE'
  | 'LOTE_APARTADO'
  | 'LOTE_DESAPARTADO'
  | 'LOTE_ESTADO'
  | 'VENTA_NUEVA';

export interface Notificacion {
  tipo: TipoNotificacion;
  mensaje: string;
  leadId: number | null;
  tareaId: number | null;
  eventoId: number | null;
  /** Solo en los avisos de lotes (admin y líder): id del movimiento de estado que los originó. */
  movimientoId: number | null;
  /** Solo en VENTA_NUEVA (admin y Administración): id de la venta registrada. */
  ventaId: number | null;
  firma: string;
}
