import ExcelJS from 'exceljs';

const COLOR_ENCABEZADO = 'FF243FB8';
const COLOR_BORDE = 'FFD9D9D9';

const BORDE_CELDA: Partial<ExcelJS.Borders> = {
  top: { style: 'thin', color: { argb: COLOR_BORDE } },
  left: { style: 'thin', color: { argb: COLOR_BORDE } },
  bottom: { style: 'thin', color: { argb: COLOR_BORDE } },
  right: { style: 'thin', color: { argb: COLOR_BORDE } },
};

/** Dispara la descarga de un Blob en el navegador con el nombre de archivo dado. */
export function descargarBlob(nombreArchivo: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}

/** Da al encabezado (fila 1) de una hoja el estilo estándar de la app: fondo azul, texto blanco en negritas. */
export function estilizarEncabezado(hoja: ExcelJS.Worksheet): void {
  const fila = hoja.getRow(1);
  fila.eachCell((celda) => {
    celda.font = { bold: true, color: { argb: 'FFFFFFFF' }, name: 'Arial', size: 11 };
    celda.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLOR_ENCABEZADO } };
    celda.border = BORDE_CELDA;
    celda.alignment = { vertical: 'middle', wrapText: true };
  });
  fila.height = 20;
  hoja.views = [{ state: 'frozen', ySplit: 1 }];
}

async function descargarLibro(nombreArchivo: string, workbook: ExcelJS.Workbook): Promise<void> {
  const buffer = await workbook.xlsx.writeBuffer();
  descargarBlob(
    nombreArchivo,
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
  );
}

/** Genera un archivo .xlsx (una sola hoja, con encabezados estilizados) a partir de filas de objetos y dispara la descarga. */
export async function descargarExcel<T extends Record<string, unknown>>(
  nombreArchivo: string,
  encabezados: Record<keyof T, string>,
  filas: T[],
  nombreHoja = 'Datos',
): Promise<void> {
  const claves = Object.keys(encabezados) as (keyof T)[];
  const workbook = new ExcelJS.Workbook();
  const hoja = workbook.addWorksheet(nombreHoja);

  hoja.columns = claves.map((clave) => ({ header: encabezados[clave], key: String(clave), width: 20 }));
  for (const fila of filas) {
    hoja.addRow(claves.map((clave) => fila[clave] ?? ''));
  }

  estilizarEncabezado(hoja);
  await descargarLibro(nombreArchivo, workbook);
}
