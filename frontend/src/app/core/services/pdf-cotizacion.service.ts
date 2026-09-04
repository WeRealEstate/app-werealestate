import { Injectable } from '@angular/core';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';

export interface QuotePdfData {
  project: string;
  location: string;

  advisorName: string;
  clientName: string;
  blockNumber: string;
  lotNumber: string;

  area: number;
  pricePerM2: number;
  totalPrice: number;
  paymentMethod: string;
  downPayment: number;
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
    }[];
}

@Injectable({
  providedIn: 'root'
})
export class PdfService {

  

  // ==============================
  // CONFIGURACIÓN GENERAL
  // ==============================

  private readonly FONT = 'Montserrat';

  private readonly WE_BLUE: [number, number, number] =
    [36, 63, 184];

  private readonly WE_DARK: [number, number, number] =
    [11, 47, 104];

  private readonly WE_LIGHT: [number, number, number] =
    [238, 243, 255];

  private readonly TEXT: [number, number, number] =
    [23, 35, 60];

  private readonly MUTED: [number, number, number] =
    [102, 112, 133];

  private readonly BORDER: [number, number, number] =
    [217, 224, 234];


  // ==============================
  // GENERAR PDF
  // ==============================

  private async buildQuotePdf(data: QuotePdfData): Promise<jsPDF> {

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


    // ==============================
    // HEADER
    // ==============================

    doc.setFillColor(...this.WE_DARK);

    doc.rect(
      0,
      0,
      pageWidth,
      25,
      'F'
    );

    // ==============================
    // PROPUESTA
    // ==============================

    y = 35;

    doc.setTextColor(
      ...this.WE_BLUE
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(9);

    doc.text(
      'PROPUESTA DE INVERSIÓN',
      18,
      y
    );


    // NOMBRE DEL PROYECTO

    doc.setTextColor(
      ...this.TEXT
    );

    doc.setFontSize(18);

    doc.text(
      data.project,
      18,
      y + 10
    );


    // UBICACIÓN

    doc.setTextColor(
      ...this.MUTED
    );

    doc.setFont(
      this.FONT,
      'normal'
    );

    doc.setFontSize(8);

    doc.text(
      data.location,
      18,
      y + 16
    );


    // FECHA

    doc.setTextColor(
      ...this.TEXT
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(8);

    doc.text(
      `Fecha: ${data.date}`,
      pageWidth - 18,
      y + 4,
      {
        align: 'right'
      }
    );


    // VIGENCIA

    doc.setTextColor(
      ...this.MUTED
    );

    doc.setFont(
      this.FONT,
      'normal'
    );

    doc.setFontSize(7);

    doc.text(
      'Vigencia de la cotización: 20 días',
      pageWidth - 18,
      y + 10,
      {
        align: 'right'
      }
    );

    // ==============================
    // DATOS DE LA COTIZACIÓN
    // ==============================

    y += 28;

    doc.setTextColor(...this.WE_DARK);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(10);

    doc.text(
    'DATOS DE LA COTIZACIÓN',
    18,
    y
    );

    y += 4;

    doc.setFillColor(247, 249, 252);
    doc.setDrawColor(...this.BORDER);

    doc.roundedRect(
    18,
    y,
    pageWidth - 36,
    30,
    3,
    3,
    'FD'
    );

    // ASESOR
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
    'Nombre del asesor',
    23,
    y + 7
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8);

    doc.text(
    data.advisorName || 'No especificado',
    23,
    y + 13
    );

    // CLIENTE
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
    'Nombre del cliente',
    112,
    y + 7
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8);

    doc.text(
    data.clientName || 'No especificado',
    112,
    y + 13
    );

    // MANZANA
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
    'Manzana',
    23,
    y + 20
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8);

    doc.text(
    data.blockNumber || 'No especificado',
    23,
    y + 26
    );

    // LOTE
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
    'Lote',
    112,
    y + 20
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(8);

    doc.text(
    data.lotNumber || 'No especificado',
    112,
    y + 26
    );

    // dejamos espacio antes del siguiente bloque
    y += 10;


    // ==============================
    // DETALLE DE INVERSIÓN
    // ==============================

    y += 28;

    doc.setTextColor(
      ...this.WE_DARK
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(10);

    doc.text(
      'DETALLE DE LA INVERSIÓN',
      18,
      y
    );

    y += 5;


    const rows: [string, string][] = [

      [
        'Proyecto',
        data.project
      ],

      [
        'Superficie',
        `${this.number(data.area)} m²`
      ],

      [
        data.paymentMethod === 'Contado'
          ? 'Modalidad de precio'
          : 'Precio por m²',

        data.paymentMethod === 'Contado'
          ? 'Precio especial de contado'
          : this.money(data.pricePerM2)
      ],

      [
        'Precio total del terreno',
        this.money(data.totalPrice)
      ],

      [
        'Forma de pago',
        data.paymentMethod
      ],

      [
        'Enganche',
        this.money(data.downPayment)
      ],

      [
        'Saldo',
        this.money(data.financedAmount)
      ],
      [
        'Intereses',
        this.money(data.interestAmount)
      ],

    ];


    const rowHeight = 9;


    rows.forEach(
      ([label, value], index) => {

        if (index === 0) {

          doc.setFillColor(
            ...this.WE_LIGHT
          );

        } else {

          doc.setFillColor(
            255,
            255,
            255
          );

        }


        doc.setDrawColor(
          ...this.BORDER
        );


        doc.rect(
          18,
          y,
          pageWidth - 36,
          rowHeight,
          'FD'
        );


        // Label

        doc.setTextColor(
          ...this.MUTED
        );

        doc.setFont(
          this.FONT,
          'normal'
        );

        doc.setFontSize(8);

        doc.text(
          label,
          22,
          y + 6
        );


        // Valor

        doc.setTextColor(
          ...this.TEXT
        );

        doc.setFont(
          this.FONT,
          'bold'
        );

        doc.text(
          value,
          pageWidth - 22,
          y + 6,
          {
            align: 'right'
          }
        );


        y += rowHeight;

      }
    );


    // ==============================
    // MENSUALIDAD
    // ==============================

    y += 8;


    doc.setFillColor(
      ...this.WE_LIGHT
    );

    doc.setDrawColor(
      205,
      218,
      255
    );


    doc.roundedRect(
      18,
      y,
      pageWidth - 36,
      30,
      4,
      4,
      'FD'
    );


    // Título

    doc.setTextColor(
      ...this.TEXT
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(8);

    doc.text(
      'MENSUALIDAD ESTIMADA',
      24,
      y + 9
    );


    // Cantidad

    doc.setTextColor(
      ...this.WE_BLUE
    );

    doc.setFontSize(20);

    doc.text(
      this.money(
        data.monthlyPayment
      ),
      24,
      y + 20
    );


    // Meses

    doc.setTextColor(
      ...this.MUTED
    );

    doc.setFont(
      this.FONT,
      'normal'
    );

    doc.setFontSize(8);

    doc.text(
      `${data.months} meses`,
      pageWidth - 24,
      y + 18,
      {
        align: 'right'
      }
    );


    // ==============================
    // TOTAL DE INVERSIÓN
    // ==============================

    y += 35;


    doc.setFillColor(
      247,
      249,
      252
    );


    doc.roundedRect(
      18,
      y,
      pageWidth - 36,
      16,
      3,
      3,
      'F'
    );


    doc.setTextColor(
      ...this.TEXT
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(9);


    doc.text(
      'TOTAL DE INVERSIÓN',
      23,
      y + 10
    );


    doc.setTextColor(
      ...this.WE_BLUE
    );

    doc.setFontSize(14);


    doc.text(
      this.money(
        data.totalInvestment
      ),
      pageWidth - 23,
      y + 10,
      {
        align: 'right'
      }
    );


    // ==============================
    // INFORMACIÓN IMPORTANTE
    // ==============================

    y += 27;


    doc.setTextColor(
      ...this.WE_DARK
    );

    doc.setFont(
      this.FONT,
      'bold'
    );

    doc.setFontSize(8);


    doc.text(
      'INFORMACIÓN IMPORTANTE',
      18,
      y
    );


    doc.setTextColor(
      ...this.MUTED
    );

    doc.setFont(
      this.FONT,
      'normal'
    );

    doc.setFontSize(7);


    const notice =
      'Esta cotización tiene una vigencia de 20 días naturales a partir de su fecha de emisión y está sujeta a disponibilidad, condiciones comerciales vigentes y validación por parte de WE Real Estate. No constituye contrato, apartado o promesa de compraventa.';


    const noticeLines =
      doc.splitTextToSize(
        notice,
        pageWidth - 36
      );


    doc.text(
      noticeLines,
      18,
      y + 6
    );


    // ==============================
    // FOOTER
    // ==============================

    const footerY =
      pageHeight - 12;


    doc.setDrawColor(
      ...this.BORDER
    );


    doc.line(
      18,
      footerY - 5,
      pageWidth - 18,
      footerY - 5
    );


    doc.setTextColor(
      ...this.MUTED
    );

    doc.setFont(
      this.FONT,
      'normal'
    );

    doc.setFontSize(6.5);


    doc.text(
      'WE Real Estate · Cotización informativa',
      18,
      footerY
    );


    doc.text(
      'Página 1',
      pageWidth - 18,
      footerY,
      {
        align: 'right'
      }
    );

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

    // HEADER DE LA PÁGINA
    doc.setFillColor(...this.WE_DARK);

    doc.rect(
        0,
        0,
        pageWidth,
        24,
        'F'
    );

    doc.setTextColor(255, 255, 255);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(12);

    doc.text(
        'TABLA DE AMORTIZACIÓN',
        18,
        14
    );

    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
        data.project.toUpperCase(),
        pageWidth - 18,
        14,
        {
        align: 'right'
        }
    );


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

    doc.setTextColor(...this.WE_DARK);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(9);

    doc.text(
        'RESUMEN DEL FINANCIAMIENTO',
        18,
        yTable
    );

    yTable += 7;

    doc.setFillColor(...this.WE_LIGHT);
    doc.setDrawColor(...this.BORDER);

    doc.roundedRect(
        18,
        yTable,
        pageWidth - 36,
        26,
        3,
        3,
        'FD'
    );


    // MONTO FINANCIADO
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
        'Monto financiado',
        23,
        yTable + 8
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(9);

    doc.text(
        this.money(data.financedAmount),
        23,
        yTable + 15
    );


    // PLAZO
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
        'Plazo',
        80,
        yTable + 8
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(9);

    doc.text(
        `${data.months} meses`,
        80,
        yTable + 15
    );


    // MENSUALIDAD
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
        'Mensualidad',
        123,
        yTable + 8
    );

    doc.setTextColor(...this.WE_BLUE);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(9);

    doc.text(
        this.money(data.monthlyPayment),
        123,
        yTable + 15
    );


    // INTERESES
    doc.setTextColor(...this.MUTED);
    doc.setFont(this.FONT, 'normal');
    doc.setFontSize(7);

    doc.text(
        'Intereses',
        168,
        yTable + 8
    );

    doc.setTextColor(...this.TEXT);
    doc.setFont(this.FONT, 'bold');
    doc.setFontSize(9);

    doc.text(
        this.money(data.interestAmount),
        168,
        yTable + 15
    );



  

    
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

        startY: yTable + 34,

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
        left: 18,
        right: 18,
        bottom: 18
        },

        theme: 'grid',

        styles: {
        font: this.FONT,
        fontSize: 7,
        cellPadding: 2.2,
        textColor: this.TEXT,
        lineColor: this.BORDER,
        lineWidth: 0.15,
        valign: 'middle'
        },

        headStyles: {
        font: this.FONT,
        fontStyle: 'bold',
        fillColor: this.WE_BLUE,
        textColor: [255, 255, 255],
        halign: 'center'
        },

        alternateRowStyles: {
        fillColor: [248, 250, 253]
        },

        columnStyles: {

        0: {
            halign: 'center',
            cellWidth: 17
        },

        1: {
            halign: 'center',
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

        didDrawPage: () => {

        const currentPage =
            doc.getCurrentPageInfo()
            .pageNumber;

        // FOOTER
        doc.setDrawColor(...this.BORDER);

        doc.line(
            18,
            pageHeight - 12,
            pageWidth - 18,
            pageHeight - 12
        );

        doc.setTextColor(...this.MUTED);
        doc.setFont(this.FONT, 'normal');
        doc.setFontSize(6.5);

        doc.text(
            'WE Real Estate · Tabla de amortización',
            18,
            pageHeight - 7
        );

        doc.text(
            `Página ${currentPage}`,
            pageWidth - 18,
            pageHeight - 7,
            {
            align: 'right'
            }
        );

        }

    });

    }

    // ==============================
    // DESCARGAR
    // ==============================

    return doc;

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

  doc.setTextColor(...this.WE_DARK);
  doc.setFont(this.FONT, 'bold');
  doc.setFontSize(10);

  doc.text(
    'PLANES DE FINANCIAMIENTO',
    18,
    startY
  );


  // ==============================
  // TABLA CON ENGANCHE
  // ==============================

  autoTable(doc, {

    startY: startY + 6,

    head: [[
      'CON ENGANCHE',
      '',
      '',
      '',
      ''
    ]],

    body: [],

    margin: {
      left: 18,
      right: 18
    },

    theme: 'grid',

    styles: {
      font: this.FONT,
      fontSize: 7.5,
      cellPadding: 2,
      halign: 'center'
    },

    headStyles: {
      font: this.FONT,
      fontStyle: 'bold',
      fillColor: this.WE_DARK,
      textColor: [255, 255, 255]
    },

    didParseCell: dataCell => {
      if (dataCell.section === 'head') {
        dataCell.cell.colSpan = 5;
      }
    }

  });


  let y =
    (doc as any).lastAutoTable.finalY + 1;


  autoTable(doc, {

    startY: y,

    head: [[
      'Plazo',
      'Interés por plazo',
      'Interés',
      'Total a pagar',
      'Mensualidades'
    ]],

    body: withDownPaymentBody,

    margin: {
      left: 18,
      right: 18
    },

    theme: 'grid',

    styles: {
      font: this.FONT,
      fontSize: 7.2,
      cellPadding: 2.2,
      textColor: this.TEXT,
      lineColor: this.BORDER,
      lineWidth: 0.15,
      valign: 'middle'
    },

    headStyles: {
      font: this.FONT,
      fontStyle: 'bold',
      fillColor: this.WE_BLUE,
      textColor: [255, 255, 255],
      halign: 'center'
    },

    alternateRowStyles: {
      fillColor: [248, 250, 253]
    },

    columnStyles: {
      0: {
        halign: 'center',
        cellWidth: 29
      },
      1: {
        halign: 'center',
        cellWidth: 34
      },
      2: {
        halign: 'right',
        cellWidth: 34
      },
      3: {
        halign: 'right',
        cellWidth: 38
      },
      4: {
        halign: 'right'
      }
    }

  });


  y =
    (doc as any).lastAutoTable.finalY + 8;


  // ==============================
  // TABLA SIN ENGANCHE
  // ==============================

  autoTable(doc, {

    startY: y,

    head: [[
      'SIN ENGANCHE',
      '',
      '',
      '',
      ''
    ]],

    body: [],

    margin: {
      left: 18,
      right: 18
    },

    theme: 'grid',

    styles: {
      font: this.FONT,
      fontSize: 7.5,
      cellPadding: 2,
      halign: 'center'
    },

    headStyles: {
      font: this.FONT,
      fontStyle: 'bold',
      fillColor: this.WE_DARK,
      textColor: [255, 255, 255]
    },

    didParseCell: dataCell => {
      if (dataCell.section === 'head') {
        dataCell.cell.colSpan = 5;
      }
    }

  });


  y =
    (doc as any).lastAutoTable.finalY + 1;


  autoTable(doc, {

    startY: y,

    head: [[
      'Plazo',
      'Interés por plazo',
      'Interés',
      'Total a pagar',
      'Mensualidades'
    ]],

    body: withoutDownPaymentBody,

    margin: {
      left: 18,
      right: 18
    },

    theme: 'grid',

    styles: {
      font: this.FONT,
      fontSize: 7.2,
      cellPadding: 2.2,
      textColor: this.TEXT,
      lineColor: this.BORDER,
      lineWidth: 0.15,
      valign: 'middle'
    },

    headStyles: {
      font: this.FONT,
      fontStyle: 'bold',
      fillColor: this.WE_BLUE,
      textColor: [255, 255, 255],
      halign: 'center'
    },

    alternateRowStyles: {
      fillColor: [248, 250, 253]
    },

    columnStyles: {
      0: {
        halign: 'center',
        cellWidth: 29
      },
      1: {
        halign: 'center',
        cellWidth: 34
      },
      2: {
        halign: 'right',
        cellWidth: 34
      },
      3: {
        halign: 'right',
        cellWidth: 38
      },
      4: {
        halign: 'right'
      }
    }

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

}