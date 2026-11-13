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
