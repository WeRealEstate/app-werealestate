import { Injectable } from '@angular/core';

const SELECTOR_DIALOGO = '[role="dialog"][aria-modal="true"]';
const SELECTOR_ENFOCABLES =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
const SELECTOR_CAMPO_INICIAL = 'input:not([disabled]):not([type="hidden"]), textarea:not([disabled]), select:not([disabled])';
const TIPOS_INPUT_SIN_ENTER = new Set(['checkbox', 'radio', 'file', 'button', 'submit', 'reset', 'range', 'color']);

interface DialogoAbierto {
  dialogo: HTMLElement;
  /** Quién tenía el foco justo antes de abrirse, para devolvérselo al cerrar (como hace una app
   * de escritorio al cerrar un diálogo). */
  previo: HTMLElement | null;
}

/** Comportamiento de teclado de escritorio (Windows/Mac) para TODOS los modales de la app, sin tocar
 * cada uno: se apoya en la convención que ya siguen todos — un `[role="dialog"][aria-modal]` cuyo
 * hermano anterior es el botón de fondo ("Cerrar") que lo cancela. Cualquier modal nuevo que siga esa
 * convención lo hereda solo.
 *
 * - Esc: cierra el modal de más arriba, igual que pulsar Cancelar/Cerrar.
 * - Enter en un campo de texto (fuera de un `<form>`, que ya envía solo): pulsa el botón principal si
 *   está habilitado; en un textarea, Ctrl/Cmd+Enter (Enter solo sigue siendo salto de línea).
 * - Tab/Shift+Tab: el foco no se escapa a la página de atrás.
 * - Al abrir: el foco entra al modal (primer campo, o el botón marcado con data-autofocus, o el
 *   propio panel); al cerrar: vuelve a donde estaba. */
@Injectable({ providedIn: 'root' })
export class AccesibilidadTecladoService {
  private readonly pila: DialogoAbierto[] = [];
  private iniciado = false;

  iniciar(): void {
    if (this.iniciado || typeof document === 'undefined') return;
    this.iniciado = true;
    // En captura sobre window: corre antes que cualquier otro manejador de teclado de la app, así un
    // Esc que cierra un modal no dispara además el "deshacer" del plano ni cierra otra cosa detrás.
    window.addEventListener('keydown', (e) => this.alPresionarTecla(e), true);
    new MutationObserver(() => this.sincronizarPila()).observe(document.body, { childList: true, subtree: true });
  }

  private dialogoSuperior(): HTMLElement | null {
    const dialogos = Array.from(document.querySelectorAll<HTMLElement>(SELECTOR_DIALOGO));
    let mejor: HTMLElement | null = null;
    let mejorZ = -Infinity;
    for (const d of dialogos) {
      const z = Number.parseInt(getComputedStyle(d).zIndex, 10);
      const valor = Number.isNaN(z) ? 0 : z;
      if (valor >= mejorZ) {
        mejor = d;
        mejorZ = valor;
      }
    }
    return mejor;
  }

  private alPresionarTecla(e: KeyboardEvent): void {
    if (e.isComposing) return;
    const dialogo = this.dialogoSuperior();
    if (!dialogo) return;

    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopImmediatePropagation();
      this.cerrar(dialogo);
    } else if (e.key === 'Tab') {
      this.atraparTab(e, dialogo);
    } else if (e.key === 'Enter') {
      this.confirmarConEnter(e, dialogo);
    }
  }

  /** Mismo efecto que hacer clic en el fondo oscuro (o, si ese modal no lo tiene, en su botón
   * Cancelar/Cerrar). */
  private cerrar(dialogo: HTMLElement): void {
    const fondo = dialogo.previousElementSibling;
    if (fondo instanceof HTMLButtonElement) {
      fondo.click();
      return;
    }
    const botones = Array.from(dialogo.querySelectorAll<HTMLButtonElement>('button'));
    botones.find((b) => /^(cancelar|cerrar)$/i.test(b.textContent?.trim() ?? ''))?.click();
  }

  private confirmarConEnter(e: KeyboardEvent, dialogo: HTMLElement): void {
    const destino = e.target;
    if (!(destino instanceof HTMLElement) || destino.closest('form')) return;

    const esTextarea = destino instanceof HTMLTextAreaElement;
    const esCampo = destino instanceof HTMLInputElement && !TIPOS_INPUT_SIN_ENTER.has(destino.type);
    if (!esTextarea && !esCampo) return;
    if (esTextarea !== (e.ctrlKey || e.metaKey)) return;
    if (e.shiftKey || e.altKey) return;

    const principales = Array.from(
      dialogo.querySelectorAll<HTMLButtonElement>('button.bg-we-primary, button.bg-red-600'),
    );
    const principal = principales.at(-1);
    e.preventDefault();
    if (principal && !principal.disabled) principal.click();
  }

  private atraparTab(e: KeyboardEvent, dialogo: HTMLElement): void {
    const enfocables = Array.from(dialogo.querySelectorAll<HTMLElement>(SELECTOR_ENFOCABLES)).filter(
      (el) => el.getClientRects().length > 0,
    );
    if (enfocables.length === 0) {
      e.preventDefault();
      return;
    }
    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];
    const activo = document.activeElement;

    if (!dialogo.contains(activo) || activo === dialogo.firstElementChild) {
      e.preventDefault();
      (e.shiftKey ? ultimo : primero).focus();
    } else if (e.shiftKey && activo === primero) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && activo === ultimo) {
      e.preventDefault();
      primero.focus();
    }
  }

  /** Detecta modales que aparecieron o desaparecieron para mover el foco adentro al abrir y
   * devolverlo al cerrar. */
  private sincronizarPila(): void {
    const presentes = new Set(Array.from(document.querySelectorAll<HTMLElement>(SELECTOR_DIALOGO)));

    for (const nuevo of presentes) {
      if (this.pila.some((p) => p.dialogo === nuevo)) continue;
      const previo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      this.pila.push({ dialogo: nuevo, previo });
      requestAnimationFrame(() => this.enfocarAdentro(nuevo));
    }

    for (let i = this.pila.length - 1; i >= 0; i--) {
      const entrada = this.pila[i];
      if (presentes.has(entrada.dialogo)) continue;
      this.pila.splice(i, 1);
      const sigueAbierto = presentes.size > 0;
      if (!sigueAbierto && entrada.previo?.isConnected) entrada.previo.focus({ preventScroll: true });
    }
  }

  private enfocarAdentro(dialogo: HTMLElement): void {
    if (!dialogo.isConnected || dialogo.contains(document.activeElement)) return;
    const destino =
      dialogo.querySelector<HTMLElement>('[data-autofocus]') ?? dialogo.querySelector<HTMLElement>(SELECTOR_CAMPO_INICIAL);
    if (destino) {
      destino.focus({ preventScroll: true });
      return;
    }
    const panel = dialogo.firstElementChild;
    if (panel instanceof HTMLElement) {
      panel.tabIndex = -1;
      panel.style.outline = 'none';
      panel.focus({ preventScroll: true });
    }
  }
}
