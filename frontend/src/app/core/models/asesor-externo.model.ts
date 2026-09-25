/** Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol) — solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un usuario interno. */
export interface AsesorExterno {
  id: number;
  nombre: string;
  activo: boolean;
}

export interface AsesorExternoCreateRequest {
  nombre: string;
}

export interface AsesorExternoUpdateRequest {
  nombre: string;
  activo: boolean;
}
