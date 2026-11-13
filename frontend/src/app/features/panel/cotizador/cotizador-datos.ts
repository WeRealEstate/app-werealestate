import type { ProjectId } from '../../../core/data/proyectos-cotizador.config';
import type { TipoPago } from '../../../core/utils/formas-pago';

/** Datos fijos del Cotizador (proyectos, precios por m², tarjetas de forma de pago, meses) y utilidades puras de
 * captura que no dependen del estado del componente. */

export type { ProjectId } from '../../../core/data/proyectos-cotizador.config';

/** Un lote ya confirmado dentro de una cotización de varios lotes juntos. Cada uno guarda su
 * propio precio por m² (no uno solo compartido) porque el umbral de macrolote de SAMAI depende
 * de la superficie de ESE lote en particular. */
export interface LoteCotizado {
  manzana: string;
  lote: string;
  superficie: number;
  pricePerM2: number;
}

/** Lote estándar (m²): con él arranca cada proyecto y sobre él están calibrados el contado de SAMAI y las promociones. */
export const SUPERFICIE_ESTANDAR_M2 = 200;

/** A partir de esta superficie (m²), SAMAI cobra la tarifa de macrolote ($170/m², la misma tarifa
 * que antes solo se elegía a mano como "Hectarea") en vez del precio de lote normal ($800/m²). */
export const SUPERFICIE_MACROLOTE_M2 = 8000;

/** Precios por m² de lista. */
export const PRECIO_M2_SAMAI = 800;
export const PRECIO_M2_MACROLOTE = 170;
export const PRECIO_M2_NANUU = 3700;
/** SAMAI de contado: el lote estándar tiene precio fijo y los mayores (fuera de macrolote) bajan a este precio por m². */
export const PRECIO_CONTADO_LOTE_ESTANDAR_SAMAI = 120000;
export const PRECIO_M2_CONTADO_SAMAI = 600;

/** Nombre (como va en el PDF y el historial), nombre de portada, ubicación e imagen de cada proyecto. */
export const PROYECTOS_COTIZADOR: Record<ProjectId, { nombre: string; portada: string; ubicacion: string; imagen: string }> = {
  samai: {
    nombre: 'SAMAI Campestre',
    portada: 'SAMAI CAMPESTRE',
    ubicacion: 'San José La Ciénega, Pochutla, Oaxaca',
    imagen: '/images/samai-cover.png',
  },
  nanuu: {
    nombre: 'Aldea Nanuu',
    portada: 'ALDEA NANUU',
    ubicacion: 'Cuatunalco, Huatulco, Oaxaca',
    imagen: '/images/nanuu-cover.png',
  },
};

/** Las tarjetas de forma de pago de cada proyecto, en el orden en que se muestran. */
export const OPCIONES_PAGO: Record<ProjectId, { tipo: TipoPago; titulo: string; detalle: string }[]> = {
  samai: [
    { tipo: 'msi', titulo: 'Sin enganche', detalle: 'Hasta 60 MSI' },
    { tipo: 'downpayment', titulo: 'Con enganche', detalle: 'Tú decides el monto' },
    { tipo: 'initial', titulo: 'Pago inicial', detalle: 'Baja tu mensualidad' },
    { tipo: 'annualities', titulo: 'Con anualidades', detalle: 'Aportación anual' },
    { tipo: 'aportaciones', titulo: 'Con aportaciones', detalle: 'Varias por año, cada una en su mes' },
    { tipo: 'cash', titulo: 'Contado', detalle: 'Precio especial, pocos lotes' },
  ],
  nanuu: [
    { tipo: 'downpayment', titulo: 'Con enganche', detalle: 'Tú decides el monto' },
    { tipo: 'msi', titulo: 'Sin enganche', detalle: '0% de enganche' },
    { tipo: 'initial', titulo: 'Pago inicial', detalle: 'Baja tu mensualidad' },
    { tipo: 'annualities', titulo: 'Con anualidades', detalle: 'Aportación anual' },
    { tipo: 'aportaciones', titulo: 'Con aportaciones', detalle: 'Varias por año, cada una en su mes' },
    { tipo: 'cash', titulo: 'Contado', detalle: 'Pago único, sin intereses' },
  ],
};

/** Selector de "mes de la aportación" (Anualidades y Promoción). */
export const MESES_DEL_ANIO: { value: number; label: string }[] = [
  { value: 1, label: 'Enero' },
  { value: 2, label: 'Febrero' },
  { value: 3, label: 'Marzo' },
  { value: 4, label: 'Abril' },
  { value: 5, label: 'Mayo' },
  { value: 6, label: 'Junio' },
  { value: 7, label: 'Julio' },
  { value: 8, label: 'Agosto' },
  { value: 9, label: 'Septiembre' },
  { value: 10, label: 'Octubre' },
  { value: 11, label: 'Noviembre' },
  { value: 12, label: 'Diciembre' },
];

/** Compara manzana/lote como números cuando se puede (así "2" queda antes que "10" en vez del
 * orden alfabético de texto); si alguno no es numérico, cae a orden alfabético normal. */
export function compararNatural(a: string, b: string): number {
  const numeroA = Number(a);
  const numeroB = Number(b);
  if (!Number.isNaN(numeroA) && !Number.isNaN(numeroB)) return numeroA - numeroB;
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

/** Superficie personalizada tal como se escribe: solo números, un punto y hasta 2 decimales. Devuelve el valor
 * para los cálculos y el texto con comas de miles para mostrar; null si quedó vacío. */
export function leerSuperficieEscrita(texto: string): { valor: number; texto: string } | null {
  // Sin comas, solo números y un punto decimal.
  let crudo = texto.replace(/,/g, '').replace(/[^0-9.]/g, '');

  const partes = crudo.split('.');
  if (partes.length > 2) {
    crudo = partes[0] + '.' + partes.slice(1).join('');
  }
  if (crudo.includes('.')) {
    const [entero, decimales] = crudo.split('.');
    crudo = entero + '.' + decimales.slice(0, 2);
  }
  if (!crudo) return null;

  const [entero, decimales] = crudo.split('.');
  const enteroConComas = Number(entero || 0).toLocaleString('en-US');
  return {
    valor: Number(crudo),
    texto: decimales !== undefined ? `${enteroConComas}.${decimales}` : enteroConComas,
  };
}

/** "a", "a y b", "a, b y c". */
export function enumerar(elementos: string[]): string {
  return elementos.length === 1 ? elementos[0] : `${elementos.slice(0, -1).join(', ')} y ${elementos[elementos.length - 1]}`;
}
