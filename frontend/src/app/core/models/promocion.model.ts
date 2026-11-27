export type ProyectoPromocion = 'samai' | 'nanuu';

export type TipoPrecioPromocion = 'LOTE' | 'HECTAREA';

export type ModalidadPromocion = 'MENSUALIDAD_FIJA' | 'BONO_LOTES';

/** Un nivel del bono: con exactamente `lotes` lotes (1 a 20) la venta recibe `bono`. */
export interface NivelBono {
  lotes: number;
  bono: number;
}

/** Máximo de lotes de un nivel del bono (igual que el servidor). */
export const MAX_LOTES_NIVEL = 20;

export interface Promocion {
  id: number;
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio: TipoPrecioPromocion;
  modalidad: ModalidadPromocion;
  mensualidadFija: number | null;
  /** Bono por número EXACTO de lotes (de menos a más); una venta con otro número de lotes no tiene bono. */
  bonos: NivelBono[];
  plazoFijo: number | null;
  descripcion: string | null;
  activa: boolean;
  fechaFin: string | null;
  fechaCreacion: string;
  /** Desde cuándo vale; null en las de antes de existir la fecha de inicio (valen desde siempre). */
  fechaInicio: string | null;
  /** true = espera su fecha de inicio y se activa sola ese día. */
  programada: boolean;
  /** Código de 5 a 7 letras o números para el registro de venta; solo lo recibe el admin. */
  codigo: string | null;
}

export interface PromocionCreateRequest {
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  /** 0 o null = no aplica a 1 lote (al editar, 0 lo quita). */
  bonos?: NivelBono[] | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
  /** null = empieza al crearla. */
  fechaInicio?: string | null;
  /** 6 letras o números; vacío = al crear, uno al azar; al editar, el mismo. */
  codigo?: string | null;
}

export interface PromocionUpdateRequest {
  nombre?: string;
  proyecto?: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  /** 0 o null = no aplica a 1 lote (al editar, 0 lo quita). */
  bonos?: NivelBono[] | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
  limpiarFechaFin?: boolean;
  fechaInicio?: string | null;
  /** 6 letras o números; vacío = al crear, uno al azar; al editar, el mismo. */
  codigo?: string | null;
}
