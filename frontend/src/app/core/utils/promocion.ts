import { Promocion } from '../models/promocion.model';
import { SUPERFICIE_MACROLOTE_M2 } from '../../features/panel/cotizador/cotizador-datos';

/** Bono de una promoción de bono por lotes para EXACTAMENTE esa cantidad de lotes (0 = no tiene nivel para ella). */
export function bonoDe(promo: Pick<Promocion, 'bonos'>, lotes: number): number {
  return promo.bonos?.find((n) => n.lotes === lotes)?.bono ?? 0;
}

/** «3 lotes: −$30,000 · 6 lotes: −$70,000» para mostrar los niveles de una promoción. */
export function textoBonos(promo: Pick<Promocion, 'bonos'>): string {
  return (promo.bonos ?? [])
    .map((n) => `${n.lotes} ${n.lotes === 1 ? 'lote' : 'lotes'}: −$${n.bono.toLocaleString('en-US', { maximumFractionDigits: 2 })}`)
    .join(' · ');
}

/** «3, 4 o 6 lotes»: los números de lotes a los que aplica. */
export function lotesQueAplica(promo: Pick<Promocion, 'bonos'>): string {
  const n = (promo.bonos ?? []).map((x) => String(x.lotes));
  if (n.length <= 1) return `${n[0] ?? '—'} ${n[0] === '1' ? 'lote' : 'lotes'}`;
  return `${n.slice(0, -1).join(', ')} o ${n[n.length - 1]} lotes`;
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
