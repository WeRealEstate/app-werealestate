import type { GState as EstadoGrafico, jsPDF } from 'jspdf';
import { AZUL_WE, BORDE, FUENTE, GRIS, TEXTO, cargarFuentes, cargarJsPdf, linea } from './pdf-base';
import type { PlanoPdfData } from './pdf-tipos';

/** Contorno de cada lote en el PDF del plano: fino (≈0.3 pt) para que siga la línea del propio plano
 * en vez de taparla, y relleno ligero para que se lean los números y medidas de cada lote. */
const GROSOR_CONTORNO_LOTE_PDF_MM = 0.1;
const OPACIDAD_RELLENO_LOTE_PDF = 0.25;

const ANCHO_HOJA = 420; // ancho de una A3 horizontal
/** Los tamaños de texto y márgenes se escalan respecto de la hoja carta horizontal (279.4 mm) con la que se diseñó. */
const K = ANCHO_HOJA / 279.4;
const MARGEN_TEXTO = 4 * K; // solo separa los textos de las franjas del borde de la hoja
const ALTO_ENCABEZADO = 10 * K;
const ALTO_LEYENDA = 8.5 * K;
const ALTO_MAXIMO = 594; // lado largo de una A2: tope para planos muy altos

interface Medidas {
  pageHeight: number;
  drawX: number;
  drawY: number;
  drawWidth: number;
  drawHeight: number;
}

/** Hoja a la medida del plano: del ancho de una A3 y con el alto que pide la proporción de la imagen,
 * así el plano llena la hoja de borde a borde, sin márgenes ni franjas vacías. Solo hay una franja de
 * marca con el título arriba y otra con la leyenda debajo. */
export async function armarPdfPlano(data: PlanoPdfData): Promise<jsPDF> {
  const { jspdf } = await cargarJsPdf();
  const { jsPDF, GState } = jspdf;
  const medidas = medirHoja(data.anchoImagen, data.altoImagen);

  // jsPDF intercambia ancho y alto si la orientación no coincide con la forma de la hoja: un plano
  // casi cuadrado o vertical daría una hoja más alta que ancha y saldría cortado. Se elige la que toca.
  const doc = new jsPDF({
    orientation: ANCHO_HOJA >= medidas.pageHeight ? 'landscape' : 'portrait',
    unit: 'mm',
    format: [ANCHO_HOJA, medidas.pageHeight],
  });
  await cargarFuentes(doc);

  dibujarEncabezado(doc, data);

  // IMAGEN DEL PLANO, de borde a borde. El JPEG original se incrusta tal cual ('NONE': sin
  // recomprimir), no repintado en un canvas.
  const { drawX, drawY, drawWidth, drawHeight } = medidas;
  doc.addImage(data.imagenBytes, 'JPEG', drawX, drawY, drawWidth, drawHeight, undefined, 'NONE');

  dibujarPoligonos(doc, new GState({ opacity: OPACIDAD_RELLENO_LOTE_PDF }), new GState({ opacity: 1 }), data, medidas);

  linea(doc, 0, drawY + drawHeight + 0.15, ANCHO_HOJA, drawY + drawHeight + 0.15, 0.15, BORDE);
  dibujarLeyenda(doc, data, drawY + drawHeight + ALTO_LEYENDA / 2 + 0.8 * K);
  return doc;
}

/** La imagen toma todo el ancho de la hoja; el alto sale de su proporción (con tope de una A2). */
function medirHoja(anchoImagen: number, altoImagen: number): Medidas {
  const aspecto = anchoImagen / altoImagen;
  let drawWidth = ANCHO_HOJA;
  let drawHeight = drawWidth / aspecto;
  let pageHeight = ALTO_ENCABEZADO + drawHeight + ALTO_LEYENDA;
  if (pageHeight > ALTO_MAXIMO) {
    pageHeight = ALTO_MAXIMO;
    drawHeight = pageHeight - ALTO_ENCABEZADO - ALTO_LEYENDA;
    drawWidth = drawHeight * aspecto;
  }
  return { pageHeight, drawX: (ANCHO_HOJA - drawWidth) / 2, drawY: ALTO_ENCABEZADO, drawWidth, drawHeight };
}

/** Sobre fondo blanco: título, fecha gris a la derecha y línea azul bajo la franja. */
function dibujarEncabezado(doc: jsPDF, data: PlanoPdfData): void {
  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(11 * K);
  doc.text(`Plano interactivo · ${data.desarrolloNombre}`, MARGEN_TEXTO, ALTO_ENCABEZADO / 2 + 1.5 * K);

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7 * K);
  doc.text(`Generado el ${data.fecha}`, ANCHO_HOJA - MARGEN_TEXTO, ALTO_ENCABEZADO / 2 + 1.5 * K, { align: 'right' });

  linea(doc, 0, ALTO_ENCABEZADO - 0.15, ANCHO_HOJA, ALTO_ENCABEZADO - 0.15, 0.5, AZUL_WE);
}

/** Lotes como figuras vectoriales. Contorno fino del color del estado, para seguir la línea del propio plano
 * sin taparla; relleno ligero, para que se lean los números y medidas de cada lote. */
function dibujarPoligonos(
  doc: jsPDF,
  rellenoLigero: EstadoGrafico,
  opaco: EstadoGrafico,
  data: PlanoPdfData,
  { drawX, drawY, drawWidth, drawHeight }: Medidas,
): void {
  doc.setLineWidth(GROSOR_CONTORNO_LOTE_PDF_MM);
  doc.setLineJoin('round');

  for (const poligono of data.poligonos) {
    if (poligono.puntos.length < 3) continue;

    const absolutos = poligono.puntos.map((p) => ({
      x: drawX + (p.x / 100) * drawWidth,
      y: drawY + (p.y / 100) * drawHeight,
    }));
    // jsPDF.lines recibe cada vértice como desplazamiento respecto del anterior.
    const segmentos = absolutos.slice(1).map((p, i) => [p.x - absolutos[i].x, p.y - absolutos[i].y]);

    doc.setGState(rellenoLigero);
    doc.setFillColor(...poligono.color);
    doc.lines(segmentos, absolutos[0].x, absolutos[0].y, [1, 1], 'F', true);

    doc.setGState(opaco);
    doc.setDrawColor(...poligono.color);
    doc.lines(segmentos, absolutos[0].x, absolutos[0].y, [1, 1], 'S', true);
  }
  doc.setGState(opaco);
}

/** Puntos de color con su etiqueta y, a la derecha, la nota al pie; todo en una franja bajo el plano. */
function dibujarLeyenda(doc: jsPDF, data: PlanoPdfData, legendY: number): void {
  let legendX = MARGEN_TEXTO;
  const radioPunto = 1.5 * K;

  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7 * K);
  for (const item of data.leyenda) {
    doc.setFillColor(...item.color);
    doc.circle(legendX + radioPunto, legendY - 0.9 * K, radioPunto, 'F');

    doc.setTextColor(...TEXTO);
    doc.text(item.label, legendX + radioPunto * 2 + 1.6 * K, legendY);

    legendX += radioPunto * 2 + 1.6 * K + doc.getTextWidth(item.label) + 8 * K;
  }

  doc.setTextColor(...GRIS);
  doc.setFontSize(6 * K);
  doc.text('WE Real Estate · Plano informativo, sujeto a cambios', ANCHO_HOJA - MARGEN_TEXTO, legendY, { align: 'right' });
}
