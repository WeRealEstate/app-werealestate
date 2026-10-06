/** INDEPENDIENTE (default, trabaja solo) / LIDER (encabeza un equipo) / LINEA (reporta a alguien
 * — ver liderDirectoId/nivelLinea) dentro de la estructura de Teams. Tope de 3 niveles: Líder →
 * Línea 1 → Línea 2. */
export type TipoAsesorExterno = 'INDEPENDIENTE' | 'LIDER' | 'LINEA';

/** Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol) — solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un usuario interno. */
/** Estado del contrato; solo con VIGENTE entra a cotizar/apartar en los planos públicos. */
export type EstadoContratoAsesor = 'VIGENTE' | 'PENDIENTE_DE_FIRMAR' | 'VENCIDO' | 'CANCELADO';

export const ESTADO_CONTRATO_LABELS: Record<EstadoContratoAsesor, string> = {
  VIGENTE: 'Vigente',
  PENDIENTE_DE_FIRMAR: 'Pendiente de firmar',
  VENCIDO: 'Vencido',
  CANCELADO: 'Cancelado',
};

export const ESTADOS_CONTRATO: EstadoContratoAsesor[] = ['VIGENTE', 'PENDIENTE_DE_FIRMAR', 'VENCIDO', 'CANCELADO'];

export const ESTADO_CONTRATO_CLASES: Record<EstadoContratoAsesor, string> = {
  VIGENTE: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  PENDIENTE_DE_FIRMAR: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  VENCIDO: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  CANCELADO: 'bg-surface-2 text-ink-muted',
};

export interface AsesorExterno {
  id: number;
  nombre: string;
  /** null en asesores registrados antes de que se pidieran estos datos (ver migración V35). */
  celular: string | null;
  correo: string | null;
  activo: boolean;
  tipo: TipoAsesorExterno;
  /** Solo si tipo = 'LINEA': a quién reporta (un LIDER en línea 1, o un LINEA de línea 1 en línea 2). */
  liderDirectoId: number | null;
  liderDirectoNombre: string | null;
  /** Solo si tipo = 'LINEA': 1 o 2. Lo calcula el servidor, nunca se manda al actualizar. */
  nivelLinea: number | null;
  contratoEstado: EstadoContratoAsesor;
  /** Un VIGENTE con vencimiento ya pasado llega como VENCIDO: es el que cuenta para el acceso. */
  contratoEstadoEfectivo: EstadoContratoAsesor;
  /** Fechas ISO (yyyy-MM-dd), opcionales. */
  contratoFechaFirma: string | null;
  contratoFechaVencimiento: string | null;
  accesoSamai: boolean;
  accesoNanuu: boolean;
}

export interface AsesorExternoCreateRequest {
  nombre: string;
  celular: string;
  correo: string | null;
  contratoEstado?: EstadoContratoAsesor;
  contratoFechaFirma?: string | null;
  contratoFechaVencimiento?: string | null;
  accesoSamai?: boolean;
  accesoNanuu?: boolean;
}

/** celular/correo no son obligatorios aquí: un asesor externo registrado antes de que existieran
 * estos campos debe poder seguir editándose.
 * tipo/liderDirectoId son la jerarquía de Teams — la pantalla de "Asesores externos" los manda
 * sin cambios (tal como venían) al renombrar/activar; solo Teams los cambia de verdad. */
export interface AsesorExternoUpdateRequest {
  nombre: string;
  celular: string | null;
  correo: string | null;
  activo: boolean;
  tipo: TipoAsesorExterno;
  liderDirectoId: number | null;
  /** Opcionales: omitidos = sin cambios. Con contratoEstado, las fechas se aplican tal cual (null las borra). */
  contratoEstado?: EstadoContratoAsesor;
  contratoFechaFirma?: string | null;
  contratoFechaVencimiento?: string | null;
  accesoSamai?: boolean;
  accesoNanuu?: boolean;
}
