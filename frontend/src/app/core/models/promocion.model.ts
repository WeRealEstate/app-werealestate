export type ProyectoPromocion = 'samai' | 'nanuu';

export interface Promocion {
  id: number;
  nombre: string;
  proyecto: ProyectoPromocion;
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
