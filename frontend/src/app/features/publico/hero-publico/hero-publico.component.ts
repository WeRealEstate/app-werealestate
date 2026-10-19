import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideChevronDown, LucideChevronRight } from '@lucide/angular';
import { animate, scroll, stagger } from 'motion';

/** Curva de llegada de toda la portada: frena suave al final, sin rebote. */
const LLEGADA: [number, number, number, number] = [0.16, 1, 0.3, 1];

/** Portada de /cotizador-publico (y /cotizador-publico/lotes, que comparte el layout): la foto de la costa
 * de Oaxaca a pantalla completa con el título centrado. Dos momentos de movimiento, nada más:
 *  - al entrar, la foto se asienta (se aleja un poco) y el texto aparece de abajo, de borroso a nítido;
 *  - al bajar, la foto se acerca y se oscurece mientras el texto sube y se desvanece, dejando paso al
 *    formulario.
 * Con "reducir movimiento" no hay desplazamientos ni zoom: el texto solo aparece. El contenido es visible
 * por defecto (las animaciones parten de JS), así que si algo falla la portada se ve completa. */
@Component({
  selector: 'app-hero-publico',
  standalone: true,
  imports: [RouterLink, LucideChevronDown, LucideChevronRight],
  templateUrl: './hero-publico.component.html',
})
export class HeroPublicoComponent implements AfterViewInit, OnDestroy {
  private readonly router = inject(Router);

  @ViewChild('hero', { static: true }) private readonly hero!: ElementRef<HTMLElement>;
  @ViewChild('fotoMarco', { static: true }) private readonly fotoMarco!: ElementRef<HTMLElement>;
  @ViewChild('foto', { static: true }) private readonly foto!: ElementRef<HTMLImageElement>;
  @ViewChild('veloScroll', { static: true }) private readonly veloScroll!: ElementRef<HTMLElement>;
  @ViewChild('contenido', { static: true }) private readonly contenido!: ElementRef<HTMLElement>;
  @ViewChild('flecha', { static: true }) private readonly flecha!: ElementRef<HTMLElement>;

  private readonly limpiezas: VoidFunction[] = [];

  ngAfterViewInit(): void {
    const items = this.hero.nativeElement.querySelectorAll<HTMLElement>('.hero-item');
    const reducido = matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reducido) {
      animate(items, { opacity: [0, 1] }, { duration: 0.4, delay: stagger(0.08) });
      return;
    }

    // Entrada: la foto se asienta y el texto llega escalonado, de borroso a nítido.
    animate(this.foto.nativeElement, { scale: [1.08, 1], opacity: [0, 1] }, { duration: 1.6, ease: LLEGADA });
    animate(
      items,
      { opacity: [0, 1], y: [28, 0], filter: ['blur(10px)', 'blur(0px)'] },
      { duration: 0.9, delay: stagger(0.12, { startDelay: 0.2 }), ease: LLEGADA },
    );
    animate(this.flecha.nativeElement, { opacity: [0, 1] }, { duration: 0.6, delay: 0.9 });

    // Al bajar: el avance del scroll sobre la portada (de su borde superior al inferior) mueve la escena.
    const opciones = { target: this.hero.nativeElement, offset: ['start start', 'end start'] as ['start start', 'end start'] };
    this.limpiezas.push(
      scroll(animate(this.fotoMarco.nativeElement, { scale: [1, 1.12] }, { ease: 'linear' }), opciones),
      scroll(animate(this.veloScroll.nativeElement, { opacity: [0, 0.55] }, { ease: 'linear' }), opciones),
      scroll(animate(this.contenido.nativeElement, { opacity: [1, 0], y: [0, -80] }, { ease: 'linear' }), {
        ...opciones,
        offset: ['start start', 'center start'],
      }),
    );
  }

  ngOnDestroy(): void {
    this.limpiezas.forEach((limpiar) => limpiar());
  }

  /** La portada es compartida por /cotizador-publico y /cotizador-publico/lotes (mismo layout, ver
   * CotizadorPublicoLayoutComponent): si "Comenzar cotización" se toca estando en /lotes, primero hay que
   * volver a la ruta del cotizador — si no, bajar a "#cotizador" solo revelaría la pantalla de lotes. */
  irAlCotizador(): void {
    if (this.router.url.startsWith('/cotizador-publico/lotes')) {
      void this.router.navigateByUrl('/cotizador-publico');
    }
    const destino = document.getElementById('cotizador');
    if (!destino) return;
    const reducido = matchMedia('(prefers-reduced-motion: reduce)').matches;
    // La barra superior es fija: se descuenta su alto para que el formulario no quede debajo de ella.
    const barra = document.getElementById('barra-publica')?.offsetHeight ?? 0;
    window.scrollTo({ top: destino.getBoundingClientRect().top + window.scrollY - barra, behavior: reducido ? 'auto' : 'smooth' });
  }
}
