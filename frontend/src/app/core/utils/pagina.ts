import { DestroyRef } from '@angular/core';

/** Pinta el fondo de TODA la página (html y body) mientras el componente esté en pantalla, para que
 * cualquier hueco que deje el contenedor (barras del navegador en Android, rebote al jalar) tenga el
 * color de la vista y no el blanco del tema. Ver las reglas html.fondo-* en styles.css. */
export function fondoDePagina(destroyRef: DestroyRef, clase: 'fondo-marca' | 'fondo-negro'): void {
  const raiz = document.documentElement;
  raiz.classList.add(clase);
  destroyRef.onDestroy(() => raiz.classList.remove(clase));
}

/** Barra del navegador (Android) del color indicado mientras el componente esté en pantalla. El
 * viewport ya es viewport-fit=cover para toda la app (ver index.html), así que la página llega hasta
 * el borde inferior y cada vista deja su propio margen con env(safe-area-inset-*). Al salir se
 * restaura el <meta name="theme-color"> original. */
export function colorDeBarra(destroyRef: DestroyRef, color: string): void {
  let themeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  const creado = themeColor === null;
  const colorOriginal = themeColor?.content ?? null;
  if (!themeColor) {
    themeColor = document.createElement('meta');
    themeColor.name = 'theme-color';
    document.head.appendChild(themeColor);
  }
  themeColor.content = color;

  destroyRef.onDestroy(() => {
    if (creado) themeColor?.remove();
    else if (themeColor && colorOriginal !== null) themeColor.content = colorOriginal;
  });
}
