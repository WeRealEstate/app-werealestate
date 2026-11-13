import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import { cargarFuentes, cargarJsPdf } from './pdf-cotizacion/pdf-base';
import { dibujarHojaCotizacion } from './pdf-cotizacion/hoja-cotizacion';
import { dibujarHojasAmortizacion } from './pdf-cotizacion/hojas-amortizacion';
import { armarPdfPlano } from './pdf-cotizacion/pdf-plano';
import type { PlanoPdfData, QuotePdfData } from './pdf-cotizacion/pdf-tipos';

export type { PlanoPdfData, QuotePdfData } from './pdf-cotizacion/pdf-tipos';

/** PDF de la cotización (hoja de propuesta + tabla de amortización) y del plano interactivo. El dibujo de cada
 * parte vive en `pdf-cotizacion/`; aquí solo se arma el documento y se entrega (descarga o archivo). */
@Injectable({
  providedIn: 'root',
})
export class PdfService {
  async downloadQuotePdf(data: QuotePdfData): Promise<void> {
    const doc = await this.buildQuotePdf(data);
    doc.save(this.getFileName(data));
  }

  async createQuoteFile(data: QuotePdfData): Promise<File> {
    const doc = await this.buildQuotePdf(data);
    const blob = doc.output('blob');
    return new File([blob], this.getFileName(data), { type: 'application/pdf' });
  }

  async downloadPlanoPdf(data: PlanoPdfData): Promise<void> {
    const doc = await armarPdfPlano(data);
    const nombre = data.desarrolloNombre.replace(/\s+/g, '-');
    doc.save(`Plano-${nombre}.pdf`);
  }

  private async buildQuotePdf(data: QuotePdfData): Promise<jsPDF> {
    const { jspdf, autoTable } = await cargarJsPdf();
    const doc = new jspdf.jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
    await cargarFuentes(doc);

    dibujarHojaCotizacion(doc, data);

    // paymentMethod es la etiqueta de la forma de pago ("Contado", "60 meses"…); el contado llega sin tabla.
    if (data.paymentMethod !== 'cash' && data.amortizationTable.length > 0) {
      dibujarHojasAmortizacion(doc, autoTable, data);
    }
    return doc;
  }

  private getFileName(data: QuotePdfData): string {
    const cliente = data.clientName.replace(/\s+/g, '-').toUpperCase();
    return `Cotizacion-${cliente}.pdf`;
  }
}
