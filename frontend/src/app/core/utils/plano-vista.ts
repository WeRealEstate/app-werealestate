import type { Lote, PuntoMapa } from '../models/lote.model';

/** Matemática y utilidades compartidas por el plano interno (/panel/plano) y el público (/samai, /aldea-nanuu):
 * el visor escala y desplaza (transform) un div con la imagen y los polígonos, así que las posiciones en % de
 * los vértices nunca cambian; aquí se convierte entre pantalla y contenido. `rect` siempre es el rect
 * renderizado real del contenedor (getBoundingClientRect), que debe medir exactamente lo que la imagen. */

export interface Desplazamiento {
  x: number;
  y: number;
}

export function limitar(valor: number, minimo: number, maximo: number): number {
  return Math.min(maximo, Math.max(minimo, valor));
}

/** Nuevo desplazamiento para acercar/alejar manteniendo fijo, bajo `punto` (px dentro del contenedor), el mismo
 * punto de la imagen: así la vista no "salta" al hacer zoom con la rueda sobre un lote en particular. */
export function desplazamientoAlHacerZoom(
  punto: Desplazamiento,
  pan: Desplazamiento,
  zoomActual: number,
  zoomFinal: number,
): Desplazamiento {
  const contenidoX = (punto.x - pan.x) / zoomActual;
  const contenidoY = (punto.y - pan.y) / zoomActual;
  return { x: punto.x - contenidoX * zoomFinal, y: punto.y - contenidoY * zoomFinal };
}

/** Acota el desplazamiento de un eje para que el contenido (más grande que la vista) no deje hueco en sus bordes. */
export function acotarAlBorde(pan: number, tamanoVista: number, tamanoContenido: number): number {
  return Math.min(0, Math.max(tamanoVista - tamanoContenido, pan));
}

/** Como acotarAlBorde, pero si el contenido cabe en la vista no hay nada que desplazar: se centra. */
export function acotarOCentrar(pan: number, tamanoVista: number, tamanoContenido: number): number {
  return tamanoContenido <= tamanoVista ? (tamanoVista - tamanoContenido) / 2 : acotarAlBorde(pan, tamanoVista, tamanoContenido);
}

/** Posición de pantalla (clientX/Y) → % dentro de la imagen sin escalar, deshaciendo el pan/zoom del visor: el
 * punto de contenido bajo el cursor es ((posición en el contenedor) - pan) / zoom. Acotado a 0-100 y con
 * 4 decimales: a 2 (lo que había antes) cada paso de redondeo ya se sentía como un salto/imán en vez de seguir
 * el cursor libremente en cuanto el zoom pasaba de unos cientos por ciento. */
export function puntoEnImagen(clientX: number, clientY: number, rect: DOMRect, pan: Desplazamiento, zoom: number): PuntoMapa {
  const innerX = (clientX - rect.left - pan.x) / zoom;
  const innerY = (clientY - rect.top - pan.y) / zoom;
  const x = Math.min(100, Math.max(0, (innerX / rect.width) * 100));
  const y = Math.min(100, Math.max(0, (innerY / rect.height) * 100));
  return { x: Math.round(x * 10000) / 10000, y: Math.round(y * 10000) / 10000 };
}

export function distanciaPuntoSegmento(p: PuntoMapa, a: PuntoMapa, b: PuntoMapa): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const largoCuadrado = dx * dx + dy * dy;
  if (largoCuadrado === 0) return Math.hypot(p.x - a.x, p.y - a.y);
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / largoCuadrado));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

/** Copia del polígono con `nuevo` insertado justo después del borde más cercano a él (el primero si empatan). */
export function insertarEnBordeMasCercano(puntos: PuntoMapa[], nuevo: PuntoMapa): PuntoMapa[] {
  let mejorIndice = 0;
  let mejorDistancia = Infinity;
  for (let i = 0; i < puntos.length; i++) {
    const distancia = distanciaPuntoSegmento(nuevo, puntos[i], puntos[(i + 1) % puntos.length]);
    if (distancia < mejorDistancia) {
      mejorDistancia = distancia;
      mejorIndice = i;
    }
  }
  const actualizados = [...puntos];
  actualizados.splice(mejorIndice + 1, 0, nuevo);
  return actualizados;
}

export function tienePoligono(lote: Lote): boolean {
  return !!lote.mapaPoligono && lote.mapaPoligono.length >= 3;
}

/** Convierte los vértices en el atributo "points" que espera <polygon>/<polyline>. */
export function puntosSvg(puntos: PuntoMapa[]): string {
  return puntos.map((p) => `${p.x},${p.y}`).join(' ');
}

/** URL de la vista rápida del plano (`-preview.jpg`, ~450 KB) que se muestra mientras llega la imagen HD. */
export function urlVistaRapida(url: string): string {
  return url.replace(/\.(jpg|jpeg|png|webp)(?=\?|$)/i, '-preview.jpg');
}

/** Descarga y decodifica una imagen fuera del hilo principal; `alTerminar` se llama ya decodificada (o si el
 * navegador no sabe decodificar aparte) y `alFallar` si no se pudo descargar. */
export function cargarImagenEnSegundoPlano(url: string, alTerminar: () => void, alFallar: () => void): void {
  const imagen = new Image();
  imagen.decoding = 'async';
  imagen.src = url;
  imagen.onload = () => {
    const decodificar = typeof imagen.decode === 'function' ? imagen.decode() : Promise.resolve();
    decodificar.catch(() => undefined).then(alTerminar);
  };
  imagen.onerror = alFallar;
}

/** Marca o desmarca un lote en el modo "Seleccionar varios": solo lotes disponibles y hasta `maximo`.
 * Devuelve la selección nueva, o el mensaje de por qué no se pudo marcar. */
export function alternarLoteEnSeleccion(
  seleccion: number[],
  lote: Lote,
  disponible: boolean,
  maximo: number,
): { seleccion: number[] } | { error: string } {
  if (seleccion.includes(lote.id)) return { seleccion: seleccion.filter((id) => id !== lote.id) };
  if (!disponible) return { error: 'Solo se pueden elegir lotes disponibles.' };
  if (seleccion.length >= maximo) return { error: `Máximo ${maximo} lotes a la vez.` };
  return { seleccion: [...seleccion, lote.id] };
}
