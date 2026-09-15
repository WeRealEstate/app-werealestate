import { Desarrollo, UsuarioResumen } from './lead.model';

export type EstadoLote = 'DISPONIBLE' | 'APARTADO' | 'APARTADO_CON_DINERO' | 'EN_PROCESO_DE_FIRMA';

export const ESTADO_LOTE_LABELS: Record<EstadoLote, string> = {
  DISPONIBLE: 'Disponible',
  APARTADO: 'Apartado',
  APARTADO_CON_DINERO: 'Apartado con dinero',
  EN_PROCESO_DE_FIRMA: 'En proceso de firma',
};

/** Solo un admin puede establecer estos dos; DISPONIBLE y APARTADO los puede mover cualquiera. */
export const ESTADOS_LOTE_SOLO_ADMIN: ReadonlySet<EstadoLote> = new Set([
  'APARTADO_CON_DINERO',
  'EN_PROCESO_DE_FIRMA',
]);

export const ESTADO_LOTE_BADGE_CLASSES: Record<EstadoLote, string> = {
  DISPONIBLE: 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300',
  APARTADO: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  APARTADO_CON_DINERO: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
  EN_PROCESO_DE_FIRMA: 'bg-we-primary/15 text-we-primary dark:text-we-blue-light',
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

/** Un renglón del historial de movimientos de un lote: qué cambió, quién y cuándo. `usuario` es
 * null en la reversión automática por vencimiento; `nombreAsesor` solo viene de un apartado hecho
 * desde /cotizador-publico/lotes, donde no hay una sesión real detrás. */
export interface MovimientoLote {
  id: number;
  estadoAnterior: EstadoLote;
  estadoNuevo: EstadoLote;
  usuario: UsuarioResumen | null;
  nombreAsesor: string | null;
  fecha: string;
}
