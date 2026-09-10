export type ProyectoPromocion = 'samai' | 'nanuu';

export interface Promocion {
  id: number;
  nombre: string;
  proyecto: ProyectoPromocion;
  mensualidadFija: number;
  descripcion: string | null;
  activa: boolean;
  fechaCreacion: string;
}

export interface PromocionCreateRequest {
  nombre: string;
  proyecto: ProyectoPromocion;
  mensualidadFija: number;
  descripcion?: string | null;
}

export interface PromocionUpdateRequest {
  nombre: string;
  mensualidadFija: number;
  descripcion?: string | null;
}
