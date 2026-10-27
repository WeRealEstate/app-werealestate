/** Score de ventas (ver backend ScoreService): ventas y lotes de cada asesor en un periodo. */
export type TipoAsesorScore = 'INTERNO' | 'EXTERNO';

export interface AsesorScore {
  /** "U-12" (usuario interno) o "E-5" (asesor externo). */
  clave: string;
  nombre: string;
  tipo: TipoAsesorScore;
  ventas: number;
  lotes: number;
}

export interface RankingScore {
  /** yyyy-MM-dd; null = sin límite ("Todo"). */
  desde: string | null;
  hasta: string | null;
  /** Ventas distintas del periodo: una compartida cuenta una vez aquí aunque sume a sus dos asesores. */
  totalVentas: number;
  /** De más a menos ventas; con las mismas, por nombre. */
  asesores: AsesorScore[];
}

export interface PosicionScore extends AsesorScore {
  posicion: number;
}

/** Numera el ranking (ya ordenado de más a menos ventas): con las mismas ventas comparten lugar y el siguiente lugar
 * no salta (1, 2, 2, 3). Se numera en el panel, no en el servidor, porque el filtro Internos/Externos cambia los lugares. */
export function conPosiciones(asesores: AsesorScore[]): PosicionScore[] {
  let posicion = 0;
  let ventasAnteriores: number | null = null;
  return asesores.map((a) => {
    if (a.ventas !== ventasAnteriores) {
      posicion++;
      ventasAnteriores = a.ventas;
    }
    return { ...a, posicion };
  });
}
