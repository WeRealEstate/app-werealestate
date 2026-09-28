/** INDEPENDIENTE (default, trabaja solo) / LIDER (encabeza un equipo) / LINEA (reporta a alguien
 * — ver liderDirectoId/nivelLinea) dentro de la estructura de Teams. Tope de 3 niveles: Líder →
 * Línea 1 → Línea 2. */
export type TipoAsesorExterno = 'INDEPENDIENTE' | 'LIDER' | 'LINEA';

/** Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol) — solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un usuario interno. */
export interface AsesorExterno {
  id: number;
  nombre: string;
  /** null en asesores registrados antes de que se pidieran estos datos (ver migración V35). */
  celular: string | null;
  correo: string | null;
  activo: boolean;
  tipo: TipoAsesorExterno;
  /** Solo si tipo = 'LINEA': a quién reporta (un LIDER en línea 1, o un LINEA de línea 1 en línea 2). */
  liderDirectoId: number | null;
  liderDirectoNombre: string | null;
  /** Solo si tipo = 'LINEA': 1 o 2. Lo calcula el servidor, nunca se manda al actualizar. */
  nivelLinea: number | null;
}

export interface AsesorExternoCreateRequest {
  nombre: string;
  celular: string;
  correo: string;
}

/** celular/correo no son obligatorios aquí (a diferencia de AsesorExternoCreateRequest): un
 * asesor externo registrado antes de que existieran estos campos debe poder seguir editándose.
 * tipo/liderDirectoId son la jerarquía de Teams — la pantalla de "Asesores externos" los manda
 * sin cambios (tal como venían) al renombrar/activar; solo Teams los cambia de verdad. */
export interface AsesorExternoUpdateRequest {
  nombre: string;
  celular: string | null;
  correo: string | null;
  activo: boolean;
  tipo: TipoAsesorExterno;
  liderDirectoId: number | null;
}
