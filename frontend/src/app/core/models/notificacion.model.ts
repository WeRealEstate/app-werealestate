export type TipoNotificacion =
  | 'SEGUIMIENTO_PENDIENTE'
  | 'TAREA_PENDIENTE'
  | 'EVENTO_PENDIENTE'
  | 'LOTE_APARTADO'
  | 'LOTE_DESAPARTADO'
  | 'LOTE_ESTADO'
  | 'VENTA_NUEVA'
  | 'REGISTRO_VENTA_NUEVO'
  | 'LOTE_SIN_VENTA'
  | 'LOTE_VENTA_DESALINEADA';

export interface Notificacion {
  tipo: TipoNotificacion;
  mensaje: string;
  leadId: number | null;
  tareaId: number | null;
  eventoId: number | null;
  /** Solo en los avisos de lotes (admin y líder): id del movimiento de estado que los originó; en REGISTRO_VENTA_NUEVO, el id de la respuesta del formulario. */
  movimientoId: number | null;
  /** Solo en VENTA_NUEVA (admin y Administración): id de la venta registrada. */
  ventaId: number | null;
  firma: string;
  /** Avisos de lotes desincronizados con Ventas (LOTE_SIN_VENTA, LOTE_VENTA_DESALINEADA): el lote a corregir. */
  loteId: number | null;
  /** true = no se puede descartar: desaparece sola cuando se corrige lo que la provoca. */
  fija: boolean;
}
