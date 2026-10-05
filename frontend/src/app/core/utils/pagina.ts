import { DestroyRef } from '@angular/core';

/** Pinta el fondo de TODA la página (html y body) mientras el componente esté en pantalla, para que
 * cualquier hueco que deje el contenedor (barras del navegador en Android, rebote al jalar) tenga el
 * color de la vista y no el blanco del tema. Ver las reglas html.fondo-* en styles.css. */
export function fondoDePagina(destroyRef: DestroyRef, clase: 'fondo-marca' | 'fondo-negro'): void {
  const raiz = document.documentElement;
  raiz.classList.add(clase);
  destroyRef.onDestroy(() => raiz.classList.remove(clase));
}

/** Vista a pantalla completa mientras el componente esté en pantalla: el viewport llega hasta los
 * bordes (viewport-fit=cover) y la barra del navegador toma `color`. Solo vive en esa vista a
 * propósito: con viewport-fit=cover global, el resto del panel quedaría bajo la muesca del iPhone
 * en horizontal. Al salir se restaura el <meta> original. */
export function pantallaCompleta(destroyRef: DestroyRef, color: string): void {
  const viewport = document.querySelector<HTMLMetaElement>('meta[name="viewport"]');
  const viewportOriginal = viewport?.content ?? null;
  if (viewport && !viewport.content.includes('viewport-fit')) {
    viewport.content = `${viewport.content}, viewport-fit=cover`;
  }

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
    if (viewport && viewportOriginal !== null) viewport.content = viewportOriginal;
    if (creado) themeColor?.remove();
    else if (themeColor && colorOriginal !== null) themeColor.content = colorOriginal;
  });
}
