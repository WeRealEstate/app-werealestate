import { EstadisticasForm, FiltroRespuestas, WeFormRespuesta } from '../models/we-form.model';
import { descargarBlob, descargarExcel } from './excel';

const fechaHora = (iso: string): string =>
  new Date(iso).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' });

const nombreSeguro = (nombre: string): string =>
  nombre.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase() || 'formulario';

/** Texto del filtro aplicado, para el encabezado del PDF. */
export function describirFiltro(filtro: FiltroRespuestas): string {
  const partes: string[] = [];
  if (filtro.desde) partes.push(`desde ${filtro.desde}`);
  if (filtro.hasta) partes.push(`hasta ${filtro.hasta}`);
  if (filtro.asesor?.trim()) partes.push(`asesor "${filtro.asesor.trim()}"`);
  return partes.length ? partes.join(' · ') : 'Sin filtros';
}

/** Una fila por respuesta y una columna por pregunta (en el orden en que aparecen). */
export async function exportarRespuestasExcel(nombreForm: string, respuestas: WeFormRespuesta[]): Promise<void> {
  const preguntas: string[] = [];
  for (const r of respuestas) {
    for (const i of r.items) {
      if (i.tipo !== 'TITULO' && !preguntas.includes(i.pregunta)) preguntas.push(i.pregunta);
    }
  }
  const encabezados: Record<string, string> = {
    fecha: 'Fecha de envío',
    asesor: 'Asesor',
    cliente: 'Cliente',
    clienteNuevo: 'Cliente nuevo / existente',
    venta: 'Venta',
  };
  preguntas.forEach((p, idx) => (encabezados['p' + idx] = p));
  const filas = respuestas.map((r) => {
    const fila: Record<string, unknown> = {
      fecha: fechaHora(r.fechaEnvio),
      asesor: r.asesorNombre,
      cliente: r.clienteNombre ?? '',
      clienteNuevo: r.clienteNuevo ? 'Nuevo' : 'Existente',
      venta: r.ventaNumero ?? '',
    };
    preguntas.forEach((p, idx) => {
      const item = r.items.find((i) => i.pregunta === p);
      fila['p' + idx] = item ? (item.tieneArchivo ? `Archivo (${item.valor ?? ''})` : (item.valor ?? '')) : '';
    });
    return fila;
  });
  await descargarExcel(`${nombreSeguro(nombreForm)}-respuestas.xlsx`, encabezados, filas, 'Respuestas');
}

/** Reporte PDF: totales, por día, por asesor, resumen de cada pregunta y la lista de respuestas. */
export async function exportarReportePdf(
  nombreForm: string,
  filtro: FiltroRespuestas,
  stats: EstadisticasForm,
  respuestas: WeFormRespuesta[],
): Promise<void> {
  const [{ jsPDF }, { default: autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
  const doc = new jsPDF({ orientation: 'portrait', unit: 'pt', format: 'a4' });
  const margen = 40;
  const cabecera = { fillColor: [36, 63, 184] as [number, number, number] };
  let y = margen;

  doc.setFontSize(16);
  doc.text(nombreForm, margen, y);
  y += 18;
  doc.setFontSize(9);
  doc.setTextColor(110);
  doc.text(`${describirFiltro(filtro)} · generado ${fechaHora(new Date().toISOString())}`, margen, y);
  doc.setTextColor(0);
  y += 10;

  const tabla = (titulo: string, cabeceras: string[], filas: (string | number)[][]): void => {
    if (filas.length === 0) return;
    if (y > 740) {
      doc.addPage();
      y = margen;
    }
    doc.setFontSize(11);
    doc.text(titulo, margen, y + 12);
    autoTable(doc, {
      startY: y + 18,
      head: [cabeceras],
      body: filas,
      headStyles: cabecera,
      styles: { fontSize: 8, cellPadding: 3 },
      margin: { left: margen, right: margen },
    });
    y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 14;
  };

  tabla('Resumen', ['Total de respuestas', 'Clientes nuevos', 'Clientes existentes'], [
    [stats.total, stats.clientesNuevos, stats.clientesExistentes],
  ]);
  tabla('Respuestas por día', ['Día', 'Respuestas'], stats.porDia.map((c) => [c.etiqueta, c.cantidad]));
  tabla('Respuestas por asesor', ['Asesor', 'Respuestas'], stats.porAsesor.map((c) => [c.etiqueta, c.cantidad]));

  for (const p of stats.preguntas) {
    if (p.opciones.length > 0) {
      const total = p.opciones.reduce((s, o) => s + o.cantidad, 0) || 1;
      tabla(
        `${p.titulo} (${p.respondidas} respuestas)`,
        ['Opción', 'Cantidad', '%'],
        p.opciones.map((o) => [o.etiqueta, o.cantidad, `${Math.round((o.cantidad / total) * 100)}%`]),
      );
    } else if (p.minimo !== null) {
      tabla(`${p.titulo} (${p.respondidas} respuestas)`, ['Mínimo', 'Máximo', 'Promedio'], [[p.minimo, p.maximo ?? '', p.promedio ?? '']]);
    }
  }

  tabla(
    'Respuestas recibidas',
    ['Fecha', 'Asesor', 'Cliente', 'Tipo', 'Venta'],
    respuestas.map((r) => [
      fechaHora(r.fechaEnvio),
      r.asesorNombre,
      r.clienteNombre ?? '',
      r.clienteNuevo ? 'Nuevo' : 'Existente',
      r.ventaNumero ? `#${r.ventaNumero}` : '',
    ]),
  );

  descargarBlob(`${nombreSeguro(nombreForm)}-reporte.pdf`, doc.output('blob'));
}
