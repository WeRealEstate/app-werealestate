import { TipoPago } from '../utils/formas-pago';

/** We Forms: formularios de registro de venta (panel) y su link público. Ver backend WeFormDtos. */

export type EstadoWeForm = 'ACTIVO' | 'CERRADO';

export type TipoPreguntaForm =
  | 'CORTA'
  | 'PARRAFO'
  | 'OPCION_MULTIPLE'
  | 'CASILLAS'
  | 'DESPLEGABLE'
  | 'FECHA'
  | 'NUMERO'
  | 'TELEFONO'
  | 'CORREO'
  | 'ARCHIVO'
  | 'TITULO'
  | 'PAIS'
  | 'ASESOR'
  | 'LOTES'
  | 'TIPO_PAGO'
  | 'APORTACIONES';

/** Campo del cliente o de la venta que alimenta una pregunta predefinida. */
export type DestinoPregunta =
  | 'NOMBRE' | 'APELLIDO_PATERNO' | 'APELLIDO_MATERNO' | 'FECHA_NACIMIENTO' | 'CURP' | 'TELEFONO' | 'CORREO' | 'RFC'
  | 'CALLE' | 'COLONIA' | 'MUNICIPIO' | 'ESTADO' | 'CODIGO_POSTAL'
  | 'LOTES' | 'TIPO_PAGO' | 'ENGANCHE' | 'PLAZO' | 'DIA_PAGO' | 'APORTACIONES' | 'ASESOR';

/** Tipos que el admin puede agregar a mano (los últimos cuatro son selectores del sistema, solo en las predefinidas). */
export const TIPOS_PREGUNTA_PROPIOS: { tipo: TipoPreguntaForm; etiqueta: string }[] = [
  { tipo: 'CORTA', etiqueta: 'Respuesta corta' },
  { tipo: 'PARRAFO', etiqueta: 'Párrafo' },
  { tipo: 'OPCION_MULTIPLE', etiqueta: 'Opción múltiple' },
  { tipo: 'CASILLAS', etiqueta: 'Casillas' },
  { tipo: 'DESPLEGABLE', etiqueta: 'Lista desplegable' },
  { tipo: 'FECHA', etiqueta: 'Fecha' },
  { tipo: 'NUMERO', etiqueta: 'Número' },
  { tipo: 'TELEFONO', etiqueta: 'Teléfono' },
  { tipo: 'CORREO', etiqueta: 'Correo' },
  { tipo: 'ARCHIVO', etiqueta: 'Archivo (imagen o PDF, máx. 2 MB)' },
  { tipo: 'PAIS', etiqueta: 'País (lista con buscador)' },
  { tipo: 'TITULO', etiqueta: 'Sección / título' },
];

export const TIPO_PREGUNTA_ETIQUETA: Record<TipoPreguntaForm, string> = {
  CORTA: 'Respuesta corta',
  PARRAFO: 'Párrafo',
  OPCION_MULTIPLE: 'Opción múltiple',
  CASILLAS: 'Casillas',
  DESPLEGABLE: 'Lista desplegable',
  FECHA: 'Fecha',
  NUMERO: 'Número',
  TELEFONO: 'Teléfono',
  CORREO: 'Correo',
  ARCHIVO: 'Archivo',
  TITULO: 'Sección',
  PAIS: 'País',
  ASESOR: 'Asesor (automático)',
  LOTES: 'Lotes disponibles',
  TIPO_PAGO: 'Tipo de pago',
  APORTACIONES: 'Aportaciones',
};

export interface WeFormLista {
  id: number;
  nombre: string;
  slug: string;
  desarrolloId: number;
  desarrolloNombre: string;
  estado: EstadoWeForm;
  totalRespuestas: number;
  fechaCreacion: string;
}

export interface WeFormPregunta {
  id: number;
  orden: number;
  tipo: TipoPreguntaForm;
  titulo: string;
  descripcion: string | null;
  obligatoria: boolean;
  opciones: string[];
  destino: DestinoPregunta | null;
  visible: boolean;
  /** Predefinida: alimenta al cliente o a la venta; no se borra. */
  fija: boolean;
  /** Siempre visible y obligatoria. */
  siempreObligatoria: boolean;
  /** Se puede ocultar o cambiar si es obligatoria. */
  opcionalEditable: boolean;
}

export interface WeFormDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  slug: string;
  desarrolloId: number;
  desarrolloNombre: string;
  /** 'samai' | 'nanuu': de ahí sale el plano y el acceso del PIN. */
  proyecto: string;
  estado: EstadoWeForm;
  fechaCreacion: string;
  preguntas: WeFormPregunta[];
}

export interface WeFormCrearRequest {
  nombre: string;
  descripcion: string | null;
  desarrolloId: number;
}

export interface WeFormDuplicarRequest {
  nombre: string;
  descripcion: string | null;
  desarrolloId: number;
}

export interface WeFormActualizarRequest {
  nombre: string;
  descripcion: string | null;
  estado: EstadoWeForm;
}

export interface WeFormPreguntaRequest {
  tipo?: TipoPreguntaForm;
  titulo: string;
  descripcion: string | null;
  obligatoria: boolean;
  opciones: string[];
  visible: boolean;
}

export interface WeFormItemRespuesta {
  id: number;
  pregunta: string;
  tipo: TipoPreguntaForm;
  valor: string | null;
  tieneArchivo: boolean;
}

export interface WeFormRespuesta {
  id: number;
  fechaEnvio: string;
  asesorNombre: string;
  clienteId: number | null;
  clienteNombre: string | null;
  clienteNuevo: boolean;
  ventaId: number | null;
  ventaNumero: number | null;
  items: WeFormItemRespuesta[];
}

// ---- Estadísticas ----

export interface FiltroRespuestas {
  desde?: string;
  hasta?: string;
  asesor?: string;
}

export interface ConteoForm {
  etiqueta: string;
  cantidad: number;
}

export interface ResumenPregunta {
  preguntaId: number;
  titulo: string;
  tipo: TipoPreguntaForm;
  respondidas: number;
  opciones: ConteoForm[];
  textos: string[];
  minimo: number | null;
  maximo: number | null;
  promedio: number | null;
}

export interface EstadisticasForm {
  total: number;
  clientesNuevos: number;
  clientesExistentes: number;
  porDia: ConteoForm[];
  porAsesor: ConteoForm[];
  preguntas: ResumenPregunta[];
}

export interface ResumenGeneralForm {
  total: number;
  porDesarrollo: ConteoForm[];
  porAsesor: ConteoForm[];
}

// ---- Link público ----

export interface WeFormPublicoLista {
  slug: string;
  nombre: string;
  descripcion: string | null;
}

export interface LotePublicoForm {
  id: number;
  manzana: string;
  numeroLote: string;
  superficie: number;
  precio: number;
  estado: string;
}

export interface PreguntaPublica {
  id: number;
  tipo: TipoPreguntaForm;
  titulo: string;
  descripcion: string | null;
  obligatoria: boolean;
  opciones: string[];
  destino: DestinoPregunta | null;
}

export interface WeFormPublico {
  slug: string;
  nombre: string;
  descripcion: string | null;
  desarrolloNombre: string;
  asesorNombre: string;
  preguntas: PreguntaPublica[];
  lotes: LotePublicoForm[];
}

export interface WeFormEnvioResultado {
  respuestaId: number;
  ventaNumero: number;
  clienteNombre: string;
  clienteNuevo: boolean;
}

/** Mismas formas de pago que el Cotizador y Registrar venta, combinables igual (ver core/utils/formas-pago). */
export type TipoPagoForm = TipoPago;

export const TIPOS_PAGO_FORM: { value: TipoPagoForm; label: string; sublabel: string }[] = [
  { value: 'msi', label: 'Sin enganche', sublabel: 'Meses sin intereses' },
  { value: 'downpayment', label: 'Con enganche', sublabel: 'Monto libre' },
  { value: 'initial', label: 'Pago inicial', sublabel: 'Baja la mensualidad' },
  { value: 'annualities', label: 'Con anualidades', sublabel: 'Una por año, mismo mes' },
  { value: 'aportaciones', label: 'Con aportaciones', sublabel: 'Varias, cada una en su mes' },
  { value: 'cash', label: 'Contado', sublabel: 'Pago único' },
];
