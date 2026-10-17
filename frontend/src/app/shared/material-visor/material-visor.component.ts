import { Component, ElementRef, HostListener, OnDestroy, afterNextRender, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { LucideChevronLeft, LucideChevronRight, LucideCopy, LucideDownload, LucideX } from '@lucide/angular';
import { MaterialItem, etiquetaCategoria, tamanoLegible } from '../../core/models/material.model';
import { WeLoaderComponent } from '../we-loader/we-loader.component';

/** Visor grande de un material: imagen ampliada, video con reproductor o PDF embebido, con su texto, "Copiar" y
 * "Descargar". Se navega con las flechas (o el teclado) entre los materiales de la lista que se está viendo.
 * `cargar` trae el archivo: cambia entre el panel (con sesión) y el plano público (con PIN).
 *
 * Al abrirse crece desde la tarjeta del material (el elemento con `data-material-id`) y al cerrarse se encoge
 * hacia ella; sin tarjeta a la vista entra con un fundido corto. Solo anima transform y opacity, y respeta
 * prefers-reduced-motion. */
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

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly fondo = viewChild<ElementRef<HTMLElement>>('fondo');
  private cerrando = false;

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
    afterNextRender(() => void this.animar(true));
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

  /** Cierra con la animación de salida (más corta que la de entrada) y luego avisa. */
  async pedirCierre(): Promise<void> {
    if (this.cerrando) return;
    this.cerrando = true;
    await this.animar(false);
    this.cerrar.emit();
  }

  /** La tarjeta del material que se está viendo, si está a la vista (de ahí crece y a ella vuelve). */
  private rectTarjeta(): DOMRect | null {
    const m = this.actual;
    if (!m) return null;
    const el = document.querySelector(`[data-material-id="${m.id}"]`);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const visible = r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
    return visible ? r : null;
  }

  private async animar(entrando: boolean): Promise<void> {
    const panel = this.panel()?.nativeElement;
    const fondo = this.fondo()?.nativeElement;
    if (!panel || typeof panel.animate !== 'function') return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const origen = this.rectTarjeta();
    const destino = panel.getBoundingClientRect();
    let desde: Keyframe;
    if (origen && destino.width > 0 && destino.height > 0) {
      const dx = origen.left + origen.width / 2 - (destino.left + destino.width / 2);
      const dy = origen.top + origen.height / 2 - (destino.top + destino.height / 2);
      desde = {
        transform: `translate(${dx}px, ${dy}px) scale(${origen.width / destino.width}, ${origen.height / destino.height})`,
        opacity: 0.3,
      };
    } else {
      desde = { transform: 'translateY(14px) scale(0.97)', opacity: 0 };
    }
    const hasta: Keyframe = { transform: 'none', opacity: 1 };
    const opciones: KeyframeAnimationOptions = entrando
      ? { duration: 360, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'both' }
      : { duration: 200, easing: 'cubic-bezier(0.4, 0, 1, 1)', fill: 'forwards' };
    const animaciones = [panel.animate(entrando ? [desde, hasta] : [hasta, { ...desde, opacity: 0 }], opciones)];
    if (fondo) {
      animaciones.push(fondo.animate(entrando ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 1 }, { opacity: 0 }], opciones));
    }
    try {
      // Con tope de tiempo: si el navegador no pinta cuadros (pestaña oculta) la animación nunca termina y el visor
      // no se podría cerrar.
      await Promise.race([
        Promise.all(animaciones.map((a) => a.finished)),
        new Promise((resolver) => setTimeout(resolver, Number(opciones.duration) + 150)),
      ]);
    } catch {
      // animación cancelada (el visor se destruyó): no hay nada que esperar
    }
  }

  @HostListener('document:keydown', ['$event'])
  teclado(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      e.stopPropagation();
      void this.pedirCierre();
    } else if (e.key === 'ArrowLeft') this.anterior();
    else if (e.key === 'ArrowRight') this.siguiente();
  }

  ngOnDestroy(): void {
    this.peticion++;
    this.liberar();
  }
}
