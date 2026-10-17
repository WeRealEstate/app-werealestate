import { Component, HostListener, OnDestroy, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { LucideChevronLeft, LucideChevronRight, LucideCopy, LucideDownload, LucideX } from '@lucide/angular';
import { MaterialItem, etiquetaCategoria, tamanoLegible } from '../../core/models/material.model';
import { WeLoaderComponent } from '../we-loader/we-loader.component';

/** Visor grande de un material: imagen ampliada, video con reproductor o PDF embebido, con su texto, "Copiar" y
 * "Descargar". Se navega con las flechas (o el teclado) entre los materiales de la lista que se está viendo.
 * `cargar` trae el archivo: cambia entre el panel (con sesión) y el plano público (con PIN). */
@Component({
  selector: 'app-material-visor',
  standalone: true,
  imports: [WeLoaderComponent, LucideChevronLeft, LucideChevronRight, LucideCopy, LucideDownload, LucideX],
  templateUrl: './material-visor.component.html',
})
export class MaterialVisorComponent implements OnDestroy {
  private readonly sanitizer = inject(DomSanitizer);

  readonly items = input.required<MaterialItem[]>();
  readonly indiceInicial = input.required<number>();
  readonly cargar = input.required<(m: MaterialItem) => Promise<Blob>>();
  readonly cerrar = output<void>();
  readonly descargar = output<MaterialItem>();
  readonly copiar = output<MaterialItem>();

  readonly indice = signal(0);
  readonly url = signal<string | null>(null);
  readonly urlSegura = signal<SafeResourceUrl | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);

  readonly etiquetaCategoria = etiquetaCategoria;
  readonly tamanoLegible = tamanoLegible;

  private urlActual: string | null = null;
  private peticion = 0;

  constructor() {
    effect(() => this.indice.set(this.indiceInicial()));
    effect(() => {
      const item = this.items()[this.indice()];
      if (item) untracked(() => void this.mostrar(item));
    });
  }

  get actual(): MaterialItem | undefined {
    return this.items()[this.indice()];
  }

  get tipo(): 'imagen' | 'video' | 'pdf' | 'texto' {
    const ext = this.actual?.archivoExtension;
    if (!ext) return 'texto';
    if (ext === 'mp4') return 'video';
    if (ext === 'pdf') return 'pdf';
    return 'imagen';
  }

  private async mostrar(item: MaterialItem): Promise<void> {
    const mia = ++this.peticion;
    this.liberar();
    this.error.set(false);
    if (!item.archivoExtension) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    try {
      const blob = await this.cargar()(item);
      if (mia !== this.peticion) return;
      this.urlActual = URL.createObjectURL(blob);
      this.url.set(this.urlActual);
      // Es un blob que acabamos de crear nosotros con el archivo del servidor: seguro para el iframe del PDF.
      this.urlSegura.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.urlActual));
    } catch {
      if (mia === this.peticion) this.error.set(true);
    } finally {
      if (mia === this.peticion) this.cargando.set(false);
    }
  }

  private liberar(): void {
    if (this.urlActual) URL.revokeObjectURL(this.urlActual);
    this.urlActual = null;
    this.url.set(null);
    this.urlSegura.set(null);
  }

  anterior(): void {
    if (this.indice() > 0) this.indice.update((i) => i - 1);
  }

  siguiente(): void {
    if (this.indice() < this.items().length - 1) this.indice.update((i) => i + 1);
  }

  @HostListener('document:keydown', ['$event'])
  teclado(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      e.stopPropagation();
      this.cerrar.emit();
    } else if (e.key === 'ArrowLeft') this.anterior();
    else if (e.key === 'ArrowRight') this.siguiente();
  }

  ngOnDestroy(): void {
    this.peticion++;
    this.liberar();
  }
}
