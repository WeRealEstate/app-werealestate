import { Component, ElementRef, OnDestroy, OnInit, inject, input, signal } from '@angular/core';

/** Una imagen que se trae (como blob, con la sesión o el PIN que use `cargar`) solo cuando entra a la pantalla:
 * para los mosaicos y tiras de miniaturas de las galerías, donde puede haber muchas. Mientras carga, un bloque gris
 * que late; si falla, el bloque se queda quieto. Ocupa todo su contenedor (object-cover). */
@Component({
  selector: 'app-imagen-diferida',
  standalone: true,
  host: { class: 'block h-full w-full overflow-hidden bg-surface-2' },
  template: `
    @if (url(); as src) {
      <img [src]="src" [alt]="alt()" class="h-full w-full object-cover" draggable="false" />
    } @else {
      <span class="block h-full w-full bg-surface-2" [class.animate-pulse]="!fallo()" aria-hidden="true"></span>
    }
  `,
})
export class ImagenDiferidaComponent implements OnInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly cargar = input.required<() => Promise<Blob>>();
  readonly alt = input('');

  readonly url = signal<string | null>(null);
  readonly fallo = signal(false);

  private observador: IntersectionObserver | null = null;
  private destruido = false;

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      void this.traer();
      return;
    }
    this.observador = new IntersectionObserver(
      (entradas) => {
        if (entradas.some((e) => e.isIntersecting)) {
          this.observador?.disconnect();
          void this.traer();
        }
      },
      { rootMargin: '200px' },
    );
    this.observador.observe(this.host.nativeElement);
  }

  private async traer(): Promise<void> {
    try {
      const blob = await this.cargar()();
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
