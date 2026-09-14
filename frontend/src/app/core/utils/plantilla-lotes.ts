import ExcelJS from 'exceljs';
import { descargarBlob, estilizarEncabezado } from './excel';

const COLOR_EJEMPLO = 'FFFFF3CD';
const COLOR_TEXTO_EJEMPLO = 'FF7A5C00';

const COLUMNAS: { header: string; key: string; width: number; nota: string }[] = [
  { header: 'Desarrollo', key: 'desarrollo', width: 20, nota: 'Obligatorio. Debe coincidir con uno de la lista.' },
  { header: 'Manzana', key: 'manzana', width: 14, nota: 'Obligatorio. Ej. 5' },
  { header: 'Lote', key: 'lote', width: 14, nota: 'Obligatorio. Ej. 11' },
  { header: 'Superficie (ha)', key: 'superficie', width: 16, nota: 'Obligatorio. En hectáreas, ej. 0.25' },
  { header: 'Precio', key: 'precio', width: 16, nota: 'Opcional. Si se deja vacío, se calcula con el precio por m² del desarrollo.' },
];

const FILA_EJEMPLO = ['SAMAI Campestre', '5', '11', '0.25', ''];

/** Genera y descarga la plantilla .xlsx para importar lotes, con la lista de desarrollos disponibles como validación. */
export async function generarPlantillaLotes(desarrollos: string[]): Promise<void> {
  const workbook = new ExcelJS.Workbook();
  const hoja = workbook.addWorksheet('Lotes');

  hoja.columns = COLUMNAS.map((c) => ({ header: c.header, key: c.key, width: c.width }));
  estilizarEncabezado(hoja);
  COLUMNAS.forEach((c, i) => {
    hoja.getRow(1).getCell(i + 1).note = c.nota;
  });

  const filaEjemplo = hoja.addRow(FILA_EJEMPLO);
  filaEjemplo.eachCell((celda) => {
    celda.font = { name: 'Arial', italic: true, color: { argb: COLOR_TEXTO_EJEMPLO }, size: 10 };
    celda.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLOR_EJEMPLO } };
  });

  const opcionesDesarrollo = desarrollos.length > 0 ? desarrollos : ['SAMAI Campestre', 'Aldea Nanuu'];
  const validacionDesarrollo: ExcelJS.DataValidation = {
    type: 'list',
    allowBlank: false,
    formulae: [`"${opcionesDesarrollo.join(',')}"`],
    error: 'Elige un desarrollo de la lista.',
    errorTitle: 'Desarrollo inválido',
  };
  for (let fila = 2; fila <= 501; fila++) {
    hoja.getRow(fila).getCell('desarrollo').dataValidation = validacionDesarrollo;
  }

  const buffer = await workbook.xlsx.writeBuffer();
  descargarBlob(
    'plantilla_importar_lotes.xlsx',
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
  );
}
