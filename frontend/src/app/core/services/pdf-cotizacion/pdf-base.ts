import type { jsPDF } from 'jspdf';
import type autoTableTipo from 'jspdf-autotable';
import type { CellHookData } from 'jspdf-autotable';

/** Piezas comunes de los PDF de cotización y de plano: carga perezosa de jsPDF, fuente Montserrat, colores,
 * formato de cantidades y los trazos repetidos (líneas, títulos de sección, pie, encabezado y estilo de tabla). */

export type Rgb = [number, number, number];
export type AutoTable = typeof autoTableTipo;

/** jspdf y jspdf-autotable pesan ~400 KB: se descargan la primera vez que se genera un PDF (cotización o plano), no
 * al abrir el Cotizador o el Plano. Aquí solo se importan sus tipos, que no llegan al navegador. */
let librerias: { jspdf: typeof import('jspdf'); autoTable: AutoTable } | null = null;

export async function cargarJsPdf(): Promise<{ jspdf: typeof import('jspdf'); autoTable: AutoTable }> {
  if (!librerias) {
    const [jspdf, tablas] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    librerias = { jspdf, autoTable: tablas.default };
  }
  return librerias;
}

export const FUENTE = 'Montserrat';

/** Único color del PDF: la línea delgada del encabezado. Todo lo demás va en negro y gris. */
export const AZUL_WE: Rgb = [36, 63, 184];
export const TEXTO: Rgb = [26, 26, 26];
export const GRIS: Rgb = [115, 115, 115];
export const BORDE: Rgb = [200, 200, 200];
/** Línea fina entre renglones de listas y tablas. */
export const LINEA_FILA: Rgb = [225, 225, 225];

/** Margen lateral de las hojas carta (cotización y amortización). */
export const MARGEN = 20;

// ==============================
// FUENTE MONTSERRAT
// ==============================

export async function cargarFuentes(doc: jsPDF): Promise<void> {
  const regular = await fuenteEnBase64('/fonts/Montserrat-Regular.ttf');
  const bold = await fuenteEnBase64('/fonts/Montserrat-Bold.ttf');

  doc.addFileToVFS('Montserrat-Regular.ttf', regular);
  doc.addFont('Montserrat-Regular.ttf', FUENTE, 'normal');

  doc.addFileToVFS('Montserrat-Bold.ttf', bold);
  doc.addFont('Montserrat-Bold.ttf', FUENTE, 'bold');

  // Fuente predeterminada del documento
  doc.setFont(FUENTE, 'normal');
}

async function fuenteEnBase64(url: string): Promise<string> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`No se pudo cargar la fuente: ${url}`);
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// ==============================
// FORMATEADORES
// ==============================

export function numero(value: number): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

export function dinero(value: number): string {
  return `$${numero(value)} MXN`;
}

// ==============================
// ESTILO COMÚN (sin cajas ni rellenos)
// ==============================

export function linea(doc: jsPDF, x1: number, y1: number, x2: number, y2: number, grosor: number, color: Rgb): void {
  doc.setDrawColor(...color);
  doc.setLineWidth(grosor);
  doc.line(x1, y1, x2, y2);
}

/** Etiqueta gris chica con su valor en negritas debajo (datos de la cotización y resumen del financiamiento). */
export function etiquetaConValor(doc: jsPDF, etiqueta: string, valor: string, x: number, y: number): void {
  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(6.5);
  doc.text(etiqueta, x, y);
  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(8.5);
  doc.text(valor, x, y + 4.2);
}

/** Encabezado de las hojas de amortización: la primera lleva el título; las que siguen solo la línea azul con el
 * dato gris, sin repetir el título. */
export function encabezado(doc: jsPDF, titulo: string, derecha: string, pageWidth: number, primera: boolean): void {
  if (primera) {
    doc.setTextColor(...TEXTO);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(15);
    doc.text(titulo, MARGEN, 18);
  }
  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(7);
  doc.text(derecha, pageWidth - MARGEN, primera ? 18 : 14.5, { align: 'right' });

  const yLinea = primera ? 22 : 18;
  linea(doc, MARGEN, yLinea, pageWidth - MARGEN, yLinea, 0.5, AZUL_WE);
}

/** Título de sección en mayúsculas chicas con una línea gris fina debajo. Devuelve la y de la línea. */
export function tituloSeccion(doc: jsPDF, titulo: string, y: number, pageWidth: number): number {
  doc.setTextColor(...TEXTO);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(7.5);
  doc.text(titulo, MARGEN, y, { charSpace: 0.5 });
  linea(doc, MARGEN, y + 1.6, pageWidth - MARGEN, y + 1.6, 0.25, BORDE);
  return y + 1.6;
}

/** Subtítulo de una tabla (p. ej. «CON ENGANCHE»): texto azul chico, sin barra de color. */
export function subtituloTabla(doc: jsPDF, texto: string, y: number): void {
  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'bold');
  doc.setFontSize(7.5);
  doc.text(texto, MARGEN, y, { charSpace: 0.5 });
}

export function pie(doc: jsPDF, izquierda: string, derecha: string, pageWidth: number, pageHeight: number): void {
  linea(doc, MARGEN, pageHeight - 12, pageWidth - MARGEN, pageHeight - 12, 0.15, BORDE);
  doc.setTextColor(...GRIS);
  doc.setFont(FUENTE, 'normal');
  doc.setFontSize(6.5);
  doc.text(izquierda, MARGEN, pageHeight - 7);
  doc.text(derecha, pageWidth - MARGEN, pageHeight - 7, { align: 'right' });
}

/** Tabla solo con líneas horizontales: encabezado en texto azul con línea azul, renglones con línea gris
 * fina, sin rellenos. El que llama pone columnas, márgenes y datos. */
export function estiloTabla(fontSize: number) {
  return {
    theme: 'plain' as const,
    styles: {
      font: FUENTE,
      fontSize,
      cellPadding: { top: 2.3, bottom: 2.3, left: 1.5, right: 1.5 },
      textColor: TEXTO,
      lineWidth: 0,
      valign: 'middle' as const,
    },
    headStyles: { font: FUENTE, fontStyle: 'bold' as const, fillColor: false as const, textColor: TEXTO, fontSize: 6.5 },
    footStyles: { font: FUENTE, fontStyle: 'bold' as const, fillColor: false as const, textColor: TEXTO },
    // El encabezado se alinea como su columna (autoTable no lo hereda del columnStyles).
    didParseCell: (hook: CellHookData) => alinearEncabezado(hook),
    didDrawCell: (hook: CellHookData) => {
      const { x, y, width, height } = hook.cell;
      if (hook.section === 'foot') linea(hook.doc, x, y, x + width, y, 0.4, TEXTO);
      else if (hook.section === 'head') linea(hook.doc, x, y + height, x + width, y + height, 0.4, GRIS);
      else linea(hook.doc, x, y + height, x + width, y + height, 0.15, LINEA_FILA);
    },
  };
}

export function alinearEncabezado(hook: CellHookData): void {
  if (hook.section !== 'head') return;
  const columnas = hook.table.styles.columnStyles as Record<number, { halign?: 'left' | 'center' | 'right' }>;
  hook.cell.styles.halign = columnas?.[hook.column.index]?.halign ?? 'left';
}

/** y donde terminó la última tabla dibujada con autoTable. */
export function finUltimaTabla(doc: jsPDF): number | undefined {
  return (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY;
}
