import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import { ComisionTicket, FORMA_PAGO_COMISION_LABELS } from '../models/finanzas.model';
import { montoEnLetras } from '../utils/monto-en-letras';

type Rgb = [number, number, number];

/** El diseño del ticket se hizo a 998 px de ancho sobre una hoja carta: px() pasa esas medidas a mm, así
 * cada coordenada de abajo se puede comparar directo con el diseño. */
const K = 215.9 / 998;
const px = (n: number): number => n * K;

const AZUL: Rgb = [36, 63, 184];
const AZUL_CLARO: Rgb = [238, 243, 255];
const AZUL_LINEA: Rgb = [205, 216, 242];
const TINTA: Rgb = [17, 24, 39];
const TEXTO: Rgb = [23, 35, 60];
const GRIS: Rgb = [102, 112, 133];
const GRIS_AZUL: Rgb = [91, 104, 135];
const BORDE: Rgb = [217, 224, 234];
const BLANCO: Rgb = [255, 255, 255];

const FUENTE = 'Montserrat';
/** El folio va en monoespaciada, como en el diseño (Courier viene incluida en jsPDF). */
const MONO = 'courier';
const LOGO = '/images/we-logo-color.png';
const LOGO_PROPORCION = 600 / 259;

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

const X0 = px(47);
const X1 = px(956);
const ALTO_FILA = px(58);
/** El bloque de abajo (comisión por pagar, método, totales y talón) va pegado al pie, como en el diseño. */
const Y_BLOQUE_FINAL = px(808);
/** Hasta dónde llegan los renglones del desglose en una hoja antes de pasar a la siguiente. */
const Y_LIMITE_FILAS = px(1235);

interface Espaciado {
  tamano: number;
  color: Rgb;
  espacio: number;
  negrita?: boolean;
  alinear?: 'left' | 'right' | 'center';
}

/** Ticket (comprobante) de pago de una entrega de comisión, en PDF tamaño carta. Ver backend
 * ComisionService.ticket: los datos de la comisión son los que tenía al entregar. */
@Injectable({ providedIn: 'root' })
export class PdfComisionService {
  private fuentes: { regular: string; negrita: string } | null = null;
  private logo: Uint8Array | null = null;

  async descargarTicket(t: ComisionTicket): Promise<void> {
    // jsPDF se carga hasta que se pide un ticket: así no pesa en la pestaña de Finanzas.
    const { jsPDF } = await import('jspdf');
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
    await this.cargarFuentes(doc);
    const logo = await this.cargarLogo();
    const folio = `COM-${String(t.folio).padStart(6, '0')}`;

    this.encabezado(doc, t, folio, logo);
    this.recuadroPrincipal(doc, t);
    const y = this.desglose(doc, t, folio);
    if (y > Y_BLOQUE_FINAL - px(12)) {
      doc.addPage();
      this.encabezadoContinuacion(doc, folio);
    }
    this.bloqueFinal(doc, t, folio, logo);

    const paginas = doc.getNumberOfPages();
    for (let i = 1; i <= paginas; i++) {
      doc.setPage(i);
      this.pie(doc, i, paginas);
    }
    doc.save(`Comision-${folio}-${slug(t.asesorNombre)}.pdf`);
  }

  // ---- secciones ----

  private encabezado(doc: jsPDF, t: ComisionTicket, folio: string, logo: Uint8Array): void {
    doc.addImage(logo, 'PNG', X0, px(28), px(131), px(131) / LOGO_PROPORCION, 'logo-we');
    doc.setDrawColor(...BORDE);
    doc.setLineWidth(0.3);
    doc.line(px(205), px(30), px(205), px(90));
    this.espaciado(doc, 'WE REAL ESTATE · COMPROBANTE DE PAGO', px(232), px(43), { tamano: 8, color: GRIS_AZUL, espacio: 0.45 });
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(22);
    doc.setTextColor(...AZUL);
    doc.text('Comisión de asesor', px(231), px(84));

    doc.setDrawColor(...TINTA);
    doc.setLineWidth(0.55);
    doc.line(X0, px(104), X1, px(104));

    doc.setFont(MONO, 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(...TINTA);
    const textoFolio = `FOLIO ${folio}`;
    doc.text(textoFolio, X0, px(136), { charSpace: 0.2 });
    const finFolio = X0 + doc.getTextWidth(textoFolio) + 0.2 * (textoFolio.length - 1);
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.8);
    doc.setTextColor(...GRIS);
    doc.text(`Emitido ${fechaHora(t.emitido)}`, Math.max(px(240), finFolio + px(26)), px(136));

    doc.setFillColor(...AZUL);
    doc.roundedRect(px(860), px(118), px(96), px(28), 0.8, 0.8, 'F');
    this.espaciado(doc, 'PAGADA', px(908), px(136), { tamano: 7.5, color: BLANCO, espacio: 0.5, alinear: 'center' });

    doc.setDrawColor(...BORDE);
    doc.setLineWidth(0.3);
    doc.line(X0, px(158), X1, px(158));
  }

  /** Total entregado a la izquierda; asesor, periodo y entrega en el panel azul de la derecha. */
  private recuadroPrincipal(doc: jsPDF, t: ComisionTicket): void {
    const y0 = px(174);
    const y1 = px(354);
    const xm = px(551);
    const r = px(14);
    // Todo azul claro y encima la mitad izquierda en blanco: así cada lado conserva sus esquinas redondas.
    doc.setFillColor(...AZUL_CLARO);
    doc.roundedRect(X0, y0, X1 - X0, y1 - y0, r, r, 'F');
    doc.setFillColor(...BLANCO);
    doc.roundedRect(X0, y0, xm - X0 + r, y1 - y0, r, r, 'F');
    doc.setFillColor(...AZUL_CLARO);
    doc.rect(xm, y0 + 0.5, r, y1 - y0 - 1, 'F');
    doc.setDrawColor(...AZUL);
    doc.setLineWidth(0.5);
    doc.roundedRect(X0, y0, X1 - X0, y1 - y0, r, r, 'S');

    // Izquierda: total entregado, grande, y en letras.
    const xi = px(79);
    this.espaciado(doc, 'TOTAL ENTREGADO', xi, px(209), { tamano: 7.5, color: TINTA, espacio: 0.55 });
    const monto = dinero(t.monto);
    const anchoMax = xm - xi - px(24);
    doc.setFont(FUENTE, 'bold');
    let tamano = 33;
    const anchoCon = (tam: number): number => {
      doc.setFontSize(10);
      const mxn = doc.getTextWidth('MXN');
      doc.setFontSize(tam);
      return doc.getTextWidth(monto) + px(8) + mxn;
    };
    while (tamano > 18 && anchoCon(tamano) > anchoMax) tamano--;
    doc.setFontSize(tamano);
    doc.setTextColor(...TINTA);
    doc.text(monto, xi, px(283));
    const finMonto = xi + doc.getTextWidth(monto);
    doc.setFontSize(10);
    doc.text('MXN', finMonto + px(8), px(281));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.3);
    doc.setTextColor(...GRIS);
    const letras = doc.splitTextToSize(montoEnLetras(t.monto), xm - xi - px(20)) as string[];
    if (letras.length === 1) doc.text(letras[0], xi, px(327));
    else doc.text(letras.slice(0, 2), xi, px(318), { lineHeightFactor: 1.35 });

    // Derecha: asesor.
    const xr = px(579);
    this.etiqueta(doc, 'ASESOR', xr, px(200));
    this.textoQueQuepa(doc, t.asesorNombre, xr, px(224), X1 - xr - px(20), { tamano: 10.5, negrita: true, color: TINTA });
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.3);
    doc.setTextColor(...GRIS);
    doc.text(t.asesorExterno ? 'Asesor externo' : 'Asesor interno', xr, px(248));

    doc.setDrawColor(...AZUL_LINEA);
    doc.setLineWidth(0.3);
    doc.line(xm, px(265), X1, px(265));
    doc.line(px(752), px(265), px(752), y1);

    // Periodo: fechas de los abonos que cubre la entrega.
    const n = t.abonos.length;
    this.etiqueta(doc, 'PERIODO', xr, px(290));
    this.textoQueQuepa(doc, periodo(t), xr, px(313), px(752) - xr - px(12), { tamano: 10, negrita: true, color: TINTA });
    this.sub(doc, `${n} ${n === 1 ? 'abono' : 'abonos'}`, xr, px(337));

    const xe = px(780);
    const [anio, mes, dia] = partesFecha(t.fechaEntrega);
    this.etiqueta(doc, 'ENTREGA', xe, px(290));
    this.textoQueQuepa(doc, `${diaSemana(t.fechaEntrega)} ${dia} ${MESES[mes - 1]}`, xe, px(313), X1 - xe - px(16), {
      tamano: 10,
      negrita: true,
      color: TINTA,
    });
    this.sub(doc, String(anio), xe, px(337));
  }

  /** Un renglón por abono que cubre la entrega. Si no caben en la hoja, siguen en la siguiente.
   * Devuelve dónde terminó el último renglón. */
  private desglose(doc: jsPDF, t: ComisionTicket, folio: string): number {
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(...TINTA);
    doc.text('Desglose por abono', X0, px(389));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.3);
    doc.setTextColor(...GRIS);
    doc.text(['1 venta', t.desarrollo].filter(Boolean).join(' · '), X1, px(389), { align: 'right' });

    let y = this.cabeceraTabla(doc, px(404));
    for (const abono of t.abonos) {
      if (y + ALTO_FILA > Y_LIMITE_FILAS) {
        doc.addPage();
        this.encabezadoContinuacion(doc, folio);
        y = this.cabeceraTabla(doc, px(96));
      }
      this.filaAbono(doc, t, abono, y);
      y += ALTO_FILA;
    }
    return y;
  }

  private cabeceraTabla(doc: jsPDF, y: number): number {
    const alto = px(32);
    doc.setFillColor(...AZUL_CLARO);
    doc.roundedRect(X0, y, X1 - X0, alto, 0.8, 0.8, 'F');
    const base = y + px(20.5);
    this.etiqueta(doc, 'VENTA · CLIENTE · LOTE', px(62), base);
    this.etiqueta(doc, 'CONCEPTO', px(477), base);
    this.etiqueta(doc, 'TASA', px(782), base, 'right');
    this.etiqueta(doc, 'COMISIÓN', px(940), base, 'right');
    return y + alto;
  }

  private filaAbono(doc: jsPDF, t: ComisionTicket, abono: ComisionTicket['abonos'][number], y: number): void {
    const x = px(62);
    const finColumna = px(455);
    const numero = t.ventaNumero !== null ? numeroVenta(t.ventaNumero) : '';
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(9);
    doc.setTextColor(...AZUL);
    if (numero) doc.text(numero, x, y + px(23));
    const xCliente = numero ? x + doc.getTextWidth(numero) + px(6) : x;
    doc.setTextColor(...TINTA);
    doc.text(recortar(doc, t.cliente, finColumna - xCliente), xCliente, y + px(23));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...GRIS);
    const detalle = t.ventaEliminada ? 'Venta eliminada' : [t.desarrollo, t.lotes].filter(Boolean).join(' · ') || '—';
    doc.text(recortar(doc, detalle, finColumna - x), x, y + px(43));

    const xc = px(477);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(7.8);
    doc.setFillColor(...AZUL);
    doc.roundedRect(xc, y + px(7), doc.getTextWidth(abono.concepto) + px(18), px(20), 0.6, 0.6, 'F');
    doc.setTextColor(...BLANCO);
    doc.text(abono.concepto, xc + px(9), y + px(21));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...GRIS);
    const [, mes, dia] = partesFecha(abono.fecha);
    doc.text(`${String(dia).padStart(2, '0')} ${MESES[mes - 1]}`, xc, y + px(45));

    doc.setFontSize(9);
    doc.setTextColor(...TEXTO);
    doc.text(porcentaje(t.porcentaje), px(782), y + px(23), { align: 'right' });
    doc.setFont(FUENTE, 'bold');
    doc.setTextColor(...TINTA);
    doc.text(dinero(abono.monto), px(940), y + px(23), { align: 'right' });

    doc.setDrawColor(...BORDE);
    doc.setLineWidth(0.25);
    doc.line(X0, y + ALTO_FILA, X1, y + ALTO_FILA);
  }

  /** Avance de la comisión, método de entrega, totales y el talón para el asesor. */
  private bloqueFinal(doc: jsPDF, t: ComisionTicket, folio: string, logo: Uint8Array): void {
    const xi = px(68);
    const xd = px(935);

    // Comisión por pagar: cómo va la comisión completa con esta entrega incluida.
    doc.setDrawColor(...BORDE);
    doc.setLineWidth(0.3);
    doc.roundedRect(X0, Y_BLOQUE_FINAL, X1 - X0, px(141), px(10), px(10), 'S');
    const venta = t.ventaNumero !== null ? ` · VENTA ${numeroVenta(t.ventaNumero)}` : '';
    this.etiqueta(doc, `COMISIÓN POR PAGAR${venta}`, xi, px(838));
    const falta = Math.max(0, redondear(t.comisionTotal - t.entregadoAcumulado));
    const avance = t.comisionTotal > 0 ? (t.entregadoAcumulado / t.comisionTotal) * 100 : 0;
    // Mientras falte algo no se muestra 100%, aunque el redondeo dé 100.
    const pct = falta === 0 ? 100 : Math.max(0, Math.min(99, Math.round(avance)));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.3);
    doc.setTextColor(...GRIS);
    doc.text(`${pct}% pagado`, xd, px(838), { align: 'right' });

    const by = px(857);
    const bh = px(8);
    const bw = xd - xi;
    doc.setFillColor(...AZUL_CLARO);
    doc.roundedRect(xi, by, bw, bh, bh / 2, bh / 2, 'F');
    if (pct > 0) {
      doc.setFillColor(...AZUL);
      doc.roundedRect(xi, by, Math.max(bh, (bw * pct) / 100), bh, bh / 2, bh / 2, 'F');
    }

    this.parValor(doc, 'Comisión total', dinero(t.comisionTotal), xi);
    this.parValor(doc, 'Pagado a la fecha', dinero(t.entregadoAcumulado), px(364));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...GRIS);
    doc.text('Pendiente por pagar', xd, px(893), { align: 'right' });
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(13);
    doc.setTextColor(...AZUL);
    doc.text(dinero(falta), xd, px(922), { align: 'right' });

    // Método de entrega: solo cómo se entregó (efectivo o transferencia). Con/sin IVA, con/sin factura y el gasto
    // se guardan y se ven dentro del sistema, no en el ticket.
    const yb = px(966);
    const hb = px(159);
    doc.setDrawColor(...BORDE);
    doc.roundedRect(X0, yb, px(491) - X0, hb, px(10), px(10), 'S');
    this.etiqueta(doc, 'MÉTODO DE ENTREGA', xi, px(994));
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(15);
    doc.setTextColor(...TINTA);
    doc.text(FORMA_PAGO_COMISION_LABELS[t.formaPago], xi, px(1055));

    // Totales: la comisión del copropietario va en "Comisión secundaria".
    const xt = px(531);
    doc.setFillColor(...AZUL_CLARO);
    doc.roundedRect(px(511), yb, X1 - px(511), hb, px(10), px(10), 'F');
    const totales: [string, number, number][] = [
      ['Subtotal comisiones', t.secundaria ? 0 : t.monto, px(999)],
      ['Comisión secundaria', t.secundaria ? t.monto : 0, px(1028)],
    ];
    for (const [etiqueta, valor, yf] of totales) {
      doc.setFont(FUENTE, 'normal');
      doc.setFontSize(9);
      doc.setTextColor(...GRIS);
      doc.text(etiqueta, xt, yf);
      doc.setTextColor(...TINTA);
      doc.text(dinero(valor), xd, yf, { align: 'right' });
    }
    doc.setDrawColor(...AZUL_LINEA);
    doc.line(xt, px(1045), xd, px(1045));
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(9.5);
    doc.setTextColor(...TINTA);
    doc.text('Total', xt, px(1082));
    doc.setFontSize(15);
    doc.setTextColor(...AZUL);
    doc.text(dinero(t.monto), xd, px(1084), { align: 'right' });

    // Talón del asesor, separado con línea punteada.
    const yl = px(1148);
    const titulo = 'TALÓN DEL ASESOR';
    const centro = (X0 + X1) / 2;
    const anchoTitulo = this.espaciado(doc, titulo, centro, yl + px(4), {
      tamano: 7,
      color: [148, 158, 178],
      espacio: 0.5,
      alinear: 'center',
    });
    doc.setDrawColor(...BORDE);
    doc.setLineWidth(0.3);
    doc.setLineDashPattern([0.8, 0.8], 0);
    doc.line(X0, yl, centro - anchoTitulo / 2 - px(12), yl);
    doc.line(centro + anchoTitulo / 2 + px(12), yl, X1, yl);
    doc.setLineDashPattern([], 0);

    const yt = px(1171);
    doc.setFillColor(...AZUL_CLARO);
    doc.roundedRect(X0, yt, X1 - X0, px(67), px(12), px(12), 'F');
    doc.addImage(logo, 'PNG', px(72), px(1188), px(73), px(73) / LOGO_PROPORCION, 'logo-we');
    this.espaciado(doc, 'FOLIO', px(173), px(1197), { tamano: 6.8, color: GRIS_AZUL, espacio: 0.5 });
    doc.setFont(MONO, 'bold');
    doc.setFontSize(10);
    doc.setTextColor(...TINTA);
    doc.text(folio, px(173), px(1218));
    this.espaciado(doc, 'ASESOR', px(299), px(1197), { tamano: 6.8, color: GRIS_AZUL, espacio: 0.5 });
    this.textoQueQuepa(doc, t.asesorNombre, px(299), px(1218), px(488) - px(299), { tamano: 9.5, negrita: true, color: TINTA });
    this.espaciado(doc, 'ENTREGA', px(500), px(1197), { tamano: 6.8, color: GRIS_AZUL, espacio: 0.5 });
    const [anio, mes, dia] = partesFecha(t.fechaEntrega);
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(9.5);
    doc.setTextColor(...TINTA);
    doc.text(`${diaSemana(t.fechaEntrega)} ${dia} ${MESES[mes - 1]} ${anio}`, px(500), px(1218));
    doc.setFontSize(16);
    doc.setTextColor(...AZUL);
    doc.text(dinero(t.monto), px(932), px(1214), { align: 'right' });
  }

  private encabezadoContinuacion(doc: jsPDF, folio: string): void {
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(12);
    doc.setTextColor(...AZUL);
    doc.text('Comisión de asesor', X0, px(52));
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(...GRIS);
    doc.text(`Folio ${folio} · continuación`, X1, px(52), { align: 'right' });
    doc.setDrawColor(...TINTA);
    doc.setLineWidth(0.4);
    doc.line(X0, px(70), X1, px(70));
  }

  private pie(doc: jsPDF, pagina: number, paginas: number): void {
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(7.8);
    doc.setTextColor(...GRIS);
    doc.text('We Real Estate · Oaxaca, México', X0, px(1262));
    doc.text('finanzas.weinmobiliaria@gmail.com · +52 951 401 9496', X1, px(1262), { align: 'right' });
    if (paginas > 1) doc.text(`${pagina} de ${paginas}`, (X0 + X1) / 2, px(1262), { align: 'center' });
  }

  // ---- piezas de texto ----

  /** Texto con espacio entre letras (las etiquetas en mayúsculas del diseño). Devuelve su ancho. */
  private espaciado(doc: jsPDF, texto: string, x: number, y: number, o: Espaciado): number {
    doc.setFont(FUENTE, o.negrita === false ? 'normal' : 'bold');
    doc.setFontSize(o.tamano);
    doc.setTextColor(...o.color);
    // getTextWidth no cuenta el espacio entre letras: se suma a mano para alinear a la derecha o al centro.
    const ancho = doc.getTextWidth(texto) + o.espacio * (texto.length - 1);
    const xi = o.alinear === 'right' ? x - ancho : o.alinear === 'center' ? x - ancho / 2 : x;
    doc.text(texto, xi, y, { charSpace: o.espacio });
    return ancho;
  }

  private etiqueta(doc: jsPDF, texto: string, x: number, y: number, alinear: 'left' | 'right' = 'left'): void {
    this.espaciado(doc, texto, x, y, { tamano: 7, color: AZUL, espacio: 0.45, alinear });
  }

  private sub(doc: jsPDF, texto: string, x: number, y: number): void {
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(8.3);
    doc.setTextColor(...GRIS);
    doc.text(texto, x, y);
  }

  /** Etiqueta gris con su monto en negritas debajo (bloque "Comisión por pagar"). */
  private parValor(doc: jsPDF, etiqueta: string, valor: string, x: number): void {
    doc.setFont(FUENTE, 'normal');
    doc.setFontSize(9);
    doc.setTextColor(...GRIS);
    doc.text(etiqueta, x, px(893));
    doc.setFont(FUENTE, 'bold');
    doc.setFontSize(10);
    doc.setTextColor(...TINTA);
    doc.text(valor, x, px(918));
  }

  /** Achica la letra hasta que el texto quepa (un nombre largo); si ni así, lo recorta con "…". */
  private textoQueQuepa(
    doc: jsPDF,
    texto: string,
    x: number,
    y: number,
    anchoMax: number,
    o: { tamano: number; negrita: boolean; color: Rgb },
  ): void {
    doc.setFont(FUENTE, o.negrita ? 'bold' : 'normal');
    doc.setTextColor(...o.color);
    let tamano = o.tamano;
    doc.setFontSize(tamano);
    while (tamano > o.tamano * 0.75 && doc.getTextWidth(texto) > anchoMax) {
      tamano -= 0.5;
      doc.setFontSize(tamano);
    }
    doc.text(recortar(doc, texto, anchoMax), x, y);
  }

  // ---- recursos ----

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

function dinero(valor: number): string {
  return `$${valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function redondear(valor: number): number {
  return Math.round(valor * 100) / 100;
}

/** 5 → "5%", 4.1667 → "4.17%". */
function porcentaje(valor: number): string {
  return `${Number(valor.toFixed(2))}%`;
}

/** Como en el diseño: "#0148". */
function numeroVenta(numero: number): string {
  return `#${String(numero).padStart(4, '0')}`;
}

function partesFecha(iso: string): [number, number, number] {
  const [anio, mes, dia] = iso.slice(0, 10).split('-').map(Number);
  return [anio, mes, dia];
}

function diaSemana(iso: string): string {
  const [anio, mes, dia] = partesFecha(iso);
  return DIAS[new Date(anio, mes - 1, dia).getDay()];
}

/** "2026-10-10T13:41:05" → "10 oct 2026, 13:41 h". */
function fechaHora(iso: string): string {
  const [anio, mes, dia] = partesFecha(iso);
  return `${dia} ${MESES[mes - 1]} ${anio}, ${iso.slice(11, 16)} h`;
}

/** Fechas de los abonos que cubre la entrega: "6 oct", "6 oct – 6 dic" o, si cambia de año, con los años. */
function periodo(t: ComisionTicket): string {
  if (t.abonos.length === 0) return '—';
  const fechas = t.abonos.map((a) => a.fecha).sort();
  const [a1, m1, d1] = partesFecha(fechas[0]);
  const [a2, m2, d2] = partesFecha(fechas[fechas.length - 1]);
  if (fechas[0] === fechas[fechas.length - 1]) return `${d1} ${MESES[m1 - 1]}`;
  if (a1 === a2) return `${d1} ${MESES[m1 - 1]} – ${d2} ${MESES[m2 - 1]}`;
  return `${d1} ${MESES[m1 - 1]} ${a1} – ${d2} ${MESES[m2 - 1]} ${a2}`;
}

/** Para el nombre del archivo: sin acentos ni espacios. */
function slug(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
