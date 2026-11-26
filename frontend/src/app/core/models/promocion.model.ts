export type ProyectoPromocion = 'samai' | 'nanuu';

export type TipoPrecioPromocion = 'LOTE' | 'HECTAREA';

export type ModalidadPromocion = 'MENSUALIDAD_FIJA' | 'BONO_LOTES';

export interface Promocion {
  id: number;
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio: TipoPrecioPromocion;
  modalidad: ModalidadPromocion;
  mensualidadFija: number | null;
  /** Bono al comprar 1 lote; null = la promoción no aplica a 1 lote. */
  bonoUnLote: number | null;
  bonoDosLotes: number | null;
  bonoTresLotes: number | null;
  plazoFijo: number | null;
  descripcion: string | null;
  activa: boolean;
  fechaFin: string | null;
  fechaCreacion: string;
  /** Desde cuándo vale; null en las de antes de existir la fecha de inicio (valen desde siempre). */
  fechaInicio: string | null;
  /** true = espera su fecha de inicio y se activa sola ese día. */
  programada: boolean;
}

export interface PromocionCreateRequest {
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  /** 0 o null = no aplica a 1 lote (al editar, 0 lo quita). */
  bonoUnLote?: number | null;
  bonoDosLotes?: number | null;
  bonoTresLotes?: number | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
  /** null = empieza al crearla. */
  fechaInicio?: string | null;
}

export interface PromocionUpdateRequest {
  nombre?: string;
  proyecto?: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  /** 0 o null = no aplica a 1 lote (al editar, 0 lo quita). */
  bonoUnLote?: number | null;
  bonoDosLotes?: number | null;
  bonoTresLotes?: number | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
  limpiarFechaFin?: boolean;
  fechaInicio?: string | null;
}
