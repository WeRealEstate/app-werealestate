import { Lote } from './lote.model';
import { UsuarioResumen } from './lead.model';

/** Un lote dentro de una venta, con el precio negociado para ese lote en particular. */
export interface VentaLote {
  id: number;
  lote: Lote;
  precio: number;
}

/** Asesor de una venta, sea interno (usuario real del sistema) o externo (ver AsesorExterno). */
export interface VentaAsesor {
  id: number;
  nombre: string;
  externo: boolean;
}

/**
 * Registro de una venta cerrada. cliente es texto libre a propósito (ver backend Venta): no todo
 * comprador pasó por el CRM como lead. El asesor sí es una relación real, a un usuario interno o a
 * un asesor externo registrado. Puede incluir varios lotes (misma operación, una sola
 * mensualidad/plazo/saldo combinado — ver VentaLote).
 */
/** Una aportación programada de la venta (esquema "Con aportaciones"): mes (1-12), año y monto. */
export interface VentaAportacion {
  /** Lo asigna el servidor; no va al crear la venta. */
  id?: number;
  anio: number;
  mes: number;
  monto: number;
}

/** Cómo se firmó el contrato. */
export type MedioFirma = 'FISICO' | 'DOCUSIGN';

export const MEDIO_FIRMA_LABELS: Record<MedioFirma, string> = {
  FISICO: 'En físico',
  DOCUSIGN: 'DocuSign',
};

export interface Venta {
  id: number;
  /** Número de venta: automático (el siguiente, mayor + 1) o escrito a mano al registrarla; se puede cambiar. */
  numero: number;
  lotes: VentaLote[];
  /** Vacía si la venta no se pactó con aportaciones. */
  aportaciones: VentaAportacion[];
  cliente: string;
  /** El cliente registrado al que está ligada (null solo en datos muy viejos). */
  clienteId: number | null;
  /** Copropietarios además del cliente principal (máximo 4). */
  copropietarios: { id: number; nombreCompleto: string }[];
  asesor: VentaAsesor;
  formaPago: string;
  fechaVenta: string;
  /** Términos de financiamiento; null en ventas de contado. */
  mensualidad: number | null;
  plazoMeses: number | null;
  /** "Enganche" / "Pago inicial" / "Aportación anual" (mismo concepto que ya usa Cotización);
   * ambos null cuando no aplica (Sin enganche / Contado). */
  engancheLabel: string | null;
  enganche: number | null;
  /** Día del mes (1-31) en que paga la mensualidad (en un mes más corto se cobra el último día). */
  diaPago: number;
  /** true: el mes de la venta cuenta como la primera mensualidad; false: la primera cae el mes siguiente. */
  primeraMensualidadMesVenta: boolean;
  notas: string | null;
  /** Contrato firmado (y desde cuándo): sin él no se puede entregar la comisión de una venta nueva. */
  contratoFirmado: boolean;
  contratoFechaFirma: string | null;
  /** Cómo se firmó (solo con el contrato firmado) y enlace opcional a su carpeta de Drive. */
  contratoMedio: MedioFirma | null;
  contratoDriveUrl: string | null;
  fechaCreacion: string;
  /** Se calculan a partir de sus lotes y sus pagos (ver PagoVenta), nunca se capturan a mano. */
  precioVenta: number;
  totalAbonado: number;
  saldoPendiente: number;
  /** Nació de un precontrato (We Forms): su fecha es la del envío y administración la confirma. */
  fechaProvisional: boolean;
  /** Dinero con el que ya estaba apartado el lote y que aún no se aplica a la venta (null = nada pendiente). */
  depositoPendiente: number | null;
}

export interface VentaLoteItemRequest {
  loteId: number;
  precio: number;
}

export interface VentaCreateRequest {
  lotes: VentaLoteItemRequest[];
  clienteId: number;
  /** Opcional: número de venta escrito a mano; omitido = el siguiente (mayor + 1). Si está ocupado, el
   * servidor recorre +1 esa venta y las consecutivas que le siguen. */
  numero?: number | null;
  /** Copropietarios (máximo 4 además del principal). */
  copropietariosIds?: number[];
  // Exactamente uno de los dos (ver backend VentaService.resolverAsesor).
  usuarioAsesorId: number | null;
  asesorExternoId: number | null;
  formaPago: string;
  fechaVenta: string;
  mensualidad: number | null;
  plazoMeses: number | null;
  engancheLabel: string | null;
  enganche: number | null;
  diaPago: number;
  primeraMensualidadMesVenta: boolean;
  notas: string | null;
  marcarLoteVendido: boolean;
  /** Solo con el tipo de pago "Con aportaciones"; el backend las valida (ver VentaService). */
  aportaciones?: VentaAportacion[];
  /** Contrato firmado (opcional); con true, la fecha de firma es obligatoria. */
  contratoFirmado?: boolean;
  contratoFechaFirma?: string | null;
  /** Obligatorio con firmado = true. */
  contratoMedio?: MedioFirma | null;
  contratoDriveUrl?: string | null;
  /** Comisión del asesor en % del valor de la venta (omitido = 5; máximo 10). */
  comisionPorcentaje?: number | null;
  /** Copropietario de la venta (un segundo asesor, interno o externo) con su propia comisión en % (omitido = 5; máximo 10). */
  coasesorUsuarioId?: number | null;
  coasesorExternoId?: number | null;
  coasesorComisionPorcentaje?: number | null;
}

/** Igual que VentaCreateRequest pero sin lotes ni marcarLoteVendido: no toca qué lotes incluye la
 * venta, solo sus datos capturados. Temporal: el botón "Modificar venta" que usa esto se va a
 * quitar más adelante. */
export interface VentaUpdateRequest {
  /** true = la fecha de venta ya es la definitiva (quita la marca de "fecha provisional"). */
  confirmarFecha?: boolean;
  /** Opcional: si se manda, la venta se re-liga a ese cliente. */
  clienteId?: number | null;
  /** Omitido = sin cambios; con valor, la venta pasa a ese número (recorriendo las consecutivas si está ocupado). */
  numero?: number | null;
  /** Omitido = sin cambios; una lista (aunque vacía) reemplaza a los copropietarios. */
  copropietariosIds?: number[];
  usuarioAsesorId: number | null;
  asesorExternoId: number | null;
  formaPago: string;
  fechaVenta: string;
  mensualidad: number | null;
  plazoMeses: number | null;
  engancheLabel: string | null;
  enganche: number | null;
  diaPago: number;
  primeraMensualidadMesVenta: boolean;
  notas: string | null;
}

/** Para qué es una línea de un recibo. Los abonos anteriores a que existieran los tipos no lo
 * tienen (null): se completan a mano. */
export type TipoAbono = 'MENSUALIDAD' | 'ENGANCHE' | 'APARTADO' | 'APORTACION';

export const TIPO_ABONO_LABELS: Record<TipoAbono, string> = {
  MENSUALIDAD: 'Mensualidad',
  ENGANCHE: 'Enganche',
  APARTADO: 'Apartado',
  APORTACION: 'Aportación',
};

export interface MesCubierto {
  anio: number;
  mes: number;
}

/** Una línea de un recibo de abono de una venta. Las líneas del mismo recibo comparten reciboId,
 * folio, fecha y nota. */
export interface PagoVenta {
  id: number;
  reciboId: number;
  fecha: string;
  monto: number;
  /** null en los abonos anteriores a que se pidiera: por completar. */
  tipo: TipoAbono | null;
  /** Solo MENSUALIDAD: los meses que cubre. */
  meses: MesCubierto[];
  /** Solo APORTACION: la aportación programada que paga. */
  aportacionId: number | null;
  /** null en los abonos anteriores a que se pidiera el folio: por completar. */
  folio: string | null;
  notas: string | null;
  registradoPor: UsuarioResumen;
  fechaCreacion: string;
  /** Recibo anulado: no cuenta en el saldo; se muestra con su motivo, quién y cuándo. */
  anulado: boolean;
  anuladoMotivo: string | null;
  anuladoPor: UsuarioResumen | null;
  anuladoFecha: string | null;
}

/** Una línea de la bitácora de un recibo (quién, qué, cuándo y el detalle de lo que cambió). */
export interface ReciboHistorial {
  reciboId: number;
  folio: string | null;
  accion: 'CREADO' | 'EDITADO' | 'ANULADO';
  usuario: string;
  fechaHora: string;
  detalle: string | null;
}

/** Edita solo lo no financiero de un recibo: el monto y la fecha no cambian (si están mal, se anula y se registra otro). */
export interface ReciboEditarRequest {
  folio: string;
  notas: string | null;
  lineas: { pagoId: number; tipo: TipoAbono; meses?: MesCubierto[]; aportacionId?: number | null }[];
}

export interface LineaReciboRequest {
  tipo: TipoAbono;
  monto: number;
  /** Solo MENSUALIDAD: al menos un mes. */
  meses?: MesCubierto[];
  /** Solo APORTACION. */
  aportacionId?: number | null;
}

/** Un recibo: folio, fecha, nota y una o más líneas (un concepto cada una). */
export interface PagoVentaCreateRequest {
  fecha: string;
  folio: string;
  notas: string | null;
  lineas: LineaReciboRequest[];
}

/** Respuesta de /ventas/numeracion: siguiente número automático y, si el consultado está ocupado, quién lo
 * tiene y cuántas ventas se recorrerían +1 (esa y las consecutivas que le siguen). */
export interface NumeracionVenta {
  siguiente: number;
  ocupanteId: number | null;
  ocupanteCliente: string | null;
  ocupanteFecha: string | null;
  ventasARecorrer: number;
}

export type AplicarDepositoA = 'SALDO' | 'MENSUALIDAD';

/** Cómo aplicar el depósito pendiente; el folio es obligatorio con SALDO. */
export interface AplicarDepositoRequest {
  aplicarA: AplicarDepositoA;
  folio?: string;
}
