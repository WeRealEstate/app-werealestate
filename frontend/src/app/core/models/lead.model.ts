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
  etiquetas: Etiqueta[];
}

export interface ColumnaPersonalizada {
  id: number;
  nombre: string;
  orden: number;
}

/** Paleta fija de 20 colores base; coincide 1:1 con nombres de color de Tailwind. */
export const ETIQUETA_COLORES = [
  'SLATE',
  'GRAY',
  'ZINC',
  'RED',
  'ORANGE',
  'AMBER',
  'YELLOW',
  'LIME',
  'GREEN',
  'EMERALD',
  'TEAL',
  'CYAN',
  'SKY',
  'BLUE',
  'INDIGO',
  'VIOLET',
  'PURPLE',
  'FUCHSIA',
  'PINK',
  'ROSE',
] as const;

export type EtiquetaColor = (typeof ETIQUETA_COLORES)[number];

export interface Etiqueta {
  id: number;
  nombre: string;
  color: EtiquetaColor;
}

export interface EtiquetaCreateRequest {
  nombre: string;
  color: EtiquetaColor;
  asesorId?: number | null;
}

export interface EtiquetaUpdateRequest {
  nombre: string;
  color: EtiquetaColor;
}

/**
 * Clases completas y literales (no armadas por interpolación) para que Tailwind las detecte al
 * compilar — una clase como `bg-${color}-100` construida en tiempo de ejecución nunca aparece
 * como texto en el código fuente y Tailwind jamás la generaría.
 */
export const ETIQUETA_BADGE_CLASSES: Record<EtiquetaColor, string> = {
  SLATE: 'bg-slate-100 text-slate-700 dark:bg-slate-500/15 dark:text-slate-300',
  GRAY: 'bg-gray-100 text-gray-700 dark:bg-gray-500/15 dark:text-gray-300',
  ZINC: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-500/15 dark:text-zinc-300',
  RED: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  ORANGE: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
  AMBER: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  YELLOW: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-500/15 dark:text-yellow-300',
  LIME: 'bg-lime-100 text-lime-700 dark:bg-lime-500/15 dark:text-lime-300',
  GREEN: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  EMERALD: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  TEAL: 'bg-teal-100 text-teal-700 dark:bg-teal-500/15 dark:text-teal-300',
  CYAN: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-500/15 dark:text-cyan-300',
  SKY: 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300',
  BLUE: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  INDIGO: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300',
  VIOLET: 'bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300',
  PURPLE: 'bg-purple-100 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300',
  FUCHSIA: 'bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-500/15 dark:text-fuchsia-300',
  PINK: 'bg-pink-100 text-pink-700 dark:bg-pink-500/15 dark:text-pink-300',
  ROSE: 'bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300',
};

/** Círculo sólido para el selector de color (crear/editar etiqueta). */
export const ETIQUETA_SWATCH_CLASSES: Record<EtiquetaColor, string> = {
  SLATE: 'bg-slate-500',
  GRAY: 'bg-gray-500',
  ZINC: 'bg-zinc-500',
  RED: 'bg-red-500',
  ORANGE: 'bg-orange-500',
  AMBER: 'bg-amber-500',
  YELLOW: 'bg-yellow-500',
  LIME: 'bg-lime-500',
  GREEN: 'bg-green-500',
  EMERALD: 'bg-emerald-500',
  TEAL: 'bg-teal-500',
  CYAN: 'bg-cyan-500',
  SKY: 'bg-sky-500',
  BLUE: 'bg-blue-500',
  INDIGO: 'bg-indigo-500',
  VIOLET: 'bg-violet-500',
  PURPLE: 'bg-purple-500',
  FUCHSIA: 'bg-fuchsia-500',
  PINK: 'bg-pink-500',
  ROSE: 'bg-rose-500',
};

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
