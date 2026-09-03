/** Opciones de duración para agendar un próximo seguimiento, como un evento de calendario. */
export const DURACION_OPCIONES: { valor: number; etiqueta: string }[] = [
  { valor: 15, etiqueta: '15 min' },
  { valor: 30, etiqueta: '30 min' },
  { valor: 45, etiqueta: '45 min' },
  { valor: 60, etiqueta: '1 hora' },
  { valor: 90, etiqueta: '1 hora 30 min' },
  { valor: 120, etiqueta: '2 horas' },
];

export const DURACION_POR_DEFECTO = 30;

/** Horas del día en formato 24h ("00".."23"): un selector explícito no deja lugar a confundir am/pm. */
export const HORAS_OPCIONES: string[] = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'));
export const MINUTOS_OPCIONES: string[] = ['00', '15', '30', '45'];

export const HORA_POR_DEFECTO = '09';
export const MINUTO_POR_DEFECTO = '00';

/**
 * Combina una fecha ("2026-09-04"), una hora ("14") y un minuto ("00") en el mismo formato de
 * texto que espera el backend (LocalDateTime), SIN pasar por `Date`/`toISOString()`: eso
 * reinterpreta la hora como si fuera UTC y la desfasa varias horas al guardarla (p. ej. 2pm
 * termina guardado como 8pm). Al armar el texto tal cual, la hora que se ve es la que se guarda.
 */
export function combinarFechaHora(fecha: string, hora: string, minuto: string): string {
  return `${fecha}T${hora || HORA_POR_DEFECTO}:${minuto || MINUTO_POR_DEFECTO}:00`;
}

export function etiquetaDuracion(minutos: number): string {
  return DURACION_OPCIONES.find((o) => o.valor === minutos)?.etiqueta ?? `${minutos} min`;
}
