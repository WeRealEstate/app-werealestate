import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import type autoTableTipo from 'jspdf-autotable';
import type { CellHookData } from 'jspdf-autotable';

/** jspdf y jspdf-autotable pesan ~400 KB: se descargan la primera vez que se genera un PDF (cotización o plano), no
 * al abrir el Cotizador o el Plano. Aquí solo se importan sus tipos, que no llegan al navegador; las funciones de
 * tabla de abajo usan `autoTable` cuando cargarJsPdf ya lo dejó listo (siempre se llama antes de armar el PDF). */
let jspdf: typeof import('jspdf') | null = null;
let autoTable: typeof autoTableTipo;

async function cargarJsPdf(): Promise<typeof import('jspdf')> {
  if (!jspdf) {
    const [libreria, tablas] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    autoTable = tablas.default;
    jspdf = libreria;
  }
  return jspdf;
}

export interface QuotePdfData {
  project: string;
  location: string;

  advisorName: string;
  clientName: string;
  blockNumber: string;
  lotNumber: string;

  /** Lotes que componen la cotización: 1 en el caso de siempre, más de 1 cuando se combinan varios
   * lotes juntos. area/pricePerM2 de arriba ya son el total/promedio de esta lista. */
  lotes: { manzana: string; lote: string; superficie: number; pricePerM2: number }[];

  area: number;
  pricePerM2: number;
  totalPrice: number;
  paymentMethod: string;
  /** true solo si el contado lleva precio especial con descuento (lote estándar de SAMAI); si no, es precio de lista. */
  descuentoContado?: boolean;
  downPaymentLabel: string;
  downPayment: number;
  /** Anualidades o aportaciones de la forma de pago (pueden ir junto con el enganche o el pago inicial):
   * un renglón aparte en el detalle. Sin entrada, el renglón del enganche en $0 no se muestra. */
  pagoExtra?: { label: string; monto: number };
  financedAmount: number;
  months: number;
  monthlyPayment: number;
  date: string;

  interestPercentage: number;
  interestAmount: number;
  totalInvestment: number;

  amortizationTable: {
    paymentNumber: number;
    month: string;
    year: number;
    payment: number;
    balance: number;
    accumulatedPayment: number;
    /** true en los meses que llevan una aportación: se resaltan en la tabla. */
    esAportacion?: boolean;
    }[];

  /** Aportaciones elegidas (esquema "Con aportaciones" o Promoción por mes): se listan aparte al
   * final de la tabla de amortización. */
  aportaciones?: { mes: string; anio: number; monto: number }[];
}

export interface PlanoPdfData {
  desarrolloNombre: string;
  fecha: string;
  /** Imagen del plano en JPEG, tal como está en el servidor: se incrusta en el PDF sin volver a
   * comprimirla ni reducirla, para que conserve todo su detalle al acercar. */
  imagenBytes: Uint8Array;
  anchoImagen: number;
  altoImagen: number;
  /** Lotes ya delimitados: se dibujan como figuras vectoriales sobre la imagen (nítidas con cualquier
   * zoom). Los vértices van en % (0-100) del ancho/alto de la imagen. */
  poligonos: { puntos: { x: number; y: number }[]; color: [number, number, number] }[];
  leyenda: { label: string; color: [number, number, number] }[];
}

/** Contorno de cada lote en el PDF del plano: fino (≈0.3 pt) para que siga la línea del propio plano
 * en vez de taparla, y relleno ligero para que se lean los números y medidas de cada lote. */
const GROSOR_CONTORNO_LOTE_PDF_MM = 0.1;
const OPACIDAD_RELLENO_LOTE_PDF = 0.25;

@Injectable({
  providedIn: 'root'
})
export class PdfService {

  

  // ==============================
  // CONFIGURACIÓN GENERAL
  // ==============================

  private readonly FONT = 'Montserrat';

  /** Único color del PDF: la línea delgada del encabezado. Todo lo demás va en negro y gris. */
  private readonly WE_BLUE: [number, number, number] =
    [36, 63, 184];

  private readonly TEXT: [number, number, number] =
    [26, 26, 26];

  private readonly MUTED: [number, number, number] =
    [115, 115, 115];

  private readonly BORDER: [number, number, number] =
    [200, 200, 200];

  /** Línea fina entre renglones de listas y tablas. */
  private readonly LINEA_FILA: [number, number, number] =
    [225, 225, 225];


  // ==============================
  // GENERAR PDF
  // ==============================

  private async buildQuotePdf(data: QuotePdfData): Promise<jsPDF> {
    const { jsPDF } = await cargarJsPdf();

    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'letter'
    });

    // Cargamos Montserrat
    await this.loadFonts(doc);

    const pageWidth =
      doc.internal.pageSize.getWidth();

    const pageHeight =
      doc.internal.pageSize.getHeight();

    let y = 0;
    const MARGEN = 20;
    const ancho = pageWidth - 2 * MARGEN;

    // ==============================
    // ENCABEZADO (sin banda: título azul, fecha y vigencia, línea azul)
    // ==============================

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(15);
    doc.text('PROPUESTA DE INVERSIÓN', MARGEN, 18);

    doc.setFontSize(7);
    doc.text(`Fecha: ${data.date}`, pageWidth - MARGEN, 17, { align: 'right' });

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);
    doc.text('Vigencia de la cotización: 20 días', pageWidth - MARGEN, 21.5, { align: 'right' });

    doc.setDrawColor(...this.WE_BLUE);
    doc.setLineWidth(0.5);
    doc.line(MARGEN, 24, pageWidth - MARGEN, 24);

    // NOMBRE DEL PROYECTO Y UBICACIÓN
    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(12);
    doc.text(data.project, MARGEN, 36);

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(8);
    doc.text(data.location, MARGEN, 41.5);

    // ==============================
    // DATOS DE LA COTIZACIÓN
    // ==============================

    y = this.tituloSeccion(doc, 'DATOS DE LA COTIZACIÓN', 55, pageWidth) + 4.5;

    const campos: [string, string, number, number][] = [
      ['Nombre del asesor', data.advisorName || 'No especificado', MARGEN, 0],
      ['Nombre del cliente', data.clientName || 'No especificado', 105, 0],
      ['Manzana', data.blockNumber || 'No especificado', MARGEN, 11],
      ['Lote', data.lotNumber || 'No especificado', 105, 11],
    ];
    for (const [etiqueta, valor, x, dy] of campos) {
      doc.setTextColor(...this.MUTED);
      doc.setFont(this.FONT, 'normal');
      doc.setFontSize(6.5);
      doc.text(etiqueta, x, y + dy);
      doc.setTextColor(...this.TEXT);
      doc.setFont(this.FONT, 'bold');
      doc.setFontSize(8.5);
      doc.text(valor, x, y + dy + 4.2);
    }
    y += 22 + 10;

    // ==============================
    // DETALLE DE LA INVERSIÓN
    // ==============================

    y = this.tituloSeccion(doc, 'DETALLE DE LA INVERSIÓN', y, pageWidth);

    // Con un solo lote, una fila de Superficie y una de Precio por m² (como siempre). Con varios
    // lotes juntos, una fila por lote (cada uno con su propia superficie y precio) en vez de un
    // total/promedio que escondería el detalle que el cliente necesita ver.
    const filasLotes: [string, string][] =
      data.lotes.length > 1
        ? data.lotes.map((lote) => [
            `Lote ${lote.manzana || '—'}-${lote.lote || '—'}`,
            `${this.number(lote.superficie)} m² · ${this.money(lote.pricePerM2)}/m²`,
          ])
        : [
            ['Superficie', `${this.number(data.area)} m²`],
            [
              data.descuentoContado ?? data.paymentMethod === 'Contado'
                ? 'Modalidad de precio'
                : 'Precio por m²',
              data.descuentoContado ?? data.paymentMethod === 'Contado'
                ? 'Precio especial de contado'
                : this.money(data.pricePerM2)
            ],
          ];

    const rows: [string, string][] = [
      ['Proyecto', data.project],
      ...filasLotes,
      ['Monto de inversión', this.money(data.totalPrice)],
      ['Forma de pago', data.paymentMethod],
      ...(data.downPayment > 0 || !data.pagoExtra
        ? [[data.downPaymentLabel, this.money(data.downPayment)] as [string, string]]
        : []),
      ...(data.pagoExtra
        ? [[data.pagoExtra.label, this.money(data.pagoExtra.monto)] as [string, string]]
        : []),
      ['Saldo', this.money(data.financedAmount)],
      ['Intereses', this.money(data.interestAmount)],
    ];

    // 9 mm por renglón con los 8 de siempre; con más (anualidades o aportaciones junto al enganche, varios
    // lotes) bajan a 8 mm para que el aviso de abajo no se encime con el pie de página.
    const rowHeight = rows.length > 8 ? 8 : 9;
    const rowTextY = rowHeight - 3;

    for (const [label, value] of rows) {
      doc.setFont(this.FONT, 'normal');
      doc.setFontSize(8.5);
      doc.setTextColor(...this.MUTED);
      doc.text(label, MARGEN, y + rowTextY);

      doc.setFont(this.FONT, 'bold');
      doc.setTextColor(...this.TEXT);
      doc.text(value, pageWidth - MARGEN, y + rowTextY, { align: 'right' });

      this.linea(doc, MARGEN, y + rowHeight, pageWidth - MARGEN, y + rowHeight, 0.15, this.LINEA_FILA);
      y += rowHeight;
    }

    // ==============================
    // MENSUALIDAD (entre dos líneas azules, sin caja)
    // ==============================

    y += 7;
    this.linea(doc, MARGEN, y, pageWidth - MARGEN, y, 0.25, this.MUTED);
    this.linea(doc, MARGEN, y + 15, pageWidth - MARGEN, y + 15, 0.25, this.MUTED);

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(6.5);
    doc.text('Mensualidad estimada', MARGEN, y + 5.5);

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8.5);
    doc.text(this.money(data.monthlyPayment), MARGEN, y + 10.5);

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.text(`${data.months} meses`, pageWidth - MARGEN, y + 10.5, { align: 'right' });

    // ==============================
    // TOTAL DE INVERSIÓN (sin caja)
    // ==============================

    y += 15 + 8;
    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8.5);
    doc.text('Total de inversión', MARGEN, y);
    doc.text(this.money(data.totalInvestment), pageWidth - MARGEN, y, { align: 'right' });

    // ==============================
    // INFORMACIÓN IMPORTANTE
    // ==============================

    const notice =
      'Esta cotización tiene una vigencia de 20 días naturales a partir de su fecha de emisión y está sujeta a disponibilidad, condiciones comerciales vigentes y validación por parte de WE Real Estate. No constituye contrato, apartado o promesa de compraventa.';

    // Medido con la letra con que se escribe (7 pt) para saber cuántos renglones ocupa.
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);
    const noticeLines = doc.splitTextToSize(notice, ancho);

    y = this.tituloSeccion(doc, 'INFORMACIÓN IMPORTANTE', y + 10, pageWidth) + 5;

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);
    doc.text(noticeLines, MARGEN, y, { lineHeightFactor: 1.5 });

    // ==============================
    // FOOTER
    // ==============================

    this.pie(doc, 'WE Real Estate · Cotización informativa', 'Página 1', pageWidth, pageHeight);

    // ==============================
    // TABLA DE AMORTIZACIÓN
    // ==============================

    if (
    data.paymentMethod !== 'cash' &&
    data.amortizationTable.length > 0
    ) {

    doc.addPage('letter', 'portrait');

    const pageWidth =
        doc.internal.pageSize.getWidth();

    const pageHeight =
        doc.internal.pageSize.getHeight();

    // Hoja donde empieza la tabla: las que siguen repiten el encabezado.
    const primeraHoja = doc.getNumberOfPages();
    const encabezadoHoja = (primera: boolean) => this.encabezado(doc, 'TABLA DE AMORTIZACIÓN', data.project.toUpperCase(), pageWidth, primera);
    encabezadoHoja(true);

    // RESUMEN SUPERIOR
    let yTable = 34;

    if (data.project === 'Aldea Nanuu') {
      yTable =
        this.addNanuuFinancingTable(
          doc,
          data,
          yTable
        );
    }

    yTable = this.tituloSeccion(doc, 'RESUMEN DEL FINANCIAMIENTO', yTable, pageWidth) + 4.5;

    const resumen: [string, string, boolean][] = [
      ['Monto financiado', this.money(data.financedAmount), false],
      ['Plazo', `${data.months} meses`, false],
      ['Mensualidad', this.money(data.monthlyPayment), true],
      ['Intereses', this.money(data.interestAmount), false],
    ];
    resumen.forEach(([etiqueta, valor, azul], i) => {
      const x = 20 + i * ((pageWidth - 40) / 4);
      doc.setTextColor(...this.MUTED);
      doc.setFont(this.FONT, 'normal');
      doc.setFontSize(6.5);
      doc.text(etiqueta, x, yTable);
      doc.setTextColor(...this.TEXT);
      doc.setFont(this.FONT, 'bold');
      doc.setFontSize(8.5);
      doc.text(valor, x, yTable + 4.2);
    });

    // ==============================
    // AUTOTABLE
    // ==============================

    const tableBody =
        data.amortizationTable.map(
        row => [
            row.paymentNumber,
            row.month,
            row.year,
            this.money(row.payment),
            this.money(row.balance),
            this.money(row.accumulatedPayment)
        ]
        );

    autoTable(doc, {
        startY: yTable + 14,
        head: [[
        '# PAGO',
        'MES',
        'AÑO',
        'PAGO',
        'SALDO',
        'ABONO ACUMULADO'
        ]],
        body: tableBody,
        margin: {
        left: 20,
        right: 20,
        top: 26,
        bottom: 18
        },
        ...this.estiloTabla(7.5),
        columnStyles: {
        0: {
            halign: 'center',
            cellWidth: 17
        },
        1: {
            halign: 'left',
            cellWidth: 33
        },
        2: {
            halign: 'center',
            cellWidth: 20
        },
        3: {
            halign: 'right',
            cellWidth: 34
        },
        4: {
            halign: 'right',
            cellWidth: 34
        },
        5: {
            halign: 'right'
        }
        },
        // Los meses que llevan una aportación se resaltan en azul y negritas (sin relleno).
        didParseCell: (hook) => {
          this.alinearEncabezado(hook);
          if (
            hook.section === 'body' &&
            data.amortizationTable[hook.row.index]?.esAportacion
          ) {
            hook.cell.styles.fontStyle = 'bold';
          }
        },
        didDrawPage: () => {
        const currentPage =
            doc.getCurrentPageInfo()
            .pageNumber;

        if (currentPage > primeraHoja) encabezadoHoja(false);

        // FOOTER
        this.pie(doc, 'WE Real Estate · Tabla de amortización', `Página ${currentPage}`, pageWidth, pageHeight);
        }
    });

    if (data.aportaciones && data.aportaciones.length > 0) {
      this.addResumenAportaciones(doc, data, data.aportaciones, pageWidth, pageHeight);
    }

    }

  // ==============================
    // DESCARGAR
    // ==============================

    return doc;

  }

  /** Lista de las aportaciones (mes, año y monto) debajo de la tabla de amortización, con su total.
   * Si no cabe en la página donde terminó la tabla, pasa a una nueva. */
  private addResumenAportaciones(
    doc: jsPDF,
    data: QuotePdfData,
    aportaciones: { mes: string; anio: number; monto: number }[],
    pageWidth: number,
    pageHeight: number
  ): void {
    const ultimaTabla = (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable;
    let y = (ultimaTabla?.finalY ?? 40) + 12;
    const encabezadoHoja = () => this.encabezado(doc, 'TABLA DE AMORTIZACIÓN', data.project.toUpperCase(), pageWidth, false);

    // Título + encabezado + un par de filas deben caber; si no, página nueva.
    if (y > pageHeight - 50) {
      doc.addPage('letter', 'portrait');
      encabezadoHoja();
      y = 30;
    }
    const primeraHoja = doc.getNumberOfPages();

    y = this.tituloSeccion(doc, 'APORTACIONES PROGRAMADAS', y, pageWidth);

    const total = aportaciones.reduce((suma, a) => suma + a.monto, 0);

    autoTable(doc, {
      startY: y + 3,
      head: [['#', 'MES', 'AÑO', 'APORTACIÓN']],
      body: aportaciones.map((a, i) => [i + 1, a.mes, a.anio, this.money(a.monto)]),
      foot: [['', '', 'TOTAL', this.money(total)]],
      margin: { left: 20, right: 20, top: 26, bottom: 18 },
      ...this.estiloTabla(7.5),
      columnStyles: {
        0: { halign: 'center', cellWidth: 17 },
        1: { halign: 'left' },
        2: { halign: 'center', cellWidth: 25 },
        3: { halign: 'right', cellWidth: 45 },
      },
      didParseCell: (hook) => {
        this.alinearEncabezado(hook);
        if (hook.section === 'foot' && hook.column.index >= 2) hook.cell.styles.halign = hook.column.index === 2 ? 'center' : 'right';
      },
      didDrawPage: () => {
        const pagina = doc.getCurrentPageInfo().pageNumber;
        if (pagina > primeraHoja) encabezadoHoja();
        this.pie(doc, 'WE Real Estate · Tabla de amortización', `Página ${pagina}`, pageWidth, pageHeight);
      },
    });
  }

  // ==============================
  // ESTILO COMÚN (sin cajas ni rellenos)
  // ==============================

  private linea(doc: jsPDF, x1: number, y1: number, x2: number, y2: number, grosor: number, color: [number, number, number]): void {
    doc.setDrawColor(...color);
    doc.setLineWidth(grosor);
    doc.line(x1, y1, x2, y2);
  }

  /** Encabezado de las hojas de amortización: la primera lleva el título; las que siguen solo la línea azul con el
   * dato gris, sin repetir el título. */
  private encabezado(doc: jsPDF, titulo: string, derecha: string, pageWidth: number, primera: boolean): void {
    if (primera) {
      doc.setTextColor(...this.TEXT);
      doc.setFont(this.FONT, 'bold');
      doc.setFontSize(15);
      doc.text(titulo, 20, 18);
    }
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);
    doc.text(derecha, pageWidth - 20, primera ? 18 : 14.5, { align: 'right' });

    const yLinea = primera ? 22 : 18;
    this.linea(doc, 20, yLinea, pageWidth - 20, yLinea, 0.5, this.WE_BLUE);
  }

  /** Título de sección en mayúsculas chicas con una línea gris fina debajo. Devuelve la y de la línea. */
  private tituloSeccion(doc: jsPDF, titulo: string, y: number, pageWidth: number): number {
    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(7.5);
    doc.text(titulo, 20, y, { charSpace: 0.5 });
    this.linea(doc, 20, y + 1.6, pageWidth - 20, y + 1.6, 0.25, this.BORDER);
    return y + 1.6;
  }

  private pie(doc: jsPDF, izquierda: string, derecha: string, pageWidth: number, pageHeight: number): void {
    this.linea(doc, 20, pageHeight - 12, pageWidth - 20, pageHeight - 12, 0.15, this.BORDER);
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(6.5);
    doc.text(izquierda, 20, pageHeight - 7);
    doc.text(derecha, pageWidth - 20, pageHeight - 7, { align: 'right' });
  }

  /** Tabla solo con líneas horizontales: encabezado en texto azul con línea azul, renglones con línea gris
   * fina, sin rellenos. El que llama pone columnas, márgenes y datos. */
  private estiloTabla(fontSize: number) {
    return {
      theme: 'plain' as const,
      styles: {
        font: this.FONT,
        fontSize,
        cellPadding: { top: 2.3, bottom: 2.3, left: 1.5, right: 1.5 },
        textColor: this.TEXT,
        lineWidth: 0,
        valign: 'middle' as const,
      },
      headStyles: { font: this.FONT, fontStyle: 'bold' as const, fillColor: false as const, textColor: this.TEXT, fontSize: 6.5 },
      footStyles: { font: this.FONT, fontStyle: 'bold' as const, fillColor: false as const, textColor: this.TEXT },
      // El encabezado se alinea como su columna (autoTable no lo hereda del columnStyles).
      didParseCell: (hook: CellHookData) => this.alinearEncabezado(hook),
      didDrawCell: (hook: CellHookData) => {
        const { x, y, width, height } = hook.cell;
        if (hook.section === 'foot') this.linea(hook.doc, x, y, x + width, y, 0.4, this.TEXT);
        else if (hook.section === 'head') this.linea(hook.doc, x, y + height, x + width, y + height, 0.4, this.MUTED);
        else this.linea(hook.doc, x, y + height, x + width, y + height, 0.15, this.LINEA_FILA);
      },
    };
  }

  private alinearEncabezado(hook: CellHookData): void {
    if (hook.section !== 'head') return;
    const columnas = hook.table.styles.columnStyles as Record<number, { halign?: 'left' | 'center' | 'right' }>;
    hook.cell.styles.halign = columnas?.[hook.column.index]?.halign ?? 'left';
  }

  /** Subtítulo de una tabla (p. ej. «CON ENGANCHE»): texto azul chico, sin barra de color. */
  private subtituloTabla(doc: jsPDF, texto: string, y: number): void {
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(7.5);
    doc.text(texto, 20, y, { charSpace: 0.5 });
  }

  private addNanuuFinancingTable(
  doc: jsPDF,
  data: QuotePdfData,
  startY: number
): number {

  if (data.project !== 'Aldea Nanuu') {
    return startY;
  }

  const financingPlans = [
    {
      months: 24,
      interestPercentage: 0
    },
    {
      months: 36,
      interestPercentage: 5
    },
    {
      months: 48,
      interestPercentage: 10
    },
    {
      months: 60,
      interestPercentage: 20
    }
  ];

  // ==============================
  // CON ENGANCHE - 20%
  // ==============================

  const downPaymentPercentage = 20;

  const downPayment =
    data.totalPrice *
    (downPaymentPercentage / 100);

  const withDownPaymentBody =
    financingPlans.map(plan => {

      const interestAmount =
        data.totalPrice *
        (plan.interestPercentage / 100);

      const totalToPay =
        data.totalPrice +
        interestAmount;

      const financedAmount =
        totalToPay -
        downPayment;

      const monthlyPayment =
        financedAmount /
        plan.months;

      return [
        `${plan.months} meses`,
        `${plan.interestPercentage}%`,
        this.money(interestAmount),
        this.money(totalToPay),
        this.money(monthlyPayment)
      ];

    });


  // ==============================
  // SIN ENGANCHE
  // ==============================

  const withoutDownPaymentBody =
    financingPlans.map(plan => {

      const interestAmount =
        data.totalPrice *
        (plan.interestPercentage / 100);

      const totalToPay =
        data.totalPrice +
        interestAmount;

      const monthlyPayment =
        totalToPay /
        plan.months;

      return [
        `${plan.months} meses`,
        `${plan.interestPercentage}%`,
        this.money(interestAmount),
        this.money(totalToPay),
        this.money(monthlyPayment)
      ];

    });


  // ==============================
  // TÍTULO GENERAL
  // ==============================

  const pageWidth = doc.internal.pageSize.getWidth();
  const yTitulo = this.tituloSeccion(doc, 'PLANES DE FINANCIAMIENTO', startY, pageWidth);

  const cabecera = ['Plazo', 'Interés por plazo', 'Interés', 'Total a pagar', 'Mensualidades'];
  const columnas = {
    0: { halign: 'center' as const, cellWidth: 29 },
    1: { halign: 'center' as const, cellWidth: 34 },
    2: { halign: 'right' as const, cellWidth: 34 },
    3: { halign: 'right' as const, cellWidth: 38 },
    4: { halign: 'right' as const },
  };

  // ==============================
  // TABLA CON ENGANCHE
  // ==============================

  this.subtituloTabla(doc, 'CON ENGANCHE', yTitulo + 6);

  autoTable(doc, {
    startY: yTitulo + 8,
    head: [cabecera],
    body: withDownPaymentBody,
    margin: { left: 20, right: 20 },
    ...this.estiloTabla(7.5),
    columnStyles: columnas,
  });

  const yDespues = (doc as any).lastAutoTable.finalY + 9;

  // ==============================
  // TABLA SIN ENGANCHE
  // ==============================

  this.subtituloTabla(doc, 'SIN ENGANCHE', yDespues);

  autoTable(doc, {
    startY: yDespues + 2,
    head: [cabecera],
    body: withoutDownPaymentBody,
    margin: { left: 20, right: 20 },
    ...this.estiloTabla(7.5),
    columnStyles: columnas,
  });

  return (
    (doc as any).lastAutoTable.finalY +
    10
  );
}

  // ==============================
  // CARGAR MONTSERRAT
  // ==============================

  private async loadFonts(
    doc: jsPDF
  ): Promise<void> {

    const regular =
      await this.fontToBase64(
        '/fonts/Montserrat-Regular.ttf'
      );

    const bold =
      await this.fontToBase64(
        '/fonts/Montserrat-Bold.ttf'
      );


    doc.addFileToVFS(
      'Montserrat-Regular.ttf',
      regular
    );

    doc.addFont(
      'Montserrat-Regular.ttf',
      this.FONT,
      'normal'
    );


    doc.addFileToVFS(
      'Montserrat-Bold.ttf',
      bold
    );

    doc.addFont(
      'Montserrat-Bold.ttf',
      this.FONT,
      'bold'
    );


    // Fuente predeterminada del documento
    doc.setFont(
      this.FONT,
      'normal'
    );

  }


  // ==============================
  // CONVERTIR FUENTE A BASE64
  // ==============================

  private async fontToBase64(
    url: string
  ): Promise<string> {

    const response =
      await fetch(url);

    if (!response.ok) {
      throw new Error(
        `No se pudo cargar la fuente: ${url}`
      );
    }

    const buffer =
      await response.arrayBuffer();

    const bytes =
      new Uint8Array(buffer);

    let binary = '';

    for (
      let i = 0;
      i < bytes.length;
      i++
    ) {
      binary += String.fromCharCode(
        bytes[i]
      );
    }

    return btoa(binary);

  }


  // ==============================
  // FORMATEADORES
  // ==============================

  private number(
    value: number
  ): string {

    return value.toLocaleString(
      'en-US',
      {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
      }
    );

  }


  private money(
    value: number
  ): string {

    return `$${this.number(value)} MXN`;

  }

  async downloadQuotePdf(data: QuotePdfData): Promise<void> {

    const doc = await this.buildQuotePdf(data);

    const fileName = this.getFileName(data);

    doc.save(fileName);
  }


  async createQuoteFile(data: QuotePdfData): Promise<File> {

    const doc = await this.buildQuotePdf(data);

    const blob = doc.output('blob');

    const fileName = this.getFileName(data);

    return new File(
      [blob],
      fileName,
      {
        type: 'application/pdf'
      }
    );
  }

  private getFileName(data: QuotePdfData): string {

    const project =
      data.clientName
        .replace(/\s+/g, '-')
        .toUpperCase();

    return `Cotizacion-${project}.pdf`;
  }

  // ==============================
  // PDF DEL PLANO INTERACTIVO
  // ==============================

  /** Hoja a la medida del plano: del ancho de una A3 y con el alto que pide la proporción de la imagen,
   * así el plano llena la hoja de borde a borde, sin márgenes ni franjas vacías. Solo hay una franja de
   * marca con el título arriba y otra con la leyenda debajo.
   * Los tamaños de texto y márgenes se escalan (`k`) respecto de la hoja carta con la que se diseñó. */
  private async buildPlanoPdf(data: PlanoPdfData): Promise<jsPDF> {
    const { jsPDF, GState } = await cargarJsPdf();
    const pageWidth = 420; // ancho de una A3 horizontal
    const k = pageWidth / 279.4; // 279.4 mm = ancho de la hoja carta horizontal
    const margen = 4 * k; // solo separa los textos de las franjas del borde de la hoja
    const headerHeight = 10 * k;
    const legendHeight = 8.5 * k;
    const alturaMaxima = 594; // lado largo de una A2: tope para planos muy altos

    // La imagen toma todo el ancho de la hoja; el alto sale de su proporción.
    const aspecto = data.anchoImagen / data.altoImagen;
    let drawWidth = pageWidth;
    let drawHeight = drawWidth / aspecto;
    let pageHeight = headerHeight + drawHeight + legendHeight;
    if (pageHeight > alturaMaxima) {
      pageHeight = alturaMaxima;
      drawHeight = pageHeight - headerHeight - legendHeight;
      drawWidth = drawHeight * aspecto;
    }
    const drawX = (pageWidth - drawWidth) / 2;
    const drawY = headerHeight;

    // jsPDF intercambia ancho y alto si la orientación no coincide con la forma de la hoja: un plano
    // casi cuadrado o vertical daría una hoja más alta que ancha y saldría cortado. Se elige la que toca.
    const doc = new jsPDF({
      orientation: pageWidth >= pageHeight ? 'landscape' : 'portrait',
      unit: 'mm',
      format: [pageWidth, pageHeight],
    });
    await this.loadFonts(doc);

    // ENCABEZADO sobre fondo blanco: título azul, fecha gris a la derecha y línea azul bajo la franja
    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(11 * k);
    doc.text(`Plano interactivo · ${data.desarrolloNombre}`, margen, headerHeight / 2 + 1.5 * k);

    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7 * k);
    doc.text(`Generado el ${data.fecha}`, pageWidth - margen, headerHeight / 2 + 1.5 * k, { align: 'right' });

    this.linea(doc, 0, headerHeight - 0.15, pageWidth, headerHeight - 0.15, 0.5, this.WE_BLUE);

    // IMAGEN DEL PLANO, de borde a borde. El JPEG original se incrusta tal cual ('NONE': sin
    // recomprimir), no repintado en un canvas.
    doc.addImage(data.imagenBytes, 'JPEG', drawX, drawY, drawWidth, drawHeight, undefined, 'NONE');

    // POLÍGONOS DE LOS LOTES (vectoriales). Contorno fino del color del estado, para seguir la línea
    // del propio plano sin taparla; relleno ligero, para que se lean los números y medidas de cada lote.
    const rellenoLigero = new GState({ opacity: OPACIDAD_RELLENO_LOTE_PDF });
    const opaco = new GState({ opacity: 1 });
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

    this.linea(doc, 0, drawY + drawHeight + 0.15, pageWidth, drawY + drawHeight + 0.15, 0.15, this.BORDER);

    // LEYENDA (puntos de color y etiquetas) y nota al pie, en una sola franja bajo el plano
    const legendY = drawY + drawHeight + legendHeight / 2 + 0.8 * k;
    let legendX = margen;
    const radioPunto = 1.5 * k;

    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7 * k);
    for (const item of data.leyenda) {
      doc.setFillColor(...item.color);
      doc.circle(legendX + radioPunto, legendY - 0.9 * k, radioPunto, 'F');

      doc.setTextColor(...this.TEXT);
      doc.text(item.label, legendX + radioPunto * 2 + 1.6 * k, legendY);

      legendX += radioPunto * 2 + 1.6 * k + doc.getTextWidth(item.label) + 8 * k;
    }

    doc.setTextColor(...this.MUTED);
    doc.setFontSize(6 * k);
    doc.text('WE Real Estate · Plano informativo, sujeto a cambios', pageWidth - margen, legendY, { align: 'right' });

    return doc;
  }

  async downloadPlanoPdf(data: PlanoPdfData): Promise<void> {
    const doc = await this.buildPlanoPdf(data);
    const nombre = data.desarrolloNombre.replace(/\s+/g, '-');
    doc.save(`Plano-${nombre}.pdf`);
  }

}