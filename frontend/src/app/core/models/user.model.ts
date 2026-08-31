export type Role = 'ASESOR' | 'LIDER_AREA' | 'EQUIPO_INTERNO' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  ASESOR: 'Asesor',
  LIDER_AREA: 'Líder de área',
  EQUIPO_INTERNO: 'Equipo interno',
  ADMIN: 'Administrador',
};

export interface User {
  id: number;
  nombre: string;
  email: string;
  rol: Role;
  areaId: number | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: Role;
  areaId: number | null;
  activo: boolean;
}

export interface UsuarioCreateRequest {
  nombre: string;
  email: string;
  password: string;
  rol: Role;
}

export interface UsuarioUpdateRequest {
  nombre: string;
  rol: Role;
  activo: boolean;
}
