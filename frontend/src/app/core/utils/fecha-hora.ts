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
export const HORA_POR_DEFECTO = '09:00';

/** Combina una fecha ("2026-09-04") y una hora ("10:00") en un ISO string local, sin desfase de zona horaria. */
export function combinarFechaHora(fecha: string, hora: string): string {
  return new Date(`${fecha}T${hora || HORA_POR_DEFECTO}:00`).toISOString();
}

/** Etiqueta corta "10:00 am" a partir de una hora "HH:mm" en formato 24h. */
export function formatearHora12h(hora: string): string {
  const [h, m] = hora.split(':').map(Number);
  const periodo = h < 12 ? 'a. m.' : 'p. m.';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${String(m).padStart(2, '0')} ${periodo}`;
}

export function etiquetaDuracion(minutos: number): string {
  return DURACION_OPCIONES.find((o) => o.valor === minutos)?.etiqueta ?? `${minutos} min`;
}
