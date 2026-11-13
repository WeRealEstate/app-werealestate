import type { jsPDF } from 'jspdf';
import { AZUL_WE, FUENTE, GRIS, LINEA_FILA, MARGEN, TEXTO, dinero, etiquetaConValor, linea, numero, pie, tituloSeccion } from './pdf-base';
import type { QuotePdfData } from './pdf-tipos';

/** Primera hoja de la cotización: encabezado, datos, detalle de la inversión, mensualidad, total y aviso. */

const AVISO_VIGENCIA =
  'Esta cotización tiene una vigencia de 20 días naturales a partir de su fecha de emisión y está sujeta a disponibilidad, condiciones comerciales vigentes y validación por parte de WE Real Estate. No constituye contrato, apartado o promesa de compraventa.';

export function dibujarHojaCotizacion(doc: jsPDF, data: QuotePdfData): void {
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();

  dibujarEncabezado(doc, data, pageWidth);
  let y = dibujarDatosCotizacion(doc, data, pageWidth);
  y = dibujarDetalleInversion(doc, data, y, pageWidth);
  y = dibujarMensualidad(doc, data, y, pageWidth);
  y = dibujarTotal(doc, data, y, pageWidth);
  dibujarInformacionImportante(doc, y, pageWidth);

  pie(doc, 'WE Real Estate · Cotización informativa', 'Página 1', pageWidth, pageHeight);
}

/** Sin banda: título, fecha y vigencia, línea azul; debajo el nombre del proyecto y su ubicación. */
function dibujarEncabezado(doc: jsPDF, data: QuotePdfData, pageWidth: number): void {
  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(15);
  doc.text('PROPUESTA DE INVERSIÓN', MARGEN, 18);

  doc.setFontSize(7);
  doc.text(`Fecha: ${data.date}`, pageWidth - MARGEN, 17, { align: 'right' });

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7);
  doc.text('Vigencia de la cotización: 20 días', pageWidth - MARGEN, 21.5, { align: 'right' });

  doc.setDrawColor(...AZUL_WE);
  doc.setLineWidth(0.5);
  doc.line(MARGEN, 24, pageWidth - MARGEN, 24);

  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(12);
  doc.text(data.project, MARGEN, 36);

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(8);
  doc.text(data.location, MARGEN, 41.5);
}

/** Asesor, cliente, manzana y lote en dos columnas. Devuelve la y donde sigue la siguiente sección. */
function dibujarDatosCotizacion(doc: jsPDF, data: QuotePdfData, pageWidth: number): number {
  const y = tituloSeccion(doc, 'DATOS DE LA COTIZACIÓN', 55, pageWidth) + 4.5;

  const campos: [string, string, number, number][] = [
    ['Nombre del asesor', data.advisorName || 'No especificado', MARGEN, 0],
    ['Nombre del cliente', data.clientName || 'No especificado', 105, 0],
    ['Manzana', data.blockNumber || 'No especificado', MARGEN, 11],
    ['Lote', data.lotNumber || 'No especificado', 105, 11],
  ];
  for (const [etiqueta, valor, x, dy] of campos) {
    etiquetaConValor(doc, etiqueta, valor, x, y + dy);
  }
  return y + 22 + 10;
}

function dibujarDetalleInversion(doc: jsPDF, data: QuotePdfData, yInicial: number, pageWidth: number): number {
  let y = tituloSeccion(doc, 'DETALLE DE LA INVERSIÓN', yInicial, pageWidth);
  const rows = renglonesDetalle(data);

  // 9 mm por renglón con los 8 de siempre; con más (anualidades o aportaciones junto al enganche, varios
  // lotes) bajan a 8 mm para que el aviso de abajo no se encime con el pie de página.
  const rowHeight = rows.length > 8 ? 8 : 9;
  const rowTextY = rowHeight - 3;

  for (const [label, value] of rows) {
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(...GRIS);
    doc.text(label, MARGEN, y + rowTextY);

    doc.setFont(FUENTE, 'bold');
    doc.setTextColor(...TEXTO);
    doc.text(value, pageWidth - MARGEN, y + rowTextY, { align: 'right' });

    linea(doc, MARGEN, y + rowHeight, pageWidth - MARGEN, y + rowHeight, 0.15, LINEA_FILA);
    y += rowHeight;
  }
  return y;
}

function renglonesDetalle(data: QuotePdfData): [string, string][] {
  return [
    ['Proyecto', data.project],
    ...renglonesLotes(data),
    ['Monto de inversión', dinero(data.totalPrice)],
    ['Forma de pago', data.paymentMethod],
    ...(data.downPayment > 0 || !data.pagoExtra
      ? [[data.downPaymentLabel, dinero(data.downPayment)] as [string, string]]
      : []),
    ...(data.pagoExtra
      ? [[data.pagoExtra.label, dinero(data.pagoExtra.monto)] as [string, string]]
      : []),
    ['Saldo', dinero(data.financedAmount)],
    ['Intereses', dinero(data.interestAmount)],
  ];
}

/** Con un solo lote, una fila de Superficie y una de Precio por m² (como siempre). Con varios
 * lotes juntos, una fila por lote (cada uno con su propia superficie y precio) en vez de un
 * total/promedio que escondería el detalle que el cliente necesita ver. */
function renglonesLotes(data: QuotePdfData): [string, string][] {
  if (data.lotes.length > 1) {
    return data.lotes.map((lote) => [
      `Lote ${lote.manzana || '—'}-${lote.lote || '—'}`,
      `${numero(lote.superficie)} m² · ${dinero(lote.pricePerM2)}/m²`,
    ]);
  }
  const precioEspecial = data.descuentoContado ?? data.paymentMethod === 'Contado';
  return [
    ['Superficie', `${numero(data.area)} m²`],
    [
      precioEspecial ? 'Modalidad de precio' : 'Precio por m²',
      precioEspecial ? 'Precio especial de contado' : dinero(data.pricePerM2),
    ],
  ];
}

/** Mensualidad entre dos líneas grises, sin caja. */
function dibujarMensualidad(doc: jsPDF, data: QuotePdfData, yAnterior: number, pageWidth: number): number {
  const y = yAnterior + 7;
  linea(doc, MARGEN, y, pageWidth - MARGEN, y, 0.25, GRIS);
  linea(doc, MARGEN, y + 15, pageWidth - MARGEN, y + 15, 0.25, GRIS);

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(6.5);
  doc.text('Mensualidad estimada', MARGEN, y + 5.5);

  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(8.5);
  doc.text(dinero(data.monthlyPayment), MARGEN, y + 10.5);

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.text(`${data.months} meses`, pageWidth - MARGEN, y + 10.5, { align: 'right' });
  return y;
}

/** Total de inversión, sin caja. */
function dibujarTotal(doc: jsPDF, data: QuotePdfData, yMensualidad: number, pageWidth: number): number {
  const y = yMensualidad + 15 + 8;
  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(8.5);
  doc.text('Total de inversión', MARGEN, y);
  doc.text(dinero(data.totalInvestment), pageWidth - MARGEN, y, { align: 'right' });
  return y;
}

function dibujarInformacionImportante(doc: jsPDF, yTotal: number, pageWidth: number): void {
  const ancho = pageWidth - 2 * MARGEN;

  // Medido con la letra con que se escribe (7 pt) para saber cuántos renglones ocupa.
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7);
  const noticeLines = doc.splitTextToSize(AVISO_VIGENCIA, ancho);

  const y = tituloSeccion(doc, 'INFORMACIÓN IMPORTANTE', yTotal + 10, pageWidth) + 5;

  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7);
  doc.text(noticeLines, MARGEN, y, { lineHeightFactor: 1.5 });
}
