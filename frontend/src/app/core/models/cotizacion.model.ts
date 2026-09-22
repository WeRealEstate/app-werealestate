export interface CotizacionAsesor {
  id: number;
  nombre: string;
}

/** Registro histórico de una cotización generada (PDF descargado o compartido). */
export interface CotizacionHistorial {
  id: number;
  asesor: CotizacionAsesor;
  proyecto: string;
  nombreCliente: string;
  /** Solo viene de /cotizador-publico: nombre del asesor que atendió al cliente con el link
   * público (sin sesión, ver CotizadorComponent.advisorName). Null en cotizaciones con sesión. */
  nombreAsesorPublico: string | null;
  manzana: string | null;
  lote: string | null;
  superficie: number;
  precioM2: number;
  precioTotal: number;
  formaPago: string;
  engancheLabel: string;
  enganche: number;
  montoFinanciado: number;
  meses: number;
  mensualidad: number;
  interesPorcentaje: number;
  interesMonto: number;
  totalInversion: number;
  fechaCreacion: string;
}

export interface CotizacionCreateRequest {
  proyecto: string;
  nombreCliente: string;
  nombreAsesorPublico: string | null;
  manzana: string | null;
  lote: string | null;
  superficie: number;
  precioM2: number;
  precioTotal: number;
  formaPago: string;
  engancheLabel: string;
  enganche: number;
  montoFinanciado: number;
  meses: number;
  mensualidad: number;
  interesPorcentaje: number;
  interesMonto: number;
  totalInversion: number;
}
