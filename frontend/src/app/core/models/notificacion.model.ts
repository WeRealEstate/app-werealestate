export type TipoNotificacion =
  | 'SEGUIMIENTO_PENDIENTE'
  | 'TAREA_PENDIENTE'
  | 'EVENTO_PENDIENTE'
  | 'LOTE_APARTADO'
  | 'LOTE_DESAPARTADO';

export interface Notificacion {
  tipo: TipoNotificacion;
  mensaje: string;
  leadId: number | null;
  tareaId: number | null;
  eventoId: number | null;
  /** Solo en los avisos de lotes (admin y líder): id del movimiento de estado que los originó. */
  movimientoId: number | null;
  firma: string;
}
