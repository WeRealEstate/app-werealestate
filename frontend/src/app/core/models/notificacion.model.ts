export type TipoNotificacion =
  | 'LEAD_FRIO'
  | 'LEAD_SIN_CONTACTAR'
  | 'SEGUIMIENTO_PENDIENTE'
  | 'TAREA_PENDIENTE'
  | 'EVENTO_PENDIENTE';

export interface Notificacion {
  tipo: TipoNotificacion;
  mensaje: string;
  leadId: number | null;
  tareaId: number | null;
  eventoId: number | null;
  firma: string;
}
