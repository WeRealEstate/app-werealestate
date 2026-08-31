export type TipoNotificacion = 'LEAD_FRIO' | 'SEGUIMIENTO_PENDIENTE' | 'TAREA_PENDIENTE';

export interface Notificacion {
  tipo: TipoNotificacion;
  mensaje: string;
  leadId: number | null;
  tareaId: number | null;
}
