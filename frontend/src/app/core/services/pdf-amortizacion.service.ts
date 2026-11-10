import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import {
  Amortizacion,
  CONCEPTO_SIN_FECHA,
  etiquetaEstadoPago,
  EstadoPagoPlan,
  fechaCorta,
  frasePagosAtrasados,
} from '../models/amortizacion.model';

type Rgb = [number, number, number];

// Mismos colores, letra y medidas que el PDF de la cotización (ver PdfCotizacionService).
const WE_BLUE: Rgb = [36, 63, 184];
const WE_DARK: Rgb = [11, 47, 104];
const WE_LIGHT: Rgb = [238, 243, 255];
const TEXT: Rgb = [23, 35, 60];
const MUTED: Rgb = [102, 112, 133];
const BORDER: Rgb = [217, 224, 234];
const CAJA: Rgb = [247, 249, 252];
const ROJO: Rgb = [185, 28, 28];
const VERDE: Rgb = [21, 128, 61];
const AMBAR: Rgb = [146, 64, 14];
const FONDO_ATRASADO: Rgb = [253, 236, 236];
const FONDO_LE_TOCA: Rgb = [255, 247, 230];

const FUENTE = 'Montserrat';
const MARGEN = 18;
const ALTO_PIE = 18;

/** Un dato de las cajas de datos: etiqueta chica arriba y valor en negritas abajo. */
interface Campo {
  etiqueta: string;
  valor: string;
  color?: Rgb;
  /** Cuántas columnas de la caja ocupa (1 por defecto). */
  columnas?: number;
}

/**
 * Tabla de amortización de una venta en PDF tamaño carta, para dársela al cliente: sus datos, los de la venta, el
 * resumen a la fecha (pagado, saldo y atraso) y la tabla completa con el estado de cada pago. Mismo diseño que la
 * cotización.
 */
@Injectable({ providedIn: 'root' })
export class PdfAmortizacionService {
  private fuentes: { regular: string; negrita: string } | null = null;

  async descargar(a: Amortizacion): Promise<void> {
    // jsPDF se carga hasta que se pide el PDF: así no pesa en la ficha del cliente ni en la venta.
    const [{ jsPDF }, { default: autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
    await this.cargarFuentes(doc);
    const ancho = doc.internal.pageSize.getWidth();

    this.encabezado(doc, a, ancho);
    let y = 34;
    doc.setTextColor(...WE_BLUE);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(9);
    doc.text(`ESTADO DE CUENTA AL ${fechaCorta(a.corte)}`, MARGEN, y);
    doc.setTextColor(...TEXT);
    doc.setFontSize(18);
    doc.text(recortar(doc, a.cliente, ancho - 2 * MARGEN), MARGEN, y + 10);
    doc.setTextColor(...MUTED);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8);
    doc.text(recortar(doc, `${a.desarrollos.join(' · ')} · ${a.lotes.join(', ')}`, ancho - 2 * MARGEN), MARGEN, y + 16);
    y += 26;

    // DATOS DEL CLIENTE
    const cliente: Campo[] = [
      { etiqueta: 'Nombre del cliente', valor: a.cliente, columnas: 2 },
      { etiqueta: 'Número de cliente', valor: a.clienteNumeros.length ? a.clienteNumeros.join(' · ') : 'Sin número' },
      { etiqueta: 'Teléfono', valor: a.clienteTelefono || 'No registrado' },
    ];
    if (a.copropietarios.length > 0) {
      cliente.push({ etiqueta: a.copropietarios.length === 1 ? 'Copropietario' : 'Copropietarios', valor: a.copropietarios.join(', '), columnas: 4 });
    }
    y = this.seccion(doc, 'DATOS DEL CLIENTE', cliente, y, ancho, CAJA);

    // DATOS DE LA VENTA
    const venta: Campo[] = [
      { etiqueta: 'Venta No.', valor: String(a.ventaNumero) },
      { etiqueta: 'Fecha de venta', valor: fechaCorta(a.fechaVenta) },
      { etiqueta: 'Desarrollo', valor: a.desarrollos.join(', ') || '—', columnas: 2 },
      { etiqueta: a.lotes.length === 1 ? 'Lote' : 'Lotes', valor: a.lotes.join(', ') || '—', columnas: 2 },
      { etiqueta: 'Precio', valor: dinero(a.precio) },
      { etiqueta: 'Forma de pago', valor: a.formaPago || '—' },
    ];
    if (a.engancheLabel && a.enganche) venta.push({ etiqueta: a.engancheLabel, valor: dinero(a.enganche) });
    if (a.plazoMeses) venta.push({ etiqueta: 'Plazo', valor: `${a.plazoMeses} meses` });
    if (a.mensualidad) venta.push({ etiqueta: 'Mensualidad', valor: dinero(a.mensualidad) });
    if (a.diaPago > 0) venta.push({ etiqueta: 'Día de pago', valor: `Día ${a.diaPago} de cada mes` });
    if (a.numeroContrato) venta.push({ etiqueta: 'N.º de contrato', valor: a.numeroContrato });
    y = this.seccion(doc, 'DATOS DE LA VENTA', venta, y, ancho, CAJA);

    // RESUMEN A LA FECHA
    const atraso = a.pagosAtrasados > 0
      ? `${dinero(a.atrasoMonto)} · ${frasePagosAtrasados(a.pagosAtrasados, a.mensualidadesAtrasadas)}`
      : a.saldo > 0 ? 'Al corriente' : 'Liquidada';
    const resumen: Campo[] = [
      { etiqueta: 'Pagado', valor: dinero(a.pagado), color: VERDE },
      { etiqueta: 'Saldo', valor: dinero(a.saldo) },
      { etiqueta: a.atrasadoDesde ? `Atraso (desde el ${fechaCorta(a.atrasadoDesde)})` : 'Atraso', valor: atraso, color: a.pagosAtrasados > 0 ? ROJO : VERDE, columnas: 2 },
    ];
    if (a.proximaFecha && a.proximoMonto) {
      resumen.push({ etiqueta: 'Próximo pago', valor: `${fechaCorta(a.proximaFecha)} · ${dinero(a.proximoMonto)}`, columnas: 2 });
    }
    y = this.seccion(doc, 'RESUMEN A LA FECHA', resumen, y, ancho, WE_LIGHT);

    // TABLA
    doc.setTextColor(...WE_DARK);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(10);
    doc.text('TABLA DE AMORTIZACIÓN', MARGEN, y);
    const filas = a.filas;
    autoTable(doc, {
      startY: y + 3,
      head: [['N.º', 'FECHA', 'CONCEPTO', 'MONTO', 'PAGADO', 'ABONADO EN EL MES', 'SALDO', 'ESTADO']],
      body: filas.map((f) => [
        f.numero ?? '—',
        fechaCorta(f.fecha),
        f.concepto,
        dinero(f.monto),
        dinero(f.pagado),
        f.fecha ? dinero(f.abonadoMes) : '—',
        f.fecha ? dinero(f.saldo) : '—',
        etiquetaEstadoPago(f.estado, f.fecha, a.corte),
      ]),
      foot: [['', '', 'Total', dinero(sumar(filas.map((f) => f.monto))), dinero(sumar(filas.map((f) => f.pagado))), dinero(sumar(filas.map((f) => f.abonadoMes))), '', '']],
      showFoot: 'lastPage',
      margin: { left: MARGEN, right: MARGEN, top: 30, bottom: ALTO_PIE + 4 },
      theme: 'grid',
      styles: { font: FUENTE, fontSize: 7, cellPadding: 2, textColor: TEXT, lineColor: BORDER, lineWidth: 0.15, valign: 'middle' },
      headStyles: { font: FUENTE, fontStyle: 'bold', fillColor: WE_BLUE, textColor: [255, 255, 255], halign: 'center' },
      footStyles: { font: FUENTE, fontStyle: 'bold', fillColor: WE_LIGHT, textColor: TEXT },
      alternateRowStyles: { fillColor: [248, 250, 253] },
      columnStyles: {
        0: { halign: 'center', cellWidth: 12 },
        1: { halign: 'center', cellWidth: 22 },
        2: { cellWidth: 'auto' },
        3: { halign: 'right', cellWidth: 26 },
        4: { halign: 'right', cellWidth: 26 },
        5: { halign: 'right', cellWidth: 26 },
        6: { halign: 'right', cellWidth: 26 },
        7: { halign: 'center', cellWidth: 21 },
      },
      didParseCell: (hook) => {
        if (hook.section === 'foot' && hook.column.index >= 3) hook.cell.styles.halign = 'right';
        if (hook.section !== 'body') return;
        const estado: EstadoPagoPlan | undefined = filas[hook.row.index]?.estado;
        if (estado === 'ATRASADO') hook.cell.styles.fillColor = FONDO_ATRASADO;
        if (estado === 'LE_TOCA') hook.cell.styles.fillColor = FONDO_LE_TOCA;
        if (hook.column.index === 7) {
          hook.cell.styles.fontStyle = 'bold';
          hook.cell.styles.textColor = estado === 'PAGADO' ? VERDE : estado === 'ATRASADO' ? ROJO : estado === 'LE_TOCA' ? AMBAR : MUTED;
        }
      },
      // En las hojas que siguen, la banda de arriba igual que en la primera.
      didDrawPage: (hook) => {
        if (hook.pageNumber > 1) this.encabezado(doc, a, ancho);
      },
    });

    // Notas al pie de la tabla.
    const finTabla = (doc as jsPDF & { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y;
    const notas = [
      'Lo abonado cubre los pagos del más antiguo al más nuevo. Un pago cuenta como atrasado desde el día siguiente a su fecha.',
      'Saldo: lo que falta del precio después de cada pago, según el plan.',
      'Abonado en el mes: el dinero recibido ese mes, según la fecha de cada recibo.',
    ];
    if (filas.some((f) => f.concepto === CONCEPTO_SIN_FECHA)) {
      notas.push(`${CONCEPTO_SIN_FECHA}: parte del precio a la que la venta no le asigna fecha (por ejemplo, anualidades); no cuenta como atraso.`);
    }
    this.notas(doc, a, notas, finTabla + 6, ancho);

    const paginas = doc.getNumberOfPages();
    for (let i = 1; i <= paginas; i++) {
      doc.setPage(i);
      this.pie(doc, i, paginas, ancho);
    }
    doc.save(`Amortizacion-Venta-${a.ventaNumero}-${slug(a.cliente)}.pdf`);
  }

  /** Banda azul oscuro de arriba, como en la hoja de amortización de la cotización. */
  private encabezado(doc: jsPDF, a: Amortizacion, ancho: number): void {
    doc.setFillColor(...WE_DARK);
    doc.rect(0, 0, ancho, 24, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(12);
    doc.text('TABLA DE AMORTIZACIÓN', MARGEN, 14);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(7);
    doc.text(`${(a.desarrollos.join(' · ') || 'WE REAL ESTATE').toUpperCase()} · VENTA NO. ${a.ventaNumero}`, ancho - MARGEN, 14, { align: 'right' });
  }

  /** Título y caja redondeada con los campos en 4 columnas; crece a lo alto si un valor no cabe en una línea.
   * Devuelve dónde sigue el contenido. */
  private seccion(doc: jsPDF, titulo: string, campos: Campo[], y: number, ancho: number, fondo: Rgb): number {
    const columnas = 4;
    const interior = ancho - 2 * MARGEN - 10;
    const anchoColumna = interior / columnas;
    // Acomodo: cada campo ocupa N columnas; si no cabe en el renglón, baja al siguiente.
    const renglones: { campo: Campo; columna: number; lineas: string[] }[][] = [];
    let actual: { campo: Campo; columna: number; lineas: string[] }[] = [];
    let ocupadas = 0;
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(8);
    for (const campo of campos) {
      const n = Math.min(campo.columnas ?? 1, columnas);
      if (ocupadas + n > columnas) {
        renglones.push(actual);
        actual = [];
        ocupadas = 0;
      }
      const lineas = doc.splitTextToSize(campo.valor, anchoColumna * n - 4) as string[];
      actual.push({ campo: { ...campo, columnas: n }, columna: ocupadas, lineas: lineas.slice(0, 3) });
      ocupadas += n;
    }
    if (actual.length) renglones.push(actual);

    const altoLinea = 3.6;
    const altos = renglones.map((r) => 8 + Math.max(...r.map((c) => c.lineas.length)) * altoLinea);
    const altoCaja = altos.reduce((s, h) => s + h, 0) + 3;

    doc.setTextColor(...WE_DARK);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(10);
    doc.text(titulo, MARGEN, y);
    const yCaja = y + 4;
    doc.setFillColor(...fondo);
    doc.setDrawColor(...BORDER);
    doc.roundedRect(MARGEN, yCaja, ancho - 2 * MARGEN, altoCaja, 3, 3, 'FD');

    let yRenglon = yCaja + 3;
    renglones.forEach((renglon, i) => {
      for (const { campo, columna, lineas } of renglon) {
        const x = MARGEN + 5 + columna * anchoColumna;
        doc.setTextColor(...MUTED);
        doc.setFont(FUENTE, 'normal');
        doc.setFontSize(7);
        doc.text(recortar(doc, campo.etiqueta, anchoColumna * (campo.columnas ?? 1) - 4), x, yRenglon + 4);
        doc.setTextColor(...(campo.color ?? TEXT));
        doc.setFont(FUENTE, 'bold');
        doc.setFontSize(8);
        doc.text(lineas, x, yRenglon + 8.5);
      }
      yRenglon += altos[i];
    });
    return yCaja + altoCaja + 9;
  }

  private notas(doc: jsPDF, a: Amortizacion, notas: string[], y: number, ancho: number): void {
    const alto = doc.internal.pageSize.getHeight();
    for (const nota of notas) {
      doc.setFont(FUENTE, 'normal');
      doc.setFontSize(6.5);
      const lineas = doc.splitTextToSize(nota, ancho - 2 * MARGEN) as string[];
      if (y + lineas.length * 3 > alto - ALTO_PIE) {
        doc.addPage('letter', 'portrait');
        this.encabezado(doc, a, ancho);
        y = 34;
        doc.setFont(FUENTE, 'normal');
        doc.setFontSize(6.5);
      }
      doc.setTextColor(...MUTED);
      doc.text(lineas, MARGEN, y);
      y += lineas.length * 3 + 1.2;
    }
  }

  /** Pie igual al de la cotización: línea, "WE Real Estate · Tabla de amortización" y el número de página. */
  private pie(doc: jsPDF, pagina: number, paginas: number, ancho: number): void {
    const alto = doc.internal.pageSize.getHeight();
    doc.setDrawColor(...BORDER);
    doc.setLineWidth(0.2);
    doc.line(MARGEN, alto - 12, ancho - MARGEN, alto - 12);
    doc.setTextColor(...MUTED);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(6.5);
    doc.text('WE Real Estate · Tabla de amortización', MARGEN, alto - 7);
    doc.text(`Página ${pagina} de ${paginas}`, ancho - MARGEN, alto - 7, { align: 'right' });
  }

  private async cargarFuentes(doc: jsPDF): Promise<void> {
    if (!this.fuentes) {
      const [regular, negrita] = await Promise.all([
        base64De('/fonts/Montserrat-Regular.ttf'),
        base64De('/fonts/Montserrat-Bold.ttf'),
      ]);
      this.fuentes = { regular, negrita };
    }
    doc.addFileToVFS('Montserrat-Regular.ttf', this.fuentes.regular);
    doc.addFont('Montserrat-Regular.ttf', FUENTE, 'normal');
    doc.addFileToVFS('Montserrat-Bold.ttf', this.fuentes.negrita);
    doc.addFont('Montserrat-Bold.ttf', FUENTE, 'bold');
    doc.setFont(FUENTE, 'normal');
  }
}

async function base64De(url: string): Promise<string> {
  const respuesta = await fetch(url);
  if (!respuesta.ok) throw new Error(`No se pudo cargar la fuente: ${url}`);
  const bytes = new Uint8Array(await respuesta.arrayBuffer());
  let binario = '';
  for (let i = 0; i < bytes.length; i++) binario += String.fromCharCode(bytes[i]);
  return btoa(binario);
}

/** Recorta con "…" lo que no quepa en el ancho (con la letra que ya tenga el documento). */
function recortar(doc: jsPDF, texto: string, anchoMax: number): string {
  if (doc.getTextWidth(texto) <= anchoMax) return texto;
  let corto = texto;
  while (corto.length > 1 && doc.getTextWidth(`${corto}…`) > anchoMax) corto = corto.slice(0, -1);
  return `${corto.trimEnd()}…`;
}

function dinero(valor: number): string {
  return `$${valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function sumar(valores: number[]): number {
  return Math.round(valores.reduce((s, v) => s + v, 0) * 100) / 100;
}

function slug(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
