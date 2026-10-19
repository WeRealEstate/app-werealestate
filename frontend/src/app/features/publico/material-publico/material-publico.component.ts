import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  CATEGORIAS_MATERIAL,
  CategoriaMaterial,
  MaterialImagen,
  MaterialItem,
  etiquetaCategoria,
  tamanoLegible,
} from '../../../core/models/material.model';
import { MaterialService } from '../../../core/services/material.service';
import { ToastService } from '../../../core/services/toast.service';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';
import { descargarBlob } from '../../../core/utils/descargar-blob';
import { DesarrolloLoaderComponent } from '../../../shared/desarrollo-loader/desarrollo-loader.component';
import { MaterialGaleriaMosaicoComponent } from '../../../shared/material-galeria-mosaico/material-galeria-mosaico.component';
import { MaterialMiniaturaComponent } from '../../../shared/material-miniatura/material-miniatura.component';
import { MaterialVisorComponent } from '../../../shared/material-visor/material-visor.component';
import { PinPublicoComponent } from '../../../shared/pin-publico/pin-publico.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';

/** /material?proyecto=samai: a donde lleva el botón "Material" del plano público (se abre en otra pestaña).
 * Grid con el material de venta del desarrollo. Igual que We Forms: pide el PIN del asesor (o usa el que este
 * dispositivo ya recuerda) y el servidor lo revisa en cada petición. */
@Component({
  selector: 'app-material-publico',
  standalone: true,
  imports: [
    PinPublicoComponent,
    DesarrolloLoaderComponent,
    MaterialMiniaturaComponent,
    MaterialGaleriaMosaicoComponent,
    MaterialVisorComponent,
    ToastContainerComponent,
  ],
  templateUrl: './material-publico.component.html',
})
export class MaterialPublicoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(MaterialService);
  private readonly weFormsPublico = inject(WeFormsPublicoService);
  private readonly toast = inject(ToastService);

  readonly proyecto = this.route.snapshot.queryParamMap.get('proyecto') === 'nanuu' ? 'nanuu' : 'samai';
  readonly nombreProyecto = this.proyecto === 'nanuu' ? 'Aldea Nanuu' : 'SAMAI Campestre';
  readonly rutaPlano = this.proyecto === 'nanuu' ? '/aldea-nanuu' : '/samai';

  readonly etiquetaCategoria = etiquetaCategoria;
  readonly tamanoLegible = tamanoLegible;

  readonly items = signal<MaterialItem[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly verificando = signal(false);
  /** true mientras se entra solo con el PIN recordado: así no se ve un instante la pantalla del PIN. */
  readonly entrandoSolo = signal(!!this.weFormsPublico.pin());
  readonly filtro = signal<CategoriaMaterial | ''>('');
  readonly trabajando = signal<number | null>(null);
  /** Posición en la lista visible del material que se está viendo en el visor (null = visor cerrado). */
  readonly visorIndice = signal<number | null>(null);
  /** En qué imagen empieza el visor si se abrió desde una celda del mosaico de una galería. */
  readonly visorImagen = signal(0);

  readonly categoriasPresentes = computed(() =>
    CATEGORIAS_MATERIAL.filter((c) => (this.items() ?? []).some((m) => m.categoria === c.valor)),
  );
  readonly visibles = computed(() => (this.items() ?? []).filter((m) => !this.filtro() || m.categoria === this.filtro()));

  readonly cargarArchivo = (m: MaterialItem): Promise<Blob> =>
    this.service.archivoPublico(m.id, this.proyecto, this.weFormsPublico.pin() ?? '');
  readonly cargarMiniatura = (m: MaterialItem): Promise<Blob> =>
    this.service.miniaturaPublico(m.id, this.proyecto, this.weFormsPublico.pin() ?? '');
  readonly cargarImagen = (m: MaterialItem, img: MaterialImagen): Promise<Blob> =>
    this.service.imagenPublico(m.id, img.id, this.proyecto, this.weFormsPublico.pin() ?? '');
  readonly cargarMiniaturaImagen = (m: MaterialItem, img: MaterialImagen): Promise<Blob> =>
    this.service.miniaturaImagenPublico(m.id, img.id, this.proyecto, this.weFormsPublico.pin() ?? '');

  constructor() {
    const pin = this.weFormsPublico.pin();
    if (pin) void this.entrar(pin).finally(() => this.entrandoSolo.set(false));
  }

  /** Olvida el PIN de este dispositivo para que otro asesor pueda entrar con el suyo. */
  salir(): void {
    this.weFormsPublico.guardarPin(null);
    this.items.set(null);
    this.error.set(null);
    this.visorIndice.set(null);
  }

  async entrar(pin: string): Promise<void> {
    this.verificando.set(true);
    this.error.set(null);
    try {
      this.items.set(await this.service.listarPublico(this.proyecto, pin));
      this.weFormsPublico.guardarPin(pin);
    } catch (e) {
      // Solo se olvida el PIN si el servidor lo rechazó; un fallo de red no debe borrarlo.
      if (e instanceof HttpErrorResponse && (e.status === 403 || e.status === 404)) this.weFormsPublico.guardarPin(null);
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo cargar el material.',
      );
    } finally {
      this.verificando.set(false);
    }
  }

  abrirVisor(m: MaterialItem, imagen = 0): void {
    const i = this.visibles().findIndex((x) => x.id === m.id);
    if (i < 0) return;
    this.visorImagen.set(imagen);
    this.visorIndice.set(i);
  }

  async copiarTexto(m: MaterialItem): Promise<void> {
    try {
      await navigator.clipboard.writeText(m.texto ?? '');
      this.toast.success('Texto copiado.');
    } catch {
      this.toast.error('No se pudo copiar el texto.');
    }
  }

  async descargarImagen(e: { item: MaterialItem; imagen: MaterialImagen }): Promise<void> {
    try {
      descargarBlob(await this.cargarImagen(e.item, e.imagen), e.imagen.nombre);
    } catch {
      this.toast.error('No se pudo descargar la imagen. Intenta de nuevo.');
    }
  }

  /** Toda la galería en un ZIP (puede tardar: el botón dice "Descargando…" mientras tanto). */
  async descargarGaleria(m: MaterialItem): Promise<void> {
    if (this.trabajando() !== null) return;
    this.trabajando.set(m.id);
    try {
      descargarBlob(await this.service.zipPublico(m.id, this.proyecto, this.weFormsPublico.pin() ?? ''), `${m.titulo}.zip`);
    } catch {
      this.toast.error('No se pudo descargar la galería. Intenta de nuevo.');
    } finally {
      this.trabajando.set(null);
    }
  }

  async descargar(m: MaterialItem): Promise<void> {
    if (this.trabajando() !== null) return;
    this.trabajando.set(m.id);
    try {
      descargarBlob(await this.cargarArchivo(m), m.archivoNombre ?? 'material');
    } catch {
      this.toast.error('No se pudo descargar el archivo. Intenta de nuevo.');
    } finally {
      this.trabajando.set(null);
    }
  }
}
