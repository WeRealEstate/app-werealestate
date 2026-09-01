export type EstadoLead =
  | 'NUEVO'
  | 'CONTACTADO'
  | 'INTERESADO'
  | 'CITA_AGENDADA'
  | 'NEGOCIACION'
  | 'CERRADO_GANADO'
  | 'CERRADO_PERDIDO';

export type TipoSeguimiento = 'LLAMADA' | 'WHATSAPP' | 'EMAIL' | 'VISITA' | 'OTRO';

export interface Desarrollo {
  id: number;
  nombre: string;
  ubicacion: string;
  precioM2: number;
  areaMinima: number;
}

export interface UsuarioResumen {
  id: number;
  nombre: string;
}

export interface Lead {
  id: number;
  nombreCliente: string;
  telefono: string;
  email: string | null;
  origen: string | null;
  desarrollo: Desarrollo;
  asesor: UsuarioResumen;
  estado: EstadoLead;
  fechaCreacion: string;
  fechaUltimoContacto: string;
  valorEstimado: number | null;
  diasSinContacto: number;
  frio: boolean;
}

export interface LeadCreateRequest {
  nombreCliente: string;
  telefono: string;
  email?: string | null;
  origen?: string | null;
  desarrolloId: number;
  valorEstimado?: number | null;
  asesorId?: number | null;
}

export interface LeadUpdateRequest {
  nombreCliente: string;
  telefono: string;
  email?: string | null;
  origen?: string | null;
  estado: EstadoLead;
  valorEstimado?: number | null;
}

export interface Seguimiento {
  id: number;
  leadId: number;
  asesor: UsuarioResumen;
  fecha: string;
  tipo: TipoSeguimiento;
  nota: string;
  resultado: string | null;
  proximoSeguimiento: string | null;
}

export interface SeguimientoProximo {
  id: number;
  leadId: number;
  leadNombreCliente: string;
  asesor: UsuarioResumen;
  tipo: TipoSeguimiento;
  proximoSeguimiento: string;
}

export interface SeguimientoCreateRequest {
  tipo: TipoSeguimiento;
  nota: string;
  resultado?: string | null;
  proximoSeguimiento?: string | null;
}

export const ESTADO_LEAD_LABELS: Record<EstadoLead, string> = {
  NUEVO: 'Nuevo',
  CONTACTADO: 'Contactado',
  INTERESADO: 'Interesado',
  CITA_AGENDADA: 'Cita agendada',
  NEGOCIACION: 'Negociación',
  CERRADO_GANADO: 'Cerrado (ganado)',
  CERRADO_PERDIDO: 'Cerrado (perdido)',
};

export const TIPO_SEGUIMIENTO_LABELS: Record<TipoSeguimiento, string> = {
  LLAMADA: 'Llamada',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'Correo',
  VISITA: 'Visita',
  OTRO: 'Otro',
};
