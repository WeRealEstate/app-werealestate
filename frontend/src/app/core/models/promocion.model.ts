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
  bonoDosLotes: number | null;
  bonoTresLotes: number | null;
  plazoFijo: number | null;
  descripcion: string | null;
  activa: boolean;
  fechaFin: string | null;
  fechaCreacion: string;
}

export interface PromocionCreateRequest {
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  bonoDosLotes?: number | null;
  bonoTresLotes?: number | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
}

export interface PromocionUpdateRequest {
  nombre?: string;
  proyecto?: ProyectoPromocion;
  tipoPrecio?: TipoPrecioPromocion;
  modalidad?: ModalidadPromocion;
  mensualidadFija?: number | null;
  bonoDosLotes?: number | null;
  bonoTresLotes?: number | null;
  plazoFijo?: number | null;
  descripcion?: string | null;
  activa?: boolean;
  fechaFin?: string | null;
  limpiarFechaFin?: boolean;
}
