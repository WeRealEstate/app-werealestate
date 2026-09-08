import * as XLSX from 'xlsx';

/** Genera un archivo .xlsx (una sola hoja, con encabezados) a partir de filas de objetos y dispara la descarga en el navegador. */
export function descargarExcel<T extends Record<string, unknown>>(
  nombreArchivo: string,
  encabezados: Record<keyof T, string>,
  filas: T[],
  nombreHoja = 'Datos',
): void {
  const claves = Object.keys(encabezados) as (keyof T)[];
  const datos = [
    claves.map((clave) => encabezados[clave]),
    ...filas.map((fila) => claves.map((clave) => fila[clave] ?? '')),
  ];

  const hoja = XLSX.utils.aoa_to_sheet(datos);
  const libro = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(libro, hoja, nombreHoja);
  XLSX.writeFile(libro, nombreArchivo);
}
