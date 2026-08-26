export type Role = 'ASESOR' | 'LIDER_AREA' | 'EQUIPO_INTERNO' | 'ADMIN';

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
