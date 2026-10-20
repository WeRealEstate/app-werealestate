import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { FormsModule } from '@angular/forms';
import { LucideCopy, LucideDownload, LucideEllipsis, LucidePencil, LucidePlus, LucideTrash2, LucideX } from '@lucide/angular';
import {
  CATEGORIAS_MATERIAL,
  CategoriaMaterial,
  MAX_ARCHIVO_MATERIAL_MB,
  MAX_IMAGEN_GALERIA_MB,
  MAX_IMAGENES_GALERIA,
  MaterialImagen,
  MaterialItem,
  PROYECTOS_MATERIAL,
  ProyectoMaterial,
  etiquetaCategoria,
  nombreDescargaImagen,
  etiquetaProyecto,
  tamanoLegible,
} from '../../core/models/material.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { MaterialService } from '../../core/services/material.service';
import { ToastService } from '../../core/services/toast.service';
import { descargarBlob } from '../../core/utils/descargar-blob';
import { ImagenDiferidaComponent } from '../../shared/imagen-diferida/imagen-diferida.component';
import { MaterialGaleriaMosaicoComponent } from '../../shared/material-galeria-mosaico/material-galeria-mosaico.component';
import { MaterialMiniaturaComponent } from '../../shared/material-miniatura/material-miniatura.component';
import { MaterialVisorComponent } from '../../shared/material-visor/material-visor.component';
import { POSICION_MENU_DERECHA } from '../../shared/menu/posicion-menu';
import { WeLoaderComponent } from '../../shared/we-loader/we-loader.component';

/** Una imagen en el formulario de una galería: una que ya está en el servidor (`existente`) o un archivo elegido
 * que todavía no se sube (`archivo`, con su vista previa local). */
interface EntradaGaleria {
  clave: string;
  existente: MaterialImagen | null;
  archivo: File | null;
  vista: string | null;
  estado: 'lista' | 'pendiente' | 'subiendo' | 'error';
  progreso: number;
  error: string | null;
}

const TIPOS_IMAGEN = ['image/jpeg', 'image/png', 'image/webp'];

/** Material de venta (módulo Material): administración sube copys, flyers, brochures, videos y galerías de imágenes
 * que los asesores consultan desde el plano público de su desarrollo. Una galería se guarda primero (datos) y luego
 * sube sus imágenes una por una, con su avance; si alguna falla, el formulario se queda abierto para reintentar. */
@Component({
  selector: 'app-material-list',
  standalone: true,
  imports: [
    FormsModule,
    WeLoaderComponent,
    MaterialMiniaturaComponent,
    MaterialGaleriaMosaicoComponent,
    ImagenDiferidaComponent,
    MaterialVisorComponent,
    CdkDrag,
    CdkDropList,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    LucideCopy,
    LucideDownload,
    LucideEllipsis,
    LucidePencil,
    LucidePlus,
    LucideTrash2,
    LucideX,
  ],
  templateUrl: './material-list.component.html',
})
export class MaterialListComponent {
  readonly posicionMenu = POSICION_MENU_DERECHA;
  private readonly service = inject(MaterialService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly categorias = CATEGORIAS_MATERIAL;
  readonly proyectos = PROYECTOS_MATERIAL;
  readonly maxMb = MAX_ARCHIVO_MATERIAL_MB;
  readonly maxImagenMb = MAX_IMAGEN_GALERIA_MB;
  readonly maxImagenes = MAX_IMAGENES_GALERIA;
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

  // Galería en el formulario
  readonly esGaleria = computed(() => this.categoria() === 'GALERIA');
  readonly galeria = signal<EntradaGaleria[]>([]);
  /** Texto del botón mientras se suben las imágenes ("Subiendo imagen 3 de 8…"). */
  readonly progresoGuardado = signal<string | null>(null);
  /** Imágenes que ya estaban en el servidor y se quitaron en el formulario (se borran al guardar). */
  private quitadas: number[] = [];
  private consecutivo = 0;

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
    this.reiniciarGaleria([]);
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
    this.reiniciarGaleria(m.imagenes);
    this.errorForm.set(null);
    this.mostrarForm.set(true);
  }

  cerrarForm(): void {
    if (this.guardando()) return;
    this.reiniciarGaleria([]);
    this.mostrarForm.set(false);
  }

  // ---------------------------------------------------------------- galería en el formulario

  private reiniciarGaleria(imagenes: MaterialImagen[]): void {
    for (const e of this.galeria()) if (e.vista) URL.revokeObjectURL(e.vista);
    this.quitadas = [];
    this.galeria.set(
      imagenes.map((img) => ({
        clave: `s-${img.id}`,
        existente: img,
        archivo: null,
        vista: null,
        estado: 'lista' as const,
        progreso: 100,
        error: null,
      })),
    );
  }

  /** Agrega las imágenes elegidas (pueden ser varias); las que no son imagen, pesan de más o ya no caben se avisan. */
  elegirImagenes(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivos = Array.from(input.files ?? []);
    input.value = '';
    const rechazadas: string[] = [];
    const nuevas: EntradaGaleria[] = [];
    for (const f of archivos) {
      if (this.galeria().length + nuevas.length >= this.maxImagenes) {
        rechazadas.push(`${f.name} (la galería ya tiene ${this.maxImagenes})`);
      } else if (!TIPOS_IMAGEN.includes(f.type)) {
        rechazadas.push(`${f.name} (no es JPG, PNG ni WEBP)`);
      } else if (f.size > this.maxImagenMb * 1024 * 1024) {
        rechazadas.push(`${f.name} (pesa ${tamanoLegible(f.size)})`);
      } else {
        nuevas.push({
          clave: `n-${++this.consecutivo}`,
          existente: null,
          archivo: f,
          vista: URL.createObjectURL(f),
          estado: 'pendiente',
          progreso: 0,
          error: null,
        });
      }
    }
    this.galeria.update((lista) => [...lista, ...nuevas]);
    this.errorForm.set(rechazadas.length ? `No se agregaron: ${rechazadas.join(', ')}.` : null);
  }

  quitarDeGaleria(entrada: EntradaGaleria): void {
    if (entrada.existente) this.quitadas.push(entrada.existente.id);
    if (entrada.vista) URL.revokeObjectURL(entrada.vista);
    this.galeria.update((lista) => lista.filter((e) => e.clave !== entrada.clave));
  }

  soltar(evento: CdkDragDrop<EntradaGaleria[]>): void {
    const lista = [...this.galeria()];
    moveItemInArray(lista, evento.previousIndex, evento.currentIndex);
    this.galeria.set(lista);
  }

  miniaturaDe(imagen: MaterialImagen): () => Promise<Blob> {
    const id = this.editandoId();
    return () => this.service.miniaturaImagen(id!, imagen.id);
  }

  private actualizarEntrada(clave: string, cambios: Partial<EntradaGaleria>): void {
    this.galeria.update((lista) => lista.map((e) => (e.clave === clave ? { ...e, ...cambios } : e)));
  }

  /** Después de guardar los datos: borra las quitadas, sube las nuevas una por una y deja el orden de la cuadrícula. */
  private async sincronizarGaleria(item: MaterialItem): Promise<MaterialItem> {
    let actual = item;
    for (const id of this.quitadas) actual = await this.service.quitarImagen(item.id, id);
    this.quitadas = [];

    const pendientes = this.galeria().filter((e) => e.archivo && e.estado !== 'lista');
    for (const [n, e] of pendientes.entries()) {
      this.progresoGuardado.set(`Subiendo imagen ${n + 1} de ${pendientes.length}…`);
      this.actualizarEntrada(e.clave, { estado: 'subiendo', progreso: 0, error: null });
      try {
        const antes = new Set(actual.imagenes.map((i) => i.id));
        actual = await this.service.agregarImagen(item.id, e.archivo!, (p) => this.actualizarEntrada(e.clave, { progreso: p }));
        const nueva = actual.imagenes.find((i) => !antes.has(i.id)) ?? null;
        this.actualizarEntrada(e.clave, { estado: 'lista', existente: nueva, progreso: 100 });
      } catch (err) {
        const mensaje = err instanceof HttpErrorResponse ? err.error?.message : null;
        this.actualizarEntrada(e.clave, {
          estado: 'error',
          error: typeof mensaje === 'string' ? mensaje : 'No se pudo subir',
        });
      }
    }

    // Orden final: el de la cuadrícula, con las que sí quedaron en el servidor.
    const ids = this.galeria()
      .map((e) => e.existente?.id)
      .filter((id): id is number => id != null);
    const enServidor = actual.imagenes.map((i) => i.id);
    if (ids.length === enServidor.length && ids.some((id, i) => id !== enServidor[i])) {
      actual = await this.service.ordenarImagenes(item.id, ids);
    }
    this.progresoGuardado.set(null);
    return actual;
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
    const esGaleria = this.esGaleria();
    if (esGaleria) {
      if (this.galeria().length === 0) {
        this.errorForm.set('Agrega al menos una imagen a la galería.');
        return;
      }
    } else {
      if (this.galeria().some((e) => e.existente)) {
        this.errorForm.set('Quita las imágenes de la galería antes de cambiarle el tipo.');
        return;
      }
      const quedaArchivo = !!this.archivo() || (!!this.archivoActual() && !this.quitarArchivo());
      if (!this.texto().trim() && !quedaArchivo) {
        this.errorForm.set('Agrega un texto, un archivo o ambos.');
        return;
      }
    }
    this.guardando.set(true);
    this.errorForm.set(null);
    const form = {
      titulo: this.titulo(),
      descripcion: this.descripcion(),
      categoria: this.categoria(),
      proyecto: this.proyecto(),
      texto: this.texto(),
      // Una galería no lleva archivo propio: si el material tenía uno, se quita al convertirlo.
      archivo: esGaleria ? null : this.archivo(),
      quitarArchivo: esGaleria ? !!this.archivoActual() : this.quitarArchivo(),
    };
    const id = this.editandoId();
    try {
      let guardado = id == null ? await this.service.crear(form) : await this.service.actualizar(id, form);
      // Desde aquí el material ya existe: si algo falla al subir imágenes, el formulario sigue como edición.
      this.editandoId.set(guardado.id);
      this.archivoActual.set(null);
      if (esGaleria) guardado = await this.sincronizarGaleria(guardado);
      this.items.update((lista) =>
        lista.some((m) => m.id === guardado.id) ? lista.map((m) => (m.id === guardado.id ? guardado : m)) : [guardado, ...lista],
      );
      if (this.galeria().some((e) => e.estado === 'error')) {
        this.errorForm.set('Algunas imágenes no se subieron (marcadas en rojo). Quítalas o vuelve a guardar para reintentar.');
        return;
      }
      this.reiniciarGaleria([]);
      this.mostrarForm.set(false);
      this.toast.success(id == null ? 'Material agregado.' : 'Material actualizado.');
    } catch (e) {
      const mensaje = e instanceof HttpErrorResponse ? e.error?.message : null;
      this.errorForm.set(typeof mensaje === 'string' ? mensaje : 'No se pudo guardar. Intenta de nuevo.');
    } finally {
      this.progresoGuardado.set(null);
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
  /** En qué imagen empieza el visor si se abrió desde una celda del mosaico de una galería. */
  readonly visorImagen = signal(0);
  readonly cargarArchivo = (m: MaterialItem): Promise<Blob> => this.service.archivo(m.id);
  readonly cargarMiniatura = (m: MaterialItem): Promise<Blob> => this.service.miniatura(m.id);
  readonly cargarImagen = (m: MaterialItem, img: MaterialImagen): Promise<Blob> => this.service.imagen(m.id, img.id);
  readonly cargarMiniaturaImagen = (m: MaterialItem, img: MaterialImagen): Promise<Blob> =>
    this.service.miniaturaImagen(m.id, img.id);

  abrirVisor(m: MaterialItem, imagen = 0): void {
    const i = this.visibles().findIndex((x) => x.id === m.id);
    if (i < 0) return;
    this.visorImagen.set(imagen);
    this.visorIndice.set(i);
  }

  /** Peso total de una galería, para la tarjeta. */
  pesoGaleria(m: MaterialItem): string {
    return tamanoLegible(m.imagenes.reduce((total, i) => total + i.tamano, 0));
  }

  async descargarImagen(e: { item: MaterialItem; imagen: MaterialImagen }): Promise<void> {
    try {
      descargarBlob(await this.service.imagen(e.item.id, e.imagen.id), nombreDescargaImagen(e.item, e.imagen));
    } catch {
      this.toast.error('No se pudo descargar la imagen.');
    }
  }

  async descargarGaleria(m: MaterialItem): Promise<void> {
    try {
      descargarBlob(await this.service.zip(m.id), `${m.titulo}.zip`);
    } catch {
      this.toast.error('No se pudo descargar la galería.');
    }
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
