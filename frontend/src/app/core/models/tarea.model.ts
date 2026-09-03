import { UsuarioResumen } from './lead.model';

export interface Tarea {
  id: number;
  titulo: string;
  descripcion: string | null;
  asignadoA: UsuarioResumen;
  creadoPor: UsuarioResumen;
  fechaLimite: string | null;
  completada: boolean;
  fechaCreacion: string;
}

export interface TareaCreateRequest {
  titulo: string;
  descripcion?: string | null;
  asignadoAId: number;
  fechaLimite?: string | null;
}

export interface TareaReasignarRequest {
  nuevoAsignadoAId: number;
}
