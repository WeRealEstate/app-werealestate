/** Escapa un valor para una celda CSV (RFC 4180): comillas dobles y envuelve si contiene coma, comilla o salto de línea. */
function escapeCsvCell(valor: unknown): string {
  const texto = valor === null || valor === undefined ? '' : String(valor);
  if (/[",\n]/.test(texto)) {
    return `"${texto.replace(/"/g, '""')}"`;
  }
  return texto;
}

/** Genera un CSV (con encabezados) a partir de filas de objetos y dispara la descarga en el navegador. */
export function descargarCsv<T extends Record<string, unknown>>(
  nombreArchivo: string,
  encabezados: Record<keyof T, string>,
  filas: T[],
): void {
  const claves = Object.keys(encabezados) as (keyof T)[];
  const lineas = [
    claves.map((clave) => escapeCsvCell(encabezados[clave])).join(','),
    ...filas.map((fila) => claves.map((clave) => escapeCsvCell(fila[clave])).join(',')),
  ];

  const bom = '﻿';
  const blob = new Blob([bom + lineas.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);

  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}
