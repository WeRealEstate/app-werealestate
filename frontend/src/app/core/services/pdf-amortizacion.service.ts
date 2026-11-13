import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import {
  Amortizacion,
  PagoMes,
  CONCEPTO_SIN_FECHA,
  etiquetaEstadoPago,
  EstadoPagoPlan,
  fechaCorta,
  frasePagosAtrasados,
} from '../models/amortizacion.model';

type Rgb = [number, number, number];

// Mismos colores, letra y medidas que el PDF de la cotización (ver PdfCotizacionService).
/** Único color de la hoja: la línea delgada del encabezado. Todo lo demás va en negro y gris. */
const WE_BLUE: Rgb = [36, 63, 184];
const TEXT: Rgb = [26, 26, 26];
const MUTED: Rgb = [115, 115, 115];
const BORDER: Rgb = [200, 200, 200];
const LINEA_FILA: Rgb = [225, 225, 225];
/**
 * Color del mes actual en la tabla: barrita a la izquierda del renglón y el texto de su estado. Solo para los
 * estados que dicen algo ya cobrado o vencido (pagado con retraso cuenta como pagado: verde, decisión del usuario);
 * Le toca y Pendiente van sin marca.
 */
const COLOR_MES_ACTUAL: Partial<Record<EstadoPagoPlan, Rgb>> = {
  ATRASADO: [185, 28, 28],
  PAGADO: [21, 128, 61],
  PAGADO_CON_RETRASO: [21, 128, 61],
  ABONADO: [3, 105, 161],
};

const FUENTE = 'Montserrat';
const LOGO = '/images/we-logo-color.png';
const LOGO_PROPORCION = 600 / 259;
const MARGEN = 20;
const ALTO_PIE = 18;

/** Un dato de las cajas de datos: etiqueta chica arriba y valor en negritas abajo. */
interface Campo {
  etiqueta: string;
  valor: string;
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
  private logo: Uint8Array | null = null;

  async descargar(a: Amortizacion): Promise<void> {
    // jsPDF se carga hasta que se pide el PDF: así no pesa en la ficha del cliente ni en la venta.
    const [{ jsPDF }, { default: autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
    const [logo] = await Promise.all([this.cargarLogo(), this.cargarFuentes(doc)]);
    const ancho = doc.internal.pageSize.getWidth();

    this.encabezado(doc, a, ancho, true, 'ESTADO DE CUENTA');
    const logoW = 24;
    const logoH = logoW / LOGO_PROPORCION;
    const logoX = ancho - MARGEN - logoW - 10;
    doc.addImage(logo, 'PNG', logoX, 31, logoW, logoH, 'logo-we');
    const anchoTextoPortada = logoX - MARGEN - 6;

    let y = 32;
    doc.setTextColor(...MUTED);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(7.5);
    doc.text(`ESTADO DE CUENTA AL ${fechaCorta(a.corte)}`, MARGEN, y, { charSpace: 0.4 });
    doc.setTextColor(...TEXT);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(12);
    doc.text(recortar(doc, a.cliente, anchoTextoPortada), MARGEN, y + 8);
    doc.setTextColor(...MUTED);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8);
    doc.text(recortar(doc, `${a.desarrollos.join(' · ')} · ${a.lotes.join(', ')}`, anchoTextoPortada), MARGEN, y + 13.5);
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
    y = this.seccion(doc, 'DATOS DEL CLIENTE', cliente, y, ancho);

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
    y = this.seccion(doc, 'DATOS DE LA VENTA', venta, y, ancho);

    // RESUMEN A LA FECHA
    const atraso = a.pagosAtrasados > 0
      ? `${dinero(a.atrasoMonto)} · ${frasePagosAtrasados(a.pagosAtrasados, a.mensualidadesAtrasadas)}`
      : a.saldo > 0 ? 'Al corriente' : 'Liquidada';
    const resumen: Campo[] = [
      { etiqueta: 'Pagado', valor: dinero(a.pagado), },
      { etiqueta: 'Saldo', valor: dinero(a.saldo) },
      { etiqueta: a.atrasadoDesde ? `Atraso (desde el ${fechaCorta(a.atrasadoDesde)})` : 'Atraso', valor: atraso, columnas: 2 },
    ];
    if (a.proximaFecha && a.proximoMonto) {
      resumen.push({ etiqueta: 'Próximo pago', valor: `${fechaCorta(a.proximaFecha)} · ${dinero(a.proximoMonto)}`, columnas: 2 });
    }
    y = this.seccion(doc, 'RESUMEN A LA FECHA', resumen, y, ancho);

    // TABLA: siempre empieza en una hoja nueva, con el encabezado completo y su título.
    const filas = a.detalleMensual;
    // Renglones del mes de la fecha de corte (el mismo del rótulo "ESTADO DE CUENTA AL"), por su posición en `filas`.
    const mesActual = a.corte.slice(0, 7);
    const marcas = new Map<number, Rgb>();
    filas.forEach((f, i) => {
      const color = f.estado === 'ATRASADO'
        ? COLOR_MES_ACTUAL.ATRASADO
        : f.fecha?.slice(0, 7) === mesActual ? COLOR_MES_ACTUAL[f.estado] : undefined;
      if (color) marcas.set(i, color);
    });
    doc.addPage('letter', 'portrait');
    const primeraHojaTabla = doc.getNumberOfPages();
    this.encabezado(doc, a, ancho, true, 'TABLA DE AMORTIZACIÓN');
    autoTable(doc, {
      startY: 30,
      head: [['N.º', 'DÍA DE PAGO', 'CONCEPTO', 'PAGO CORRESPONDIENTE', 'MONTO PAGADO', 'FECHA DEL PAGO', 'FOLIO', 'SALDO', 'ESTADO']],
      body: filas.map((f) => [
        f.numero ?? '—',
        fechaCorta(f.fecha),
        f.concepto,
        dinero(f.monto),
        columnaPagos(f, (x) => x.monto, dinero(0), true),
        columnaPagos(f, (x) => fechaCorta(x.fecha), '—'),
        columnaPagos(f, (x) => x.folio || '—', '—'),
        f.fecha ? dinero(f.saldo) : '—',
        etiquetaEstadoPago(f.estado, f.fecha, a.corte),
      ]),
      foot: [['', '', 'Total pagado', '', dinero(sumar(filas.map((f) => f.abonadoMes))), '', '', '', '']],
      showFoot: 'lastPage',
      margin: { left: MARGEN, right: MARGEN, top: 26, bottom: ALTO_PIE + 4 },
      theme: 'plain',
      styles: { font: FUENTE, fontSize: 7.5, cellPadding: { top: 2.3, bottom: 2.3, left: 1.5, right: 1.5 }, textColor: TEXT, lineWidth: 0, valign: 'middle' },
      headStyles: { font: FUENTE, fontStyle: 'bold', fillColor: false, textColor: TEXT, fontSize: 6.5 },
      footStyles: { font: FUENTE, fontStyle: 'bold', fillColor: false, textColor: TEXT },
      columnStyles: {
        0: { halign: 'center', cellWidth: 8 },
        1: { halign: 'left', cellWidth: 17 },
        2: { cellWidth: 'auto' },
        3: { halign: 'right', cellWidth: 28 },
        4: { halign: 'right', cellWidth: 24 },
        5: { halign: 'left', cellWidth: 17 },
        6: { halign: 'left', cellWidth: 14 },
        7: { halign: 'right', cellWidth: 20 },
        8: { halign: 'left', cellWidth: 20, cellPadding: { top: 2.3, bottom: 2.3, left: 4.5, right: 1 } },
      },
      didParseCell: (hook) => {
        if (hook.section === 'foot' && hook.column.index >= 3 && hook.column.index !== 5 && hook.column.index !== 6 && hook.column.index !== 8) hook.cell.styles.halign = 'right';
        // El encabezado se alinea como su columna (autoTable no lo hereda del columnStyles).
        if (hook.section === 'head') hook.cell.styles.halign = hook.table.styles.columnStyles?.[hook.column.index]?.halign ?? 'left';
        if (hook.section === 'body' && hook.column.index === 8) {
          const color = marcas.get(hook.row.index);
          if (color) hook.cell.styles.textColor = color;
        }
      },
      didDrawCell: (hook) => {
        const { x, y: yc, width, height } = hook.cell;
        if (hook.section === 'foot') {
          doc.setLineWidth(0.4);
          doc.setDrawColor(...TEXT);
          doc.line(x, yc, x + width, yc);
          return;
        }
        doc.setLineWidth(hook.section === 'head' ? 0.4 : 0.15);
        doc.setDrawColor(...(hook.section === 'head' ? MUTED : LINEA_FILA));
        doc.line(x, yc + height, x + width, yc + height);
        if (hook.section === 'body' && hook.column.index === 0 && marcas.has(hook.row.index)) {
          // Barrita de 1 mm pegada al borde izquierdo del renglón, a lo alto de la celda.
          doc.setFillColor(...marcas.get(hook.row.index)!);
          doc.rect(x - 1.6, yc, 1, height, 'F');
        }
        if (hook.section === 'body' && hook.column.index === 8) {
          doc.setFillColor(...(marcas.get(hook.row.index) ?? MUTED));
          doc.circle(x + 2.6, yc + height / 2, 0.8, 'F');
        }
      },
      // En las hojas que siguen a la de inicio de la tabla, solo la línea con el dato (sin título).
      didDrawPage: (hook) => {
        if (hook.pageNumber > primeraHojaTabla) this.encabezado(doc, a, ancho, false);
      },
    });

    // Notas al pie de la tabla.
    const finTabla = (doc as jsPDF & { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y;
    const notas = [
      'Pago correspondiente: lo que toca pagar ese mes más lo que faltó de los meses anteriores ya vencidos. Un pago cuenta como atrasado desde el día siguiente a su fecha.',
      'Saldo: lo que falta del precio después de cada pago, según el plan.',
      'Monto pagado: cada abono en el mes que cubre (el que marca su recibo), con la fecha del pago y el folio de su recibo en las columnas siguientes; lo que se paga de más en un mes se descuenta de la última mensualidad. Estado: Pagado (cubrió la mensualidad), Abonado (algún pago, sin cubrirla) o Atrasado (su fecha pasó sin ningún pago).',
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

  /** Encabezado sin banda. La primera hoja de cada parte (estado de cuenta y tabla) lleva su título; las que siguen
   * solo la línea azul con el dato gris, sin repetir el título. */
  private encabezado(doc: jsPDF, a: Amortizacion, ancho: number, primera: boolean, titulo = ''): void {
    const dato = `${(a.desarrollos.join(' · ') || 'WE REAL ESTATE').toUpperCase()} · VENTA NO. ${a.ventaNumero}`;
    const yLinea = primera ? 22 : 18;
    if (primera) {
      doc.setTextColor(...TEXT);
      doc.setFont(FUENTE, 'bold');
      doc.setFontSize(15);
      doc.text(titulo, MARGEN, 18);
    }
    doc.setTextColor(...MUTED);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(7);
    doc.text(dato, ancho - MARGEN, primera ? 18 : 14.5, { align: 'right' });
    doc.setDrawColor(...WE_BLUE);
    doc.setLineWidth(0.5);
    doc.line(MARGEN, yLinea, ancho - MARGEN, yLinea);
  }

  /** Título pequeño en mayúsculas con una línea gris fina debajo. Devuelve la y de la línea. */
  private tituloSeccion(doc: jsPDF, titulo: string, y: number, ancho: number): number {
    doc.setTextColor(...TEXT);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(7.5);
    doc.text(titulo, MARGEN, y, { charSpace: 0.5 });
    doc.setDrawColor(...BORDER);
    doc.setLineWidth(0.25);
    doc.line(MARGEN, y + 1.6, ancho - MARGEN, y + 1.6);
    return y + 1.6;
  }

  /** Título con línea y los campos en 4 columnas, sin cajas; un renglón crece si un valor no cabe en una línea.
   * Devuelve dónde sigue el contenido. */
  private seccion(doc: jsPDF, titulo: string, campos: Campo[], y: number, ancho: number): number {
    const columnas = 4;
    const anchoColumna = (ancho - 2 * MARGEN) / columnas;
    const tamano = 8.5;
    // Acomodo: cada campo ocupa N columnas; si no cabe en el renglón, baja al siguiente.
    const renglones: { campo: Campo; columna: number; lineas: string[]; tamano: number }[][] = [];
    let actual: { campo: Campo; columna: number; lineas: string[]; tamano: number }[] = [];
    let ocupadas = 0;
    doc.setFont(FUENTE, 'normal');
    for (const campo of campos) {
      const n = Math.min(campo.columnas ?? 1, columnas);
      if (ocupadas + n > columnas) {
        renglones.push(actual);
        actual = [];
        ocupadas = 0;
      }
      const anchoCampo = anchoColumna * n - 4;
      const t = tamano;
      doc.setFontSize(t);
      const lineas = doc.splitTextToSize(campo.valor, anchoCampo) as string[];
      actual.push({ campo: { ...campo, columnas: n }, columna: ocupadas, lineas: lineas.slice(0, 3), tamano: t });
      ocupadas += n;
    }
    if (actual.length) renglones.push(actual);

    let yRenglon = this.tituloSeccion(doc, titulo, y, ancho) + 4.5;
    for (const renglon of renglones) {
      const lineasMax = Math.max(...renglon.map((c) => c.lineas.length));
      for (const { campo, columna, lineas, tamano: t } of renglon) {
        const x = MARGEN + columna * anchoColumna;
        doc.setTextColor(...MUTED);
        doc.setFont(FUENTE, 'normal');
        doc.setFontSize(6.5);
        doc.text(recortar(doc, campo.etiqueta, anchoColumna * (campo.columnas ?? 1) - 4), x, yRenglon);
        doc.setTextColor(...TEXT);
        doc.setFont(FUENTE, 'normal');
        doc.setFontSize(t);
        doc.text(lineas, x, yRenglon + 4.2);
      }
      yRenglon += 11 + (lineasMax - 1) * 3.6;
    }
    return yRenglon + 3;
  }

  private notas(doc: jsPDF, a: Amortizacion, notas: string[], y: number, ancho: number): void {
    const alto = doc.internal.pageSize.getHeight();
    for (const nota of notas) {
      doc.setFont(FUENTE, 'normal');
      doc.setFontSize(6.5);
      const lineas = doc.splitTextToSize(nota, ancho - 2 * MARGEN) as string[];
      if (y + lineas.length * 3 > alto - ALTO_PIE) {
        doc.addPage('letter', 'portrait');
        this.encabezado(doc, a, ancho, false);
        y = 28;
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
    doc.setLineWidth(0.25);
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

  private async cargarLogo(): Promise<Uint8Array> {
    if (!this.logo) {
      const respuesta = await fetch(LOGO);
      if (!respuesta.ok) throw new Error(`No se pudo cargar el logo: ${LOGO}`);
      this.logo = new Uint8Array(await respuesta.arrayBuffer());
    }
    return this.logo;
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

/**
 * Un abono por bloque, igual de alto en Monto pagado, Fecha del pago y Folio para que queden alineados: la nota
 * («parte de», «a cta. comisión») va en líneas extra bajo el monto y las otras dos columnas dejan esas líneas en blanco.
 */
function columnaPagos(f: Amortizacion['detalleMensual'][number], dato: (p: PagoMes) => string | number, vacio: string, conNotas = false): string {
  if (!f.fecha) return '—';
  if (!f.pagos.length) return vacio;
  return f.pagos.map((p) => {
    const extras = [p.parteDe ? `parte de ${dinero(p.parteDe)}` : '', p.aCuentaComision ? 'a cta. comisión' : ''].filter(Boolean);
    const principal = conNotas ? dinero(p.monto) : String(dato(p));
    return conNotas ? [principal, ...extras].join('\n') : principal + '\n'.repeat(extras.length);
  }).join('\n');
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
