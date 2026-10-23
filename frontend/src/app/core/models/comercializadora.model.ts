export interface Comercializadora {
  id: number;
  nombre: string;
  liderId: number;
  liderNombre: string;
  notas: string | null;
  activa: boolean;
  integrantes: number;
  /** Secreto del enlace que la líder comparte con su equipo (ver /unirse/:token). */
  token: string;
  fechaCreacion: string;
}

export interface IntegranteComercializadora {
  id: number;
  nombre: string;
  celular: string | null;
  correo: string | null;
  activo: boolean;
  pin: string | null;
  fechaAlta: string;
}

export interface ComercializadoraRequest {
  nombre: string;
  liderId: number;
  notas: string | null;
}

export interface ComercializadoraPublica {
  comercializadora: string;
  lider: string;
}

export interface UnirseResultado {
  nombre: string;
  pin: string;
  comercializadora: string;
  lider: string;
}

/** El enlace que la líder comparte con su equipo. */
export function enlaceDeComercializadora(token: string): string {
  return `${window.location.origin}/unirse/${token}`;
}
