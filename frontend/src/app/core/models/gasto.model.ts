/** Categoría de gasto (ej. "Comisiones", "Renta de oficina") definida por un admin.
 * requiereTicket decide si al registrar un gasto de este tipo el comprobante es obligatorio. */
export interface TipoGasto {
  id: number;
  nombre: string;
  requiereTicket: boolean;
  activo: boolean;
}

export interface TipoGastoCreateRequest {
  nombre: string;
  requiereTicket: boolean;
}

export interface TipoGastoUpdateRequest {
  nombre: string;
  requiereTicket: boolean;
  activo: boolean;
}

/** fecha en formato ISO (yyyy-MM-dd). tieneTicket solo indica si hay un comprobante subido — el
 * archivo en sí se trae con GastosService.verTicket (autenticado, no es una URL pública). */
export interface Gasto {
  id: number;
  tipoGasto: TipoGasto;
  fecha: string;
  monto: number;
  tieneTicket: boolean;
  registradoPor: { id: number; nombre: string };
  fechaCreacion: string;
}
