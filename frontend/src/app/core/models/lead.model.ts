export type EstadoLead =
  | 'NUEVO'
  | 'CONTACTADO'
  | 'INTERESADO'
  | 'CITA_AGENDADA'
  | 'NEGOCIACION'
  | 'CERRADO_GANADO'
  | 'CERRADO_PERDIDO';

export type TipoSeguimiento = 'LLAMADA' | 'WHATSAPP' | 'EMAIL' | 'VISITA' | 'VISITA_OFICINA' | 'OTRO';

export type Pais = 'MEXICANO' | 'EXTRANJERO';

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
  edad: number | null;
  pais: Pais | null;
  estadoRepublica: string | null;
  diasSinContacto: number;
  frio: boolean;
  archivado: boolean;
  columnaPersonalizadaId: number | null;
  columnaPersonalizadaNombre: string | null;
}

export interface ColumnaPersonalizada {
  id: number;
  nombre: string;
  orden: number;
}

export interface LeadCreateRequest {
  nombreCliente: string;
  telefono: string;
  email?: string | null;
  origen?: string | null;
  desarrolloId: number;
  valorEstimado?: number | null;
  asesorId?: number | null;
  edad?: number | null;
  pais?: Pais | null;
  estadoRepublica?: string | null;
}

export interface LeadUpdateRequest {
  nombreCliente: string;
  telefono: string;
  email?: string | null;
  origen?: string | null;
  estado: EstadoLead;
  valorEstimado?: number | null;
  edad?: number | null;
  pais?: Pais | null;
  estadoRepublica?: string | null;
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
  duracionMinutos: number | null;
}

export interface SeguimientoProximo {
  id: number;
  leadId: number;
  leadNombreCliente: string;
  asesor: UsuarioResumen;
  tipo: TipoSeguimiento;
  proximoSeguimiento: string;
  duracionMinutos: number | null;
}

export interface SeguimientoCreateRequest {
  tipo: TipoSeguimiento;
  nota: string;
  resultado?: string | null;
  proximoSeguimiento?: string | null;
  duracionMinutos?: number | null;
}

/** Mover un lead a otra tarjeta (o a "Sin asignar" si columnaPersonalizadaId es null) se confirma con estos mismos datos de seguimiento. */
export interface MoverColumnaRequest {
  columnaPersonalizadaId: number | null;
  tipo: TipoSeguimiento;
  nota: string;
  resultado?: string | null;
  proximoSeguimiento?: string | null;
  duracionMinutos?: number | null;
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
  VISITA: 'Recorrido',
  VISITA_OFICINA: 'Visita a la oficina',
  OTRO: 'Otro',
};

export const PAIS_LABELS: Record<Pais, string> = {
  MEXICANO: 'Mexicano',
  EXTRANJERO: 'Extranjero',
};

export const ESTADOS_REPUBLICA = [
  'Aguascalientes',
  'Baja California',
  'Baja California Sur',
  'Campeche',
  'Chiapas',
  'Chihuahua',
  'Ciudad de México',
  'Coahuila',
  'Colima',
  'Durango',
  'Estado de México',
  'Guanajuato',
  'Guerrero',
  'Hidalgo',
  'Jalisco',
  'Michoacán',
  'Morelos',
  'Nayarit',
  'Nuevo León',
  'Oaxaca',
  'Puebla',
  'Querétaro',
  'Quintana Roo',
  'San Luis Potosí',
  'Sinaloa',
  'Sonora',
  'Tabasco',
  'Tamaulipas',
  'Tlaxcala',
  'Veracruz',
  'Yucatán',
  'Zacatecas',
] as const;
