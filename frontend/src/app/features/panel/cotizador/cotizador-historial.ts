import type { QuotePdfData } from '../../../core/services/pdf-cotizacion.service';

/** Registro del historial de cotizaciones (bitácora del admin) a partir de los mismos datos del PDF. */
export function solicitudDeHistorial(data: QuotePdfData, esPublico: boolean, conAnualidades: boolean) {
  // Sin enganche ni pago inicial, el monto que se guarda es el de las anualidades o aportaciones (como
  // siempre se guardó "Total en aportaciones"); con entrada, la entrada, y el extra queda en formaPago.
  const guardaElExtra = data.downPayment <= 0 && !!data.pagoExtra;
  return {
    proyecto: data.project,
    nombreCliente: data.clientName,
    // Solo lo exige/usa el backend en /cotizador-publico (ver CotizacionService.registrarPublica);
    // en el cotizador con sesión el asesor ya se resuelve del token, así que se manda null.
    nombreAsesorPublico: esPublico ? data.advisorName : null,
    manzana: data.blockNumber || null,
    lote: data.lotNumber || null,
    superficie: data.area,
    precioM2: data.pricePerM2,
    precioTotal: data.totalPrice,
    formaPago: data.paymentMethod,
    // (enganche_label mide 30: va el nombre corto, no el del PDF.)
    engancheLabel: guardaElExtra ? (conAnualidades ? 'Total en anualidades' : 'Total en aportaciones') : data.downPaymentLabel,
    enganche: guardaElExtra ? data.pagoExtra!.monto : data.downPayment,
    montoFinanciado: data.financedAmount,
    meses: data.months,
    mensualidad: data.monthlyPayment,
    interesPorcentaje: data.interestPercentage,
    interesMonto: data.interestAmount,
    totalInversion: data.totalInvestment,
  };
}
