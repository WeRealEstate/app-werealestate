import { Desarrollo, UsuarioResumen } from './lead.model';

export type EstadoLote =
  | 'DISPONIBLE'
  | 'APARTADO'
  | 'APARTADO_A_PLAZO'
  | 'APARTADO_CON_DINERO'
  | 'EN_PROCESO_DE_FIRMA'
  | 'VENDIDO';

export const ESTADO_LOTE_LABELS: Record<EstadoLote, string> = {
  DISPONIBLE: 'Disponible',
  APARTADO: 'Apartado',
  APARTADO_A_PLAZO: 'Apartado a plazo',
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

/** Solo un admin o un líder de área puede establecer estos (o moverlos a cualquier otro estado,
 * una vez que ya están aquí). */
export const ESTADOS_LOTE_ADMIN_O_LIDER: ReadonlySet<EstadoLote> = new Set(['APARTADO_A_PLAZO']);

export const ESTADO_LOTE_BADGE_CLASSES: Record<EstadoLote, string> = {
  DISPONIBLE: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  APARTADO: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  APARTADO_A_PLAZO: 'bg-purple-100 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300',
  APARTADO_CON_DINERO: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
  EN_PROCESO_DE_FIRMA: 'bg-we-primary/15 text-we-primary dark:text-we-blue-light',
  VENDIDO: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
};

/** Colores de relleno/borde del polígono de cada lote en el plano interactivo (a diferencia del
 * badge, que usa un fondo pálido pensado para texto encima). */
export const ESTADO_LOTE_POLIGONO_CLASSES: Record<EstadoLote, string> = {
  DISPONIBLE: 'fill-green-500/35 stroke-green-500 dark:fill-green-400/30 dark:stroke-green-400',
  APARTADO: 'fill-amber-500/35 stroke-amber-500 dark:fill-amber-400/30 dark:stroke-amber-400',
  APARTADO_A_PLAZO: 'fill-purple-500/35 stroke-purple-500 dark:fill-purple-400/30 dark:stroke-purple-400',
  APARTADO_CON_DINERO: 'fill-orange-500/35 stroke-orange-500 dark:fill-orange-400/30 dark:stroke-orange-400',
  EN_PROCESO_DE_FIRMA: 'fill-we-primary/35 stroke-we-primary',
  VENDIDO: 'fill-red-500/35 stroke-red-500 dark:fill-red-400/30 dark:stroke-red-400',
};

/** Un vértice del polígono que delimita un lote sobre el plano, en % (0-100) del ancho/alto de la
 * imagen — así sirve para cualquier tamaño de pantalla. */
export interface PuntoMapa {
  x: number;
  y: number;
}

export interface Lote {
  id: number;
  desarrollo: Desarrollo;
  manzana: string;
  numeroLote: string;
  superficie: number;
  estado: EstadoLote;
  fechaCambioEstado: string;
  cambiadoPor: UsuarioResumen | null;
  /** null mientras el lote no se ha delimitado en el editor del plano. */
  mapaPoligono: PuntoMapa[] | null;
  fechaExpiraApartado: string | null;
}

/** El plano interactivo de un desarrollo: la imagen subida por un admin y el polígono de cada lote
 * (si ya se delimitó), para /panel/plano. */
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
