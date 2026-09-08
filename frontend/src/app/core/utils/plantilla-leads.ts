import ExcelJS from 'exceljs';
import { descargarBlob, estilizarEncabezado } from './excel';

const COLOR_EJEMPLO = 'FFFFF3CD';
const COLOR_TEXTO_EJEMPLO = 'FF7A5C00';

const COLUMNAS: { header: string; key: string; width: number; nota: string }[] = [
  {
    header: 'Nombre del cliente',
    key: 'nombre',
    width: 30,
    nota: 'Obligatorio. Nombre completo o como se identificó al cliente.',
  },
  {
    header: 'Telefono',
    key: 'telefono',
    width: 16,
    nota: 'Obligatorio. Si no se conoce el número real del cliente, escribe 000000000 (nueve ceros).',
  },
  { header: 'Correo', key: 'correo', width: 26, nota: 'Opcional. Déjalo vacío si no se tiene.' },
  {
    header: 'Desarrollo',
    key: 'desarrollo',
    width: 20,
    nota: 'Opcional. Si lo dejas vacío, se asignará el desarrollo que elijas al importar el archivo.',
  },
  {
    header: 'Origen',
    key: 'origen',
    width: 26,
    nota: 'Opcional. De dónde vino el lead, por ejemplo: Campaña Facebook Junio 2026.',
  },
  {
    header: 'Notas',
    key: 'notas',
    width: 55,
    nota: 'Opcional. Cualquier nota o contexto ya levantado con el cliente. Se guardará como el primer seguimiento del lead.',
  },
];

const FILA_EJEMPLO = [
  'Juan Pérez Gómez',
  '9611234567',
  'juan.perez@correo.com',
  'SAMAI Campestre',
  'Campaña Facebook Junio 2026',
  'EJEMPLO — BORRA ESTA FILA. Interesado en terreno de 200m2, pidió información por WhatsApp.',
];

/** Genera y descarga la plantilla .xlsx para importar leads, con la lista de desarrollos disponibles como validación. */
export async function generarPlantillaLeads(desarrollos: string[]): Promise<void> {
  const workbook = new ExcelJS.Workbook();
  const hoja = workbook.addWorksheet('Leads');

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
  filaEjemplo.getCell('notas').alignment = { wrapText: true };

  const opcionesDesarrollo = desarrollos.length > 0 ? desarrollos : ['SAMAI Campestre', 'Aldea Nanuu', 'Otro'];
  const validacionDesarrollo: ExcelJS.DataValidation = {
    type: 'list',
    allowBlank: true,
    formulae: [`"${opcionesDesarrollo.join(',')}"`],
    error: 'Elige un desarrollo de la lista o deja la celda vacía.',
    errorTitle: 'Desarrollo inválido',
  };
  for (let fila = 2; fila <= 201; fila++) {
    hoja.getRow(fila).getCell('desarrollo').dataValidation = validacionDesarrollo;
  }

  hoja.getColumn('notas').alignment = { wrapText: true };

  const buffer = await workbook.xlsx.writeBuffer();
  descargarBlob(
    'plantilla_importar_leads.xlsx',
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
  );
}
