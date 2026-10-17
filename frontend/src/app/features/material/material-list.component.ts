import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CATEGORIAS_MATERIAL,
  CategoriaMaterial,
  MAX_ARCHIVO_MATERIAL_MB,
  MaterialItem,
  PROYECTOS_MATERIAL,
  ProyectoMaterial,
  etiquetaCategoria,
  etiquetaProyecto,
  tamanoLegible,
} from '../../core/models/material.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { MaterialService } from '../../core/services/material.service';
import { ToastService } from '../../core/services/toast.service';
import { descargarBlob } from '../../core/utils/descargar-blob';
import { MaterialMiniaturaComponent } from '../../shared/material-miniatura/material-miniatura.component';
import { MaterialVisorComponent } from '../../shared/material-visor/material-visor.component';
import { WeLoaderComponent } from '../../shared/we-loader/we-loader.component';

/** Material de venta (módulo Material): administración sube copys, flyers, brochures y videos que los asesores
 * consultan desde el plano público de su desarrollo. */
@Component({
  selector: 'app-material-list',
  standalone: true,
  imports: [FormsModule, WeLoaderComponent, MaterialMiniaturaComponent, MaterialVisorComponent],
  templateUrl: './material-list.component.html',
})
export class MaterialListComponent {
  private readonly service = inject(MaterialService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly categorias = CATEGORIAS_MATERIAL;
  readonly proyectos = PROYECTOS_MATERIAL;
  readonly maxMb = MAX_ARCHIVO_MATERIAL_MB;
  readonly etiquetaCategoria = etiquetaCategoria;
  readonly etiquetaProyecto = etiquetaProyecto;
  readonly tamanoLegible = tamanoLegible;

  readonly items = signal<MaterialItem[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly filtroProyecto = signal<ProyectoMaterial | ''>('');
  readonly filtroCategoria = signal<CategoriaMaterial | ''>('');
  readonly visibles = computed(() =>
    this.items().filter(
      (m) =>
        (!this.filtroProyecto() || m.proyecto === this.filtroProyecto() || m.proyecto === 'AMBOS') &&
        (!this.filtroCategoria() || m.categoria === this.filtroCategoria()),
    ),
  );

  // Formulario (crear o editar)
  readonly mostrarForm = signal(false);
  readonly editandoId = signal<number | null>(null);
  readonly archivoActual = signal<MaterialItem | null>(null);
  readonly titulo = signal('');
  readonly descripcion = signal('');
  readonly categoria = signal<CategoriaMaterial>('FLYER');
  readonly proyecto = signal<ProyectoMaterial>('AMBOS');
  readonly texto = signal('');
  readonly archivo = signal<File | null>(null);
  readonly quitarArchivo = signal(false);
  readonly guardando = signal(false);
  readonly errorForm = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.items.set(await this.service.listar());
    } catch {
      this.errorMessage.set('No se pudo cargar el material. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  abrirNuevo(): void {
    this.editandoId.set(null);
    this.archivoActual.set(null);
    this.titulo.set('');
    this.descripcion.set('');
    this.categoria.set('FLYER');
    this.proyecto.set('AMBOS');
    this.texto.set('');
    this.archivo.set(null);
    this.quitarArchivo.set(false);
    this.errorForm.set(null);
    this.mostrarForm.set(true);
  }

  abrirEditar(m: MaterialItem): void {
    this.editandoId.set(m.id);
    this.archivoActual.set(m.archivoExtension ? m : null);
    this.titulo.set(m.titulo);
    this.descripcion.set(m.descripcion ?? '');
    this.categoria.set(m.categoria);
    this.proyecto.set(m.proyecto);
    this.texto.set(m.texto ?? '');
    this.archivo.set(null);
    this.quitarArchivo.set(false);
    this.errorForm.set(null);
    this.mostrarForm.set(true);
  }

  elegirArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (f && f.size > this.maxMb * 1024 * 1024) {
      this.errorForm.set(`El archivo pesa ${tamanoLegible(f.size)}; el máximo es ${this.maxMb} MB.`);
      input.value = '';
      this.archivo.set(null);
      return;
    }
    this.errorForm.set(null);
    this.archivo.set(f);
    if (f) this.quitarArchivo.set(false);
  }

  async guardar(): Promise<void> {
    if (this.guardando()) return;
    if (!this.titulo().trim()) {
      this.errorForm.set('El título es obligatorio.');
      return;
    }
    const quedaArchivo = !!this.archivo() || (!!this.archivoActual() && !this.quitarArchivo());
    if (!this.texto().trim() && !quedaArchivo) {
      this.errorForm.set('Agrega un texto, un archivo o ambos.');
      return;
    }
    this.guardando.set(true);
    this.errorForm.set(null);
    const form = {
      titulo: this.titulo(),
      descripcion: this.descripcion(),
      categoria: this.categoria(),
      proyecto: this.proyecto(),
      texto: this.texto(),
      archivo: this.archivo(),
      quitarArchivo: this.quitarArchivo(),
    };
    try {
      const id = this.editandoId();
      const guardado = id == null ? await this.service.crear(form) : await this.service.actualizar(id, form);
      this.items.update((lista) =>
        id == null ? [guardado, ...lista] : lista.map((m) => (m.id === guardado.id ? guardado : m)),
      );
      this.mostrarForm.set(false);
      this.toast.success(id == null ? 'Material agregado.' : 'Material actualizado.');
    } catch (e) {
      const mensaje = e instanceof HttpErrorResponse ? e.error?.message : null;
      this.errorForm.set(typeof mensaje === 'string' ? mensaje : 'No se pudo guardar. Intenta de nuevo.');
    } finally {
      this.guardando.set(false);
    }
  }

  async eliminar(m: MaterialItem): Promise<void> {
    const ok = await this.confirmService.confirm({
      titulo: 'Eliminar material',
      mensaje: `Se eliminará "${m.titulo}" y los asesores dejarán de verlo. Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!ok) return;
    try {
      await this.service.eliminar(m.id);
      this.items.update((lista) => lista.filter((x) => x.id !== m.id));
      this.toast.success('Material eliminado.');
    } catch {
      this.toast.error('No se pudo eliminar. Intenta de nuevo.');
    }
  }

  /** Posición en la lista visible del material que se está viendo en el visor (null = visor cerrado). */
  readonly visorIndice = signal<number | null>(null);
  readonly cargarArchivo = (m: MaterialItem): Promise<Blob> => this.service.archivo(m.id);
  readonly cargarMiniatura = (m: MaterialItem): Promise<Blob> => this.service.miniatura(m.id);

  abrirVisor(m: MaterialItem): void {
    const i = this.visibles().findIndex((x) => x.id === m.id);
    if (i >= 0) this.visorIndice.set(i);
  }

  async descargar(m: MaterialItem): Promise<void> {
    try {
      descargarBlob(await this.service.archivo(m.id), m.archivoNombre ?? 'material');
    } catch {
      this.toast.error('No se pudo descargar el archivo.');
    }
  }

  async copiarTexto(m: MaterialItem): Promise<void> {
    try {
      await navigator.clipboard.writeText(m.texto ?? '');
      this.toast.success('Texto copiado.');
    } catch {
      this.toast.error('No se pudo copiar el texto.');
    }
  }
}
