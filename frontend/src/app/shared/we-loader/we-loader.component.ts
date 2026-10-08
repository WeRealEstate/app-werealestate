import { Component, computed, input } from '@angular/core';

/**
 * Pantalla/bloque de carga de We: la "e" del logo gira y tres puntos rebotan. Claro y oscuro con su
 * versión del logo. modo "pantalla" ocupa todo el alto con fondo de marca; "bloque" va dentro de la
 * página; "compacto" es para modales y secciones pequeñas.
 */
@Component({
  selector: 'app-we-loader',
  host: { class: 'block' },
  templateUrl: './we-loader.component.html',
})
export class WeLoaderComponent {
  readonly modo = input<'pantalla' | 'bloque' | 'compacto'>('bloque');
  readonly texto = input('Cargando…');

  readonly ancho = computed(() => (this.modo() === 'pantalla' ? 230 : this.modo() === 'bloque' ? 110 : 64));
  readonly clases = computed(() =>
    this.modo() === 'pantalla'
      ? 'we-loader-pantalla flex h-full min-h-dvh w-full flex-col items-center justify-center gap-8'
      : this.modo() === 'bloque'
        ? 'flex min-h-[60dvh] flex-col items-center justify-center gap-6 py-10'
        : 'flex flex-col items-center justify-center gap-3 py-6',
  );
}
