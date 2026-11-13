import { Component, input, output } from '@angular/core';
import type { ExtraPago } from '../../../../core/utils/formas-pago';
import { ValuePulseDirective } from '../../../../shared/motion/value-pulse.directive';

/** Lo que muestra la tarjeta "Resumen de cotización": ya calculado por el Cotizador, aquí solo se presenta. */
export interface ResumenCotizacion {
  currentProjectImage: string;
  currentProjectName: string;
  currentProjectLocation: string;
  esMultiLote: boolean;
  superficieTotal: number;
  cantidadLotes: number;
  cashDiscountApplied: boolean;
  precioM2Promedio: number;
  displayedLandPrice: number;
  paymentMethodLabel: string;
  isPromocion: boolean;
  promocionUsaAportaciones: boolean;
  aportacionesTotal: number;
  promocionAnnualContribution: number;
  promocionContribucionesCount: number;
  llevaEntrada: boolean;
  /** Anualidades o aportaciones de la forma de pago (null sin extra). */
  extra: ExtraPago | null;
  etiquetaEntrada: string;
  downPayment: number;
  isAnnualities: boolean;
  annualContributionsCount: number;
  annualContributionsTotal: number;
  isAportaciones: boolean;
  numeroAportaciones: number;
  financedAmount: number;
  hayFormaPago: boolean;
  esContado: boolean;
  totalInvestment: number;
  monthlyPayment: number;
  financingMonths: number;
  interestAmount: number;
  isQuoteValid: boolean;
  motivoCotizacionIncompleta: string | null;
}

/** Interior de la tarjeta "Resumen de cotización" del Cotizador (la tarjeta en sí, pegajosa en pantallas anchas,
 * la pone el Cotizador): proyecto, precio, forma de pago, mensualidad, totales y los botones del PDF. */
@Component({
  selector: 'app-cotizador-resumen',
  standalone: true,
  imports: [ValuePulseDirective],
  templateUrl: './cotizador-resumen.component.html',
  host: { class: 'block' },
})
export class CotizadorResumenComponent {
  readonly resumen = input.required<ResumenCotizacion>();
  readonly generar = output<void>();
  readonly compartir = output<void>();
}
