import { AfterViewInit, Directive, ElementRef, Input, OnDestroy } from '@angular/core';
import { animate, inView } from 'motion';

/**
 * Aparición al bajar, como en las páginas de Apple: el bloque sube un poco y se aclara la primera vez que
 * entra a la pantalla. Lo que ya se ve al cargar no se anima (no hace esperar a nadie), con "reducir
 * movimiento" no se anima nada, y al terminar se borran los estilos en línea: un `transform` que se queda
 * puesto convertiría al bloque en contenedor de sus modales `position: fixed` (ver .page-enter en styles.css).
 */
@Directive({
  selector: '[weRevelar]',
  standalone: true,
})
export class RevelarDirective implements AfterViewInit, OnDestroy {
  /** Segundos de espera, para escalonar bloques que entran juntos. */
  @Input() weRevelarRetraso = 0;

  private detener: VoidFunction | null = null;

  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngAfterViewInit(): void {
    const el = this.el.nativeElement;
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (el.getBoundingClientRect().top < window.innerHeight) return;

    el.style.opacity = '0';
    el.style.transform = 'translateY(32px)';
    this.detener = inView(
      el,
      () => {
        this.detener?.();
        this.detener = null;
        animate(el, { opacity: [0, 1], y: [32, 0] }, { duration: 0.8, delay: this.weRevelarRetraso, ease: [0.16, 1, 0.3, 1] })
          .finished.then(() => {
            el.style.opacity = '';
            el.style.transform = '';
          });
      },
      { amount: 0.12 },
    );
  }

  ngOnDestroy(): void {
    this.detener?.();
  }
}
