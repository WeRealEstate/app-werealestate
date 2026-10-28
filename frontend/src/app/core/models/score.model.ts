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
  /** Ventas del periodo con al menos un asesor que compite, activo o no (las de solo administradores no cuentan); una
   * compartida cuenta una vez aquí aunque sume a sus dos asesores. */
  totalVentas: number;
  /** De más a menos ventas; con las mismas, por nombre. */
  asesores: AsesorScore[];
}

/** Página pública del cotizador: miClave = la fila de quien la ve (si su dispositivo mandó un PIN válido);
 * pinRechazado = el PIN guardado ya no sirve y hay que olvidarlo. */
export interface ScorePublico {
  ranking: RankingScore;
  miClave: string | null;
  pinRechazado: boolean;
}

export interface PosicionScore extends AsesorScore {
  posicion: number;
}

/** Numera el ranking (ya ordenado de más a menos ventas): con las mismas ventas comparten lugar y el siguiente lugar
 * no salta (1, 2, 2, 3). Lo usan la pantalla del score y el resumen del cotizador público. */
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
