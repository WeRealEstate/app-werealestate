import { Component, computed, input } from '@angular/core';

export type DesarrolloLoader = 'samai' | 'nanuu';

/** Duración de un ciclo de las animaciones (ver styles.css, .dl-*). */
const CICLO_MS = 4400;

/**
 * Pantalla de carga de un desarrollo: la animación de SAMAI Campestre o la de Aldea Nanuu (CSS en
 * styles.css, imágenes en /images/loaders). modo "cubrir" llena el contenedor posicionado donde está
 * (el plano público); "pantalla" ocupa toda la ventana; "bloque" va dentro de la página con fondo
 * transparente. Sin `tema` sigue el tema de la app; "oscuro"/"claro" lo fuerzan.
 * `continuaSplash`: si la página arrancó con el primer pantallazo del desarrollo (#dev-splash en
 * index.html), esta animación entra en la misma fase para que el relevo no se note.
 */
@Component({
  selector: 'app-desarrollo-loader',
  host: { class: 'contents' },
  templateUrl: './desarrollo-loader.component.html',
})
export class DesarrolloLoaderComponent {
  readonly desarrollo = input.required<DesarrolloLoader>();
  readonly modo = input<'cubrir' | 'pantalla' | 'bloque'>('bloque');
  readonly tema = input<'oscuro' | 'claro' | null>(null);
  readonly texto = input('Cargando…');
  readonly continuaSplash = input(false);

  /** Retraso negativo que deja la animación en la misma fase que la del primer pantallazo. */
  readonly retraso = computed(() => (this.continuaSplash() ? `-${faseDelSplash() / 1000}s` : '0s'));
}

function faseDelSplash(): number {
  const splash = typeof document !== 'undefined' ? document.getElementById('dev-splash') : null;
  const animacion = splash?.querySelector('.dl-s-emblem, .dl-n-aldea')?.getAnimations?.()[0];
  const t = animacion?.currentTime;
  return (typeof t === 'number' ? t : performance.now()) % CICLO_MS;
}
