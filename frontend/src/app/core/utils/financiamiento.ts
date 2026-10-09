/** Cálculos de lectura del financiamiento de una venta (los mismos meses que usa el calendario de
 * cobros de FinanzasService: la primera mensualidad cae en el mes de la venta si
 * `primeraMesVenta`, si no en el siguiente; el día de pago se recorta en meses más cortos). */

/** Fecha de la última mensualidad; null si falta el plazo o la fecha. */
export function ultimaMensualidad(
  fechaVentaIso: string | null | undefined,
  plazoMeses: number | null | undefined,
  diaPago: number | null | undefined,
  primeraMesVenta: boolean,
): Date | null {
  if (!fechaVentaIso || !plazoMeses || plazoMeses < 1 || !diaPago) return null;
  const [anio, mes] = fechaVentaIso.split('-').map(Number);
  if (!anio || !mes) return null;
  const indice = (mes - 1) + (primeraMesVenta ? 0 : 1) + (plazoMeses - 1);
  const ultimoAnio = anio + Math.floor(indice / 12);
  const ultimoMes = indice % 12;
  const diasDelMes = new Date(ultimoAnio, ultimoMes + 1, 0).getDate();
  return new Date(ultimoAnio, ultimoMes, Math.min(diaPago, diasDelMes));
}

/** "15 de marzo de 2028" */
export function fechaLarga(fecha: Date): string {
  return fecha.toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Porcentaje (2 decimales) que representa `parte` del `total`; null si no se puede calcular. */
export function porcentajeDe(parte: number | null | undefined, total: number | null | undefined): number | null {
  if (!parte || !total || total <= 0) return null;
  return Math.round((parte / total) * 10000) / 100;
}
