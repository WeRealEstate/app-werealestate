import { Promocion } from '../models/promocion.model';
import { SUPERFICIE_MACROLOTE_M2 } from '../../features/panel/cotizador/cotizador-datos';

/** Bono de una promoción de bono por lotes para esa cantidad de lotes (0 = no aplica). */
export function bonoDe(promo: Promocion, lotes: number): number {
  if (lotes >= 3) return promo.bonoTresLotes ?? 0;
  if (lotes === 2) return promo.bonoDosLotes ?? 0;
  return promo.bonoUnLote ?? 0;
}

/** Parte del bono de cada lote: partes iguales al centavo; lo que sobra, al último. */
export function partesDelBono(bono: number, lotes: number): number[] {
  if (lotes <= 0) return [];
  const centavos = Math.round(bono * 100);
  const parte = Math.floor(centavos / lotes);
  return Array.from({ length: lotes }, (_, i) => (i === lotes - 1 ? centavos - parte * (lotes - 1) : parte) / 100);
}

/** Por qué una promoción no aplica a esos lotes (mismo criterio que el servidor); null = sí aplica. */
export function motivoNoAplica(promo: Promocion, lotes: { superficie: number; desarrollo: string }[]): string | null {
  if (lotes.length === 0) return 'Agrega los lotes para calcular el bono.';
  const proyecto = promo.proyecto === 'nanuu' ? 'Aldea Nanuu' : 'SAMAI Campestre';
  if (lotes.some((l) => l.desarrollo !== proyecto)) return `Esta promoción solo aplica a lotes de ${proyecto}.`;
  const hectarea = promo.tipoPrecio === 'HECTAREA';
  if (lotes.some((l) => l.superficie > SUPERFICIE_MACROLOTE_M2 !== hectarea)) {
    return `Esta promoción solo aplica a ${hectarea ? 'hectáreas' : 'lotes (no hectáreas)'}.`;
  }
  if (promo.modalidad === 'BONO_LOTES' && !(bonoDe(promo, lotes.length) > 0)) {
    return `Esta promoción no aplica a ${lotes.length} lote(s).`;
  }
  return null;
}
