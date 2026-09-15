import { Desarrollo, UsuarioResumen } from './lead.model';

export type EstadoLote =
  | 'DISPONIBLE'
  | 'APARTADO'
  | 'APARTADO_CON_DINERO'
  | 'EN_PROCESO_DE_FIRMA'
  | 'VENDIDO';

export const ESTADO_LOTE_LABELS: Record<EstadoLote, string> = {
  DISPONIBLE: 'Disponible',
  APARTADO: 'Apartado',
  APARTADO_CON_DINERO: 'Apartado con dinero',
  EN_PROCESO_DE_FIRMA: 'En proceso de firma',
  VENDIDO: 'Vendido',
};

/** Solo un admin puede establecer estos; DISPONIBLE y APARTADO los puede mover cualquiera. */
export const ESTADOS_LOTE_SOLO_ADMIN: ReadonlySet<EstadoLote> = new Set([
  'APARTADO_CON_DINERO',
  'EN_PROCESO_DE_FIRMA',
  'VENDIDO',
]);

export const ESTADO_LOTE_BADGE_CLASSES: Record<EstadoLote, string> = {
  DISPONIBLE: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  APARTADO: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  APARTADO_CON_DINERO: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
  EN_PROCESO_DE_FIRMA: 'bg-we-primary/15 text-we-primary dark:text-we-blue-light',
  VENDIDO: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
};

/** Colores sólidos para el pin de cada lote en el plano interactivo (a diferencia del badge, que
 * usa un fondo pálido pensado para texto encima). */
export const ESTADO_LOTE_PIN_CLASSES: Record<EstadoLote, string> = {
  DISPONIBLE: 'bg-green-500 ring-green-200 dark:ring-green-900',
  APARTADO: 'bg-amber-500 ring-amber-200 dark:ring-amber-900',
  APARTADO_CON_DINERO: 'bg-orange-500 ring-orange-200 dark:ring-orange-900',
  EN_PROCESO_DE_FIRMA: 'bg-we-primary ring-we-primary/20',
  VENDIDO: 'bg-red-500 ring-red-200 dark:ring-red-900',
};

export interface Lote {
  id: number;
  desarrollo: Desarrollo;
  manzana: string;
  numeroLote: string;
  superficie: number;
  estado: EstadoLote;
  fechaCambioEstado: string;
  cambiadoPor: UsuarioResumen | null;
  mapaX: number | null;
  mapaY: number | null;
}

/** El plano interactivo de un desarrollo: la imagen subida por un admin y el pin de cada lote (si
 * ya se delimitó), para /panel/lotes/plano. */
export interface PlanoDesarrollo {
  desarrolloId: number;
  desarrolloNombre: string;
  planoUrl: string | null;
  lotes: Lote[];
}

export interface LoteCreateRequest {
  desarrolloId: number;
  manzana: string;
  numeroLote: string;
  superficie: number;
}

export interface LoteUpdateRequest {
  manzana: string;
  numeroLote: string;
  superficie: number;
}

export interface LoteImportRequest {
  desarrolloId: number;
  manzana: string;
  numeroLote: string;
  superficie: number;
}

export interface LoteImportBatchRequest {
  lotes: LoteImportRequest[];
}

export interface LoteImportError {
  fila: number;
  manzana: string;
  numeroLote: string;
  motivo: string;
}

export interface LoteImportResultado {
  creados: number;
  errores: LoteImportError[];
}

/** Un renglón del historial de movimientos de lotes: de qué lote, qué cambió, quién y cuándo.
 * `usuario` es null en la reversión automática por vencimiento; `nombreAsesor`/`nota` solo vienen
 * de un apartado hecho desde /cotizador-publico/lotes, donde no hay una sesión real detrás. */
export interface MovimientoLote {
  id: number;
  loteId: number;
  manzana: string;
  numeroLote: string;
  desarrolloNombre: string;
  estadoAnterior: EstadoLote;
  estadoNuevo: EstadoLote;
  usuario: UsuarioResumen | null;
  nombreAsesor: string | null;
  nota: string | null;
  fecha: string;
}
