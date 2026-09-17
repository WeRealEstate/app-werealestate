export type ProyectoPromocion = 'samai' | 'nanuu';

/** A qué tarifa del proyecto aplica: LOTE (precio normal por m²) o HECTAREA (tarifa de macrolote,
 * solo existe en SAMAI). Fijo desde la creación, igual que el proyecto. */
export type TipoPrecioPromocion = 'LOTE' | 'HECTAREA';

export interface Promocion {
  id: number;
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio: TipoPrecioPromocion;
  mensualidadFija: number;
  descripcion: string | null;
  activa: boolean;
  /** Cuándo deja de aplicar la promoción; null = sin vencimiento. Formato LocalDateTime ISO. */
  fechaFin: string | null;
  fechaCreacion: string;
}

export interface PromocionCreateRequest {
  nombre: string;
  proyecto: ProyectoPromocion;
  tipoPrecio: TipoPrecioPromocion;
  mensualidadFija: number;
  descripcion?: string | null;
  fechaFin?: string | null;
}

export interface PromocionUpdateRequest {
  nombre: string;
  mensualidadFija: number;
  descripcion?: string | null;
  fechaFin?: string | null;
}
