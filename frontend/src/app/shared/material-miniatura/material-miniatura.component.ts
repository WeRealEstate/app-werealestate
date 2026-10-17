import { Component, ElementRef, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { LucideFileText, LucidePlay } from '@lucide/angular';
import { MaterialItem } from '../../core/models/material.model';

/** Miniatura de un material en su tarjeta. Imagen: carga la miniatura (reducida por el servidor) cuando la
 * tarjeta entra a la pantalla. PDF y video: un ícono con su tipo. Al tocarla avisa con `abrir` (el visor lo
 * abre quien la usa). `cargar` trae la imagen: cambia entre el panel (con sesión) y el plano público (con PIN). */
@Component({
  selector: 'app-material-miniatura',
  standalone: true,
  imports: [LucideFileText, LucidePlay],
  templateUrl: './material-miniatura.component.html',
})
export class MaterialMiniaturaComponent implements OnInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly item = input.required<MaterialItem>();
  readonly cargar = input.required<(m: MaterialItem) => Promise<Blob>>();
  readonly abrir = output<void>();
  /** El rótulo "Ver vista previa" al pasar el mouse; se apaga cuando quien la usa pone su propia capa. */
  readonly mostrarEtiqueta = input(true);

  readonly url = signal<string | null>(null);
  readonly fallo = signal(false);

  private observador: IntersectionObserver | null = null;
  private destruido = false;

  get esImagen(): boolean {
    return ['jpg', 'png', 'webp'].includes(this.item().archivoExtension ?? '');
  }

  get esVideo(): boolean {
    return this.item().archivoExtension === 'mp4';
  }

  ngOnInit(): void {
    if (!this.esImagen) return;
    if (typeof IntersectionObserver === 'undefined') {
      void this.traer();
      return;
    }
    this.observador = new IntersectionObserver((entradas) => {
      if (entradas.some((e) => e.isIntersecting)) {
        this.observador?.disconnect();
        void this.traer();
      }
    }, { rootMargin: '200px' });
    this.observador.observe(this.host.nativeElement);
  }

  private async traer(): Promise<void> {
    try {
      const blob = await this.cargar()(this.item());
      if (this.destruido) return;
      this.url.set(URL.createObjectURL(blob));
    } catch {
      this.fallo.set(true);
    }
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.observador?.disconnect();
    const u = this.url();
    if (u) URL.revokeObjectURL(u);
  }
}
