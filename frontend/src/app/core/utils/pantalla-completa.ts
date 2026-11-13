/** Ayudas para la pantalla completa de los planos (Fullscreen API) y su respaldo en la capa superior. */

/** Pide la pantalla completa real para `elemento`. Hay navegadores incrustados (visores dentro de otras apps)
 * donde la petición ni se concede ni se rechaza: por eso el plazo, para que el que llama use su respaldo. */
export function pedirPantallaCompleta(elemento: HTMLElement, plazoMs: number): Promise<'ok' | 'rechazada' | 'plazo'> {
  return Promise.race([
    elemento.requestFullscreen().then(
      () => 'ok' as const,
      () => 'rechazada' as const,
    ),
    new Promise<'plazo'>((resolver) => setTimeout(() => resolver('plazo'), plazoMs)),
  ]);
}

/** Sale de la pantalla completa del navegador si está puesta (sin error si ya se había cerrado). */
export async function salirDePantallaCompleta(): Promise<void> {
  if (document.fullscreenElement) await document.exitFullscreen().catch(() => undefined);
}

/** Respaldo cuando no hay pantalla completa real: un popover "manual" va a la capa superior del navegador, por
 * encima de todo (también de la barra lateral del panel, que ningún z-index tapa), sin ser pantalla completa ni
 * cerrarse al hacer clic afuera. Devuelve false si el navegador no lo soporta. */
export function subirALaCapaSuperior(elemento: HTMLElement): boolean {
  if (typeof elemento.showPopover !== 'function') return false;
  try {
    elemento.setAttribute('popover', 'manual');
    elemento.showPopover();
    return true;
  } catch {
    elemento.removeAttribute('popover');
    return false;
  }
}

export function bajarDeLaCapaSuperior(elemento: HTMLElement): void {
  try {
    elemento.hidePopover();
  } catch {
    // ya estaba cerrado
  }
  elemento.removeAttribute('popover');
}
