export interface EventoCalendario {
  id: number;
  titulo: string;
  descripcion: string | null;
  fecha: string;
  fechaCreacion: string;
}

export interface EventoCalendarioRequest {
  titulo: string;
  descripcion?: string | null;
  fecha: string;
}
