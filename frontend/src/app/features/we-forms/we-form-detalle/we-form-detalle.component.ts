import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ConteoForm,
  DestinoPregunta,
  EstadisticasForm,
  FiltroRespuestas,
  ResumenPregunta,
  TIPO_PREGUNTA_ETIQUETA,
  TIPOS_PREGUNTA_PROPIOS,
  TipoPreguntaForm,
  WeFormDetalle,
  WeFormPregunta,
  WeFormRespuesta,
} from '../../../core/models/we-form.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ToastService } from '../../../core/services/toast.service';
import { WeFormsService } from '../../../core/services/we-forms.service';
import { exportarReportePdf, exportarRespuestasExcel } from '../../../core/utils/we-forms-export';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';

/** A qué campo alimenta cada pregunta predefinida, en palabras. */
const DESTINO_ETIQUETA: Record<DestinoPregunta, string> = {
  NOMBRE: 'Cliente · nombre', APELLIDO_PATERNO: 'Cliente · apellido paterno', APELLIDO_MATERNO: 'Cliente · apellido materno',
  FECHA_NACIMIENTO: 'Cliente · fecha de nacimiento', CURP: 'Cliente · CURP', TELEFONO: 'Cliente · teléfono',
  CORREO: 'Cliente · correo', RFC: 'Cliente · RFC', CALLE: 'Cliente · calle', COLONIA: 'Cliente · colonia',
  MUNICIPIO: 'Cliente · municipio', ESTADO: 'Cliente · estado', CODIGO_POSTAL: 'Cliente · código postal',
  LOTES: 'Venta · lotes', TIPO_PAGO: 'Venta · tipo de pago', ENGANCHE: 'Venta · enganche', PLAZO: 'Venta · plazo',
  DIA_PAGO: 'Venta · día de pago', APORTACIONES: 'Venta · aportaciones', ASESOR: 'Venta · asesor',
};

const PALETA = ['#243fb8', '#16a34a', '#f59e0b', '#dc2626', '#0ea5e9', '#9333ea', '#db2777', '#64748b'];

/** Un formulario de We Forms: su constructor de preguntas y las respuestas recibidas. */
@Component({
  selector: 'app-we-form-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DragDropModule, WeLoaderComponent],
  templateUrl: './we-form-detalle.component.html',
})
export class WeFormDetalleComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(WeFormsService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly formId = Number(this.route.snapshot.paramMap.get('id'));
  readonly form = signal<WeFormDetalle | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly pestana = signal<'preguntas' | 'respuestas' | 'estadisticas'>('preguntas');

  readonly tiposPropios = TIPOS_PREGUNTA_PROPIOS;
  readonly tipoEtiqueta = TIPO_PREGUNTA_ETIQUETA;
  readonly destinoEtiqueta = DESTINO_ETIQUETA;

  // --- Encabezado (nombre, descripción, estado) ---
  readonly editandoEncabezado = signal(false);
  readonly encNombre = signal('');
  readonly encDescripcion = signal('');

  // --- Editor de una pregunta ---
  readonly editandoId = signal<number | null>(null);
  readonly eTitulo = signal('');
  readonly eDescripcion = signal('');
  readonly eObligatoria = signal(false);
  readonly eVisible = signal(true);
  readonly eOpciones = signal('');
  readonly guardandoPregunta = signal(false);
  readonly nuevoTipo = signal<TipoPreguntaForm>('CORTA');

  // --- Respuestas ---
  readonly respuestas = signal<WeFormRespuesta[]>([]);
  readonly hayMas = signal(false);
  readonly cargandoRespuestas = signal(false);
  readonly respuestasCargadas = signal(false);
  readonly expandidas = signal<Set<number>>(new Set());
  private paginaRespuestas = 0;

  // --- Filtros (comparten Respuestas, Estadísticas y la exportación) ---
  readonly fDesde = signal('');
  readonly fHasta = signal('');
  readonly fAsesor = signal('');
  readonly exportando = signal<'excel' | 'pdf' | null>(null);

  // --- Estadísticas ---
  readonly stats = signal<EstadisticasForm | null>(null);
  readonly cargandoStats = signal(false);
  readonly colores = PALETA;

  readonly filtro = computed<FiltroRespuestas>(() => ({
    desde: this.fDesde() || undefined,
    hasta: this.fHasta() || undefined,
    asesor: this.fAsesor() || undefined,
  }));

  readonly linkPublico = computed(() => {
    const f = this.form();
    return f ? `${window.location.origin}/we-forms/${f.slug}` : '';
  });

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.form.set(await this.service.obtener(this.formId));
    } catch {
      this.errorMessage.set('No se pudo cargar el formulario.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private mensajeDe(error: unknown, defecto: string): string {
    return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : defecto;
  }

  async copiarLink(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.linkPublico());
      this.toast.success('Link copiado.');
    } catch {
      this.toast.error('No se pudo copiar. Cópialo a mano: ' + this.linkPublico());
    }
  }

  // ------------------------------------------------------------------ encabezado y estado

  abrirEncabezado(): void {
    const f = this.form();
    if (!f) return;
    this.encNombre.set(f.nombre);
    this.encDescripcion.set(f.descripcion ?? '');
    this.editandoEncabezado.set(true);
  }

  async guardarEncabezado(): Promise<void> {
    const f = this.form();
    const nombre = this.encNombre().trim();
    if (!f || !nombre) return;
    try {
      this.form.set(await this.service.actualizar(f.id, { nombre, descripcion: this.encDescripcion().trim() || null, estado: f.estado }));
      this.editandoEncabezado.set(false);
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo guardar.'));
    }
  }

  async alternarEstado(): Promise<void> {
    const f = this.form();
    if (!f) return;
    const nuevo = f.estado === 'ACTIVO' ? 'CERRADO' : 'ACTIVO';
    if (nuevo === 'CERRADO') {
      const ok = await this.confirm.confirm({
        titulo: 'Cerrar formulario',
        mensaje: 'El link dejará de recibir respuestas y no aparecerá en el plano. Puedes volver a activarlo cuando quieras.',
        textoConfirmar: 'Cerrar',
      });
      if (!ok) return;
    }
    try {
      this.form.set(await this.service.actualizar(f.id, { nombre: f.nombre, descripcion: f.descripcion, estado: nuevo }));
      this.toast.success(nuevo === 'ACTIVO' ? 'Formulario activo.' : 'Formulario cerrado.');
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo cambiar el estado.'));
    }
  }

  // ------------------------------------------------------------------ preguntas

  esConOpciones(tipo: TipoPreguntaForm): boolean {
    return tipo === 'OPCION_MULTIPLE' || tipo === 'CASILLAS' || tipo === 'DESPLEGABLE';
  }

  abrirEditor(p: WeFormPregunta): void {
    this.editandoId.set(p.id);
    this.eTitulo.set(p.titulo);
    this.eDescripcion.set(p.descripcion ?? '');
    this.eObligatoria.set(p.obligatoria || p.siempreObligatoria);
    this.eVisible.set(p.visible);
    this.eOpciones.set(p.opciones.join('\n'));
  }

  cerrarEditor(): void {
    this.editandoId.set(null);
  }

  /** Si el cambio de visible/obligatoria está permitido para esta pregunta. */
  puedeCambiarObligatoria(p: WeFormPregunta): boolean {
    return p.tipo !== 'TITULO' && (!p.fija || p.opcionalEditable);
  }

  puedeCambiarVisible(p: WeFormPregunta): boolean {
    return !p.fija || p.opcionalEditable;
  }

  async guardarPregunta(p: WeFormPregunta): Promise<void> {
    const titulo = this.eTitulo().trim();
    if (!titulo || this.guardandoPregunta()) return;
    this.guardandoPregunta.set(true);
    try {
      const actualizada = await this.service.editarPregunta(this.formId, p.id, {
        titulo,
        descripcion: this.eDescripcion().trim() || null,
        obligatoria: this.eObligatoria(),
        opciones: this.eOpciones().split('\n').map((o) => o.trim()).filter((o) => o),
        visible: this.eVisible(),
      });
      this.reemplazar(actualizada);
      this.editandoId.set(null);
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo guardar la pregunta.'));
    } finally {
      this.guardandoPregunta.set(false);
    }
  }

  async agregarPregunta(): Promise<void> {
    const tipo = this.nuevoTipo();
    try {
      const nueva = await this.service.crearPregunta(this.formId, {
        tipo,
        titulo: tipo === 'TITULO' ? 'Sección sin título' : 'Pregunta sin título',
        descripcion: null,
        obligatoria: false,
        opciones: this.esConOpciones(tipo) ? ['Opción 1', 'Opción 2'] : [],
        visible: true,
      });
      this.form.update((f) => (f ? { ...f, preguntas: [...f.preguntas, nueva] } : f));
      this.abrirEditor(nueva);
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo agregar la pregunta.'));
    }
  }

  async archivar(p: WeFormPregunta): Promise<void> {
    const ok = await this.confirm.confirm({
      titulo: 'Quitar pregunta',
      mensaje: 'La pregunta deja de verse en el formulario. Las respuestas que ya se recibieron se conservan tal como se contestaron.',
      textoConfirmar: 'Quitar',
      peligroso: true,
    });
    if (!ok) return;
    try {
      await this.service.archivarPregunta(this.formId, p.id);
      this.form.update((f) => (f ? { ...f, preguntas: f.preguntas.filter((x) => x.id !== p.id) } : f));
      this.editandoId.set(null);
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo quitar la pregunta.'));
    }
  }

  async soltar(event: CdkDragDrop<WeFormPregunta[]>): Promise<void> {
    const f = this.form();
    if (!f || event.previousIndex === event.currentIndex) return;
    const preguntas = [...f.preguntas];
    moveItemInArray(preguntas, event.previousIndex, event.currentIndex);
    this.form.set({ ...f, preguntas });
    try {
      await this.service.reordenar(this.formId, preguntas.map((p) => p.id));
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo guardar el orden.'));
      await this.cargar();
    }
  }

  private reemplazar(p: WeFormPregunta): void {
    this.form.update((f) => (f ? { ...f, preguntas: f.preguntas.map((x) => (x.id === p.id ? p : x)) } : f));
  }

  // ------------------------------------------------------------------ respuestas

  async abrirRespuestas(): Promise<void> {
    this.pestana.set('respuestas');
    if (this.respuestasCargadas()) return;
    await this.cargarRespuestas(true);
  }

  async cargarRespuestas(desdeInicio: boolean): Promise<void> {
    if (this.cargandoRespuestas()) return;
    if (desdeInicio) this.paginaRespuestas = 0;
    this.cargandoRespuestas.set(true);
    try {
      const pagina = await this.service.respuestas(this.formId, this.paginaRespuestas, this.filtro());
      this.respuestas.update((r) => (desdeInicio ? pagina.contenido : [...r, ...pagina.contenido]));
      this.hayMas.set(pagina.hayMas);
      this.paginaRespuestas++;
      this.respuestasCargadas.set(true);
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudieron cargar las respuestas.'));
    } finally {
      this.cargandoRespuestas.set(false);
    }
  }

  async abrirEstadisticas(): Promise<void> {
    this.pestana.set('estadisticas');
    if (!this.stats()) await this.cargarEstadisticas();
  }

  async cargarEstadisticas(): Promise<void> {
    this.cargandoStats.set(true);
    try {
      this.stats.set(await this.service.estadisticas(this.formId, this.filtro()));
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudieron cargar las estadísticas.'));
    } finally {
      this.cargandoStats.set(false);
    }
  }

  /** Recarga la pestaña visible con los filtros actuales. */
  async aplicarFiltros(): Promise<void> {
    this.respuestasCargadas.set(false);
    this.stats.set(null);
    if (this.pestana() === 'respuestas') await this.cargarRespuestas(true);
    else if (this.pestana() === 'estadisticas') await this.cargarEstadisticas();
  }

  async limpiarFiltros(): Promise<void> {
    this.fDesde.set('');
    this.fHasta.set('');
    this.fAsesor.set('');
    await this.aplicarFiltros();
  }

  async exportar(formato: 'excel' | 'pdf'): Promise<void> {
    const f = this.form();
    if (!f || this.exportando()) return;
    this.exportando.set(formato);
    try {
      const respuestas = await this.service.exportar(this.formId, this.filtro());
      if (respuestas.length === 0) {
        this.toast.error('No hay respuestas con esos filtros para exportar.');
        return;
      }
      if (formato === 'excel') {
        await exportarRespuestasExcel(f.nombre, respuestas);
      } else {
        const stats = await this.service.estadisticas(this.formId, this.filtro());
        await exportarReportePdf(f.nombre, this.filtro(), stats, respuestas);
      }
    } catch (error) {
      this.toast.error(this.mensajeDe(error, 'No se pudo exportar.'));
    } finally {
      this.exportando.set(null);
    }
  }

  // --- Gráficas ---

  /** Pregunta cuyo resumen se dibuja como pastel (una sola opción por respuesta). */
  esPastel(p: ResumenPregunta): boolean {
    return (p.tipo === 'OPCION_MULTIPLE' || p.tipo === 'DESPLEGABLE' || p.tipo === 'TIPO_PAGO') && p.opciones.length <= 8;
  }

  porcentaje(o: ConteoForm, lista: ConteoForm[]): number {
    const total = lista.reduce((s, x) => s + x.cantidad, 0);
    return total ? Math.round((o.cantidad / total) * 100) : 0;
  }

  /** Fondo CSS del pastel (conic-gradient) para una lista de opciones. */
  pastel(lista: ConteoForm[]): string {
    const total = lista.reduce((s, x) => s + x.cantidad, 0) || 1;
    let acumulado = 0;
    const tramos = lista.map((o, idx) => {
      const ini = (acumulado / total) * 100;
      acumulado += o.cantidad;
      return `${PALETA[idx % PALETA.length]} ${ini}% ${(acumulado / total) * 100}%`;
    });
    return `conic-gradient(${tramos.join(', ')})`;
  }

  maximo(lista: ConteoForm[]): number {
    return Math.max(1, ...lista.map((o) => o.cantidad));
  }

  alternarRespuesta(id: number): void {
    this.expandidas.update((s) => {
      const nuevo = new Set(s);
      if (nuevo.has(id)) nuevo.delete(id);
      else nuevo.add(id);
      return nuevo;
    });
  }

  async verArchivo(respuestaId: number, itemId: number): Promise<void> {
    try {
      await this.service.verArchivo(respuestaId, itemId);
    } catch {
      this.toast.error('No se pudo abrir el archivo.');
    }
  }

  fecha(iso: string): string {
    return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
  }
}
