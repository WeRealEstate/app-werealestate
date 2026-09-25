/** Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol) — solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un usuario interno. */
export interface AsesorExterno {
  id: number;
  nombre: string;
  /** null en asesores registrados antes de que se pidieran estos datos (ver migración V35). */
  celular: string | null;
  correo: string | null;
  activo: boolean;
}

export interface AsesorExternoCreateRequest {
  nombre: string;
  celular: string;
  correo: string;
}

/** celular/correo no son obligatorios aquí (a diferencia de AsesorExternoCreateRequest): un
 * asesor externo registrado antes de que existieran estos campos debe poder seguir editándose. */
export interface AsesorExternoUpdateRequest {
  nombre: string;
  celular: string | null;
  correo: string | null;
  activo: boolean;
}
