import type { jsPDF } from 'jspdf';
import type { CellHookData } from 'jspdf-autotable';
import {
  AutoTable,
  MARGEN,
  alinearEncabezado,
  dinero,
  encabezado,
  estiloTabla,
  etiquetaConValor,
  finUltimaTabla,
  pie,
  subtituloTabla,
  tituloSeccion,
} from './pdf-base';
import type { QuotePdfData } from './pdf-tipos';

/** Hojas que siguen a la cotización: planes de Nanuu, resumen del financiamiento, tabla de amortización y,
 * si hay, la lista de aportaciones programadas. */

const TITULO = 'TABLA DE AMORTIZACIÓN';
const PIE = 'WE Real Estate · Tabla de amortización';
/** Márgenes de las tablas que pueden pasar de hoja: dejan lugar para el encabezado repetido y el pie. */
const margenTablaLarga = () => ({ left: MARGEN, right: MARGEN, top: 26, bottom: 18 });

/** Planes fijos de Aldea Nanuu que se muestran como referencia arriba de la tabla. */
const PLANES_NANUU = [
  { months: 24, interestPercentage: 0 },
  { months: 36, interestPercentage: 5 },
  { months: 48, interestPercentage: 10 },
  { months: 60, interestPercentage: 20 },
];
const PORCENTAJE_ENGANCHE_NANUU = 20;

export function dibujarHojasAmortizacion(doc: jsPDF, autoTable: AutoTable, data: QuotePdfData): void {
  doc.addPage('letter', 'portrait');

  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();

  // Hoja donde empieza la tabla: las que siguen repiten el encabezado.
  const primeraHoja = doc.getNumberOfPages();
  const encabezadoHoja = (primera: boolean) => encabezado(doc, TITULO, data.project.toUpperCase(), pageWidth, primera);
  encabezadoHoja(true);

  let yTable = 34;
  if (data.project === 'Aldea Nanuu') {
    yTable = dibujarPlanesNanuu(doc, autoTable, data, yTable);
  }

  yTable = dibujarResumenFinanciamiento(doc, data, yTable, pageWidth);

  autoTable(doc, {
    startY: yTable + 14,
    head: [['# PAGO', 'MES', 'AÑO', 'PAGO', 'SALDO', 'ABONO ACUMULADO']],
    body: data.amortizationTable.map((row) => [
      row.paymentNumber,
      row.month,
      row.year,
      dinero(row.payment),
      dinero(row.balance),
      dinero(row.accumulatedPayment),
    ]),
    margin: margenTablaLarga(),
    ...estiloTabla(7.5),
    columnStyles: {
      0: { halign: 'center', cellWidth: 17 },
      1: { halign: 'left', cellWidth: 33 },
      2: { halign: 'center', cellWidth: 20 },
      3: { halign: 'right', cellWidth: 34 },
      4: { halign: 'right', cellWidth: 34 },
      5: { halign: 'right' },
    },
    // Los meses que llevan una aportación se resaltan en negritas (sin relleno).
    didParseCell: (hook: CellHookData) => {
      alinearEncabezado(hook);
      if (hook.section === 'body' && data.amortizationTable[hook.row.index]?.esAportacion) {
        hook.cell.styles.fontStyle = 'bold';
      }
    },
    didDrawPage: () => {
      const currentPage = doc.getCurrentPageInfo().pageNumber;
      if (currentPage > primeraHoja) encabezadoHoja(false);
      pie(doc, PIE, `Página ${currentPage}`, pageWidth, pageHeight);
    },
  });

  if (data.aportaciones && data.aportaciones.length > 0) {
    dibujarResumenAportaciones(doc, autoTable, data, data.aportaciones, pageWidth, pageHeight);
  }
}

/** Monto financiado, plazo, mensualidad e intereses en cuatro columnas. Devuelve la y de la fila. */
function dibujarResumenFinanciamiento(doc: jsPDF, data: QuotePdfData, yInicial: number, pageWidth: number): number {
  const y = tituloSeccion(doc, 'RESUMEN DEL FINANCIAMIENTO', yInicial, pageWidth) + 4.5;
  const resumen: [string, string][] = [
    ['Monto financiado', dinero(data.financedAmount)],
    ['Plazo', `${data.months} meses`],
    ['Mensualidad', dinero(data.monthlyPayment)],
    ['Intereses', dinero(data.interestAmount)],
  ];
  resumen.forEach(([etiqueta, valor], i) => {
    etiquetaConValor(doc, etiqueta, valor, MARGEN + i * ((pageWidth - 2 * MARGEN) / 4), y);
  });
  return y;
}

/** Lista de las aportaciones (mes, año y monto) debajo de la tabla de amortización, con su total.
 * Si no cabe en la página donde terminó la tabla, pasa a una nueva. */
function dibujarResumenAportaciones(
  doc: jsPDF,
  autoTable: AutoTable,
  data: QuotePdfData,
  aportaciones: { mes: string; anio: number; monto: number }[],
  pageWidth: number,
  pageHeight: number,
): void {
  let y = (finUltimaTabla(doc) ?? 40) + 12;
  const encabezadoHoja = () => encabezado(doc, TITULO, data.project.toUpperCase(), pageWidth, false);

  // Título + encabezado + un par de filas deben caber; si no, página nueva.
  if (y > pageHeight - 50) {
    doc.addPage('letter', 'portrait');
    encabezadoHoja();
    y = 30;
  }
  const primeraHoja = doc.getNumberOfPages();

  y = tituloSeccion(doc, 'APORTACIONES PROGRAMADAS', y, pageWidth);

  const total = aportaciones.reduce((suma, a) => suma + a.monto, 0);

  autoTable(doc, {
    startY: y + 3,
    head: [['#', 'MES', 'AÑO', 'APORTACIÓN']],
    body: aportaciones.map((a, i) => [i + 1, a.mes, a.anio, dinero(a.monto)]),
    foot: [['', '', 'TOTAL', dinero(total)]],
    margin: margenTablaLarga(),
    ...estiloTabla(7.5),
    columnStyles: {
      0: { halign: 'center', cellWidth: 17 },
      1: { halign: 'left' },
      2: { halign: 'center', cellWidth: 25 },
      3: { halign: 'right', cellWidth: 45 },
    },
    didParseCell: (hook: CellHookData) => {
      alinearEncabezado(hook);
      if (hook.section === 'foot' && hook.column.index >= 2) hook.cell.styles.halign = hook.column.index === 2 ? 'center' : 'right';
    },
    didDrawPage: () => {
      const pagina = doc.getCurrentPageInfo().pageNumber;
      if (pagina > primeraHoja) encabezadoHoja();
      pie(doc, PIE, `Página ${pagina}`, pageWidth, pageHeight);
    },
  });
}

/** Planes de financiamiento de Nanuu (con enganche del 20 % y sin enganche) sobre el precio cotizado.
 * Devuelve la y donde sigue el resumen. */
function dibujarPlanesNanuu(doc: jsPDF, autoTable: AutoTable, data: QuotePdfData, startY: number): number {
  const pageWidth = doc.internal.pageSize.getWidth();
  const yTitulo = tituloSeccion(doc, 'PLANES DE FINANCIAMIENTO', startY, pageWidth);

  const cabecera = ['Plazo', 'Interés por plazo', 'Interés', 'Total a pagar', 'Mensualidades'];
  const columnas = {
    0: { halign: 'center' as const, cellWidth: 29 },
    1: { halign: 'center' as const, cellWidth: 34 },
    2: { halign: 'right' as const, cellWidth: 34 },
    3: { halign: 'right' as const, cellWidth: 38 },
    4: { halign: 'right' as const },
  };
  const enganche = data.totalPrice * (PORCENTAJE_ENGANCHE_NANUU / 100);

  subtituloTabla(doc, 'CON ENGANCHE', yTitulo + 6);
  autoTable(doc, {
    startY: yTitulo + 8,
    head: [cabecera],
    body: filasPlanesNanuu(data.totalPrice, enganche),
    margin: { left: MARGEN, right: MARGEN },
    ...estiloTabla(7.5),
    columnStyles: columnas,
  });

  const yDespues = finUltimaTabla(doc)! + 9;

  subtituloTabla(doc, 'SIN ENGANCHE', yDespues);
  autoTable(doc, {
    startY: yDespues + 2,
    head: [cabecera],
    body: filasPlanesNanuu(data.totalPrice, 0),
    margin: { left: MARGEN, right: MARGEN },
    ...estiloTabla(7.5),
    columnStyles: columnas,
  });

  return finUltimaTabla(doc)! + 10;
}

/** Una fila por plan: plazo, % de interés, interés, total a pagar y mensualidad (lo que queda tras el enganche). */
function filasPlanesNanuu(precio: number, enganche: number): string[][] {
  return PLANES_NANUU.map((plan) => {
    const interestAmount = precio * (plan.interestPercentage / 100);
    const totalToPay = precio + interestAmount;
    const monthlyPayment = (totalToPay - enganche) / plan.months;
    return [`${plan.months} meses`, `${plan.interestPercentage}%`, dinero(interestAmount), dinero(totalToPay), dinero(monthlyPayment)];
  });
}
