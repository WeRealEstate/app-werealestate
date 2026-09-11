import { AfterViewInit, Component, ElementRef } from '@angular/core';
import { animate, stagger } from 'motion';
import { PressDirective } from '../../../shared/motion/press.directive';

/** Espejo literal del hero de cotizador.weinversiones.com (mismo markup, imagen, copy y
 * animación de entrada), portado tal cual para /cotizador-publico, que reemplaza a ese sitio. */
@Component({
  selector: 'app-hero-publico',
  standalone: true,
  imports: [PressDirective],
  templateUrl: './hero-publico.component.html',
  styles: [
    `
      .hero-item {
        opacity: 0;
      }
    `,
  ],
})
export class HeroPublicoComponent implements AfterViewInit {
  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngAfterViewInit(): void {
    const items = this.el.nativeElement.querySelectorAll('.hero-item');

    animate(
      items,
      {
        opacity: [0, 1],
        y: [24, 0],
      },
      {
        duration: 0.6,
        delay: stagger(0.1),
        ease: [0.22, 1, 0.36, 1],
      },
    );
  }

  irAlCotizador(): void {
    const destino = document.getElementById('cotizador');

    if (!destino) return;

    const inicio = window.scrollY;
    const fin = destino.getBoundingClientRect().top + window.scrollY;
    const distancia = fin - inicio;

    const duracion = 900;
    let tiempoInicio: number | null = null;

    const animarScroll = (tiempoActual: number) => {
      if (tiempoInicio === null) {
        tiempoInicio = tiempoActual;
      }

      const tiempoTranscurrido = tiempoActual - tiempoInicio;

      const progreso = Math.min(tiempoTranscurrido / duracion, 1);

      // Curva easeInOutCubic
      const suavizado = progreso < 0.5 ? 4 * progreso * progreso * progreso : 1 - Math.pow(-2 * progreso + 2, 3) / 2;

      window.scrollTo(0, inicio + distancia * suavizado);

      if (progreso < 1) {
        requestAnimationFrame(animarScroll);
      }
    };

    requestAnimationFrame(animarScroll);
  }
}
