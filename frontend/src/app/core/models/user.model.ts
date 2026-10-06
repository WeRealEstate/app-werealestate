export type Role = 'ASESOR' | 'LIDER_AREA' | 'EQUIPO_INTERNO' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  ASESOR: 'Asesor',
  LIDER_AREA: 'Administración',
  EQUIPO_INTERNO: 'Equipo interno',
  ADMIN: 'Administrador',
};

/** Secciones del panel que un admin puede activar o quitar por usuario; ver backend ModulosAcceso. */
export type Modulo = 'LEADS' | 'PIPELINE' | 'COTIZADOR' | 'LOTES' | 'PLANO' | 'VENTAS' | 'FINANZAS' | 'GASTOS' | 'CALENDARIO' | 'ASESORES_EXTERNOS' | 'COMUNIDADES';

/** Un módulo con los roles que pueden tenerlo y con los que arranca por defecto (viene del backend). */
export interface ModuloCatalogo {
  codigo: Modulo;
  etiqueta: string;
  permitidoPara: Role[];
  porDefectoPara: Role[];
}

export interface User {
  id: number;
  nombre: string;
  email: string;
  rol: Role;
  areaId: number | null;
  /** Módulos que puede usar hoy; un usuario guardado de una sesión anterior a esta función no lo trae
   * hasta que el panel lo refresca (ver AuthService.refrescarPerfil). */
  modulos?: Modulo[];
}

export interface LoginRequest {
  email: string;
  password: string;
  captchaToken: string;
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
  modulos: Modulo[];
}

export interface UsuarioCreateRequest {
  nombre: string;
  email: string;
  password: string;
  rol: Role;
  /** null = los que trae su rol por defecto. */
  modulos: Modulo[] | null;
}

export interface UsuarioUpdateRequest {
  nombre: string;
  rol: Role;
  activo: boolean;
  /** null = restablecer a los de su rol por defecto (al cambiarle el rol); para conservarlos, mandarlos. */
  modulos: Modulo[] | null;
}
