import { Injectable, inject } from '@angular/core';
import { NavigationCancel, NavigationEnd, NavigationError, Router } from '@angular/router';
import { filter, take } from 'rxjs';

const MIN_VISIBLE_MS = 700;
const MAX_VISIBLE_MS = 10_000;
const ESTABLE_MS = 250;

/**
 * Pantalla de carga inicial (el #we-splash de index.html): se queda hasta que la primera pantalla ya
 * tiene sus datos —navegación terminada y ninguna petición en curso, de forma sostenida—, para que
 * al desaparecer todo el contenido se vea de inmediato y no haya un hueco en blanco.
 */
@Injectable({ providedIn: 'root' })
export class CargaInicialService {
  private readonly router = inject(Router);
  private readonly inicio = performance.now();
  private pendientes = 0;
  private navegacionLista = false;
  private terminado = false;
  private temporizadorEstable: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd || e instanceof NavigationCancel || e instanceof NavigationError),
        take(1),
      )
      .subscribe(() => {
        this.navegacionLista = true;
        this.evaluar();
      });
    setTimeout(() => this.ocultar(), MAX_VISIBLE_MS);
  }

  /** Lo llama el interceptor HTTP: una petición empezó. */
  peticionIniciada(): void {
    this.pendientes++;
    this.cancelarEstable();
  }

  /** Lo llama el interceptor HTTP: una petición terminó (bien o mal). */
  peticionTerminada(): void {
    this.pendientes = Math.max(0, this.pendientes - 1);
    this.evaluar();
  }

  private evaluar(): void {
    if (this.terminado || !this.navegacionLista || this.pendientes > 0) return;
    this.cancelarEstable();
    // Espera a que no salga otra petición encadenada y a que Angular termine de pintar.
    this.temporizadorEstable = setTimeout(() => {
      if (this.pendientes > 0) return;
      const faltan = MIN_VISIBLE_MS - (performance.now() - this.inicio);
      setTimeout(() => this.ocultar(), Math.max(0, faltan));
    }, ESTABLE_MS);
  }

  private cancelarEstable(): void {
    if (this.temporizadorEstable) clearTimeout(this.temporizadorEstable);
    this.temporizadorEstable = null;
  }

  private ocultar(): void {
    if (this.terminado) return;
    this.terminado = true;
    const splash = document.getElementById('we-splash');
    if (!splash) return;
    // Dos cuadros para asegurar que el contenido ya está pintado antes de descubrirlo.
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        splash.classList.add('we-splash-salir');
        setTimeout(() => splash.remove(), 400);
      }),
    );
  }
}
