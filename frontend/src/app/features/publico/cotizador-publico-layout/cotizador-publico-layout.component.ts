import { Component, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { LucideMap, LucideTrophy } from '@lucide/angular';
import { filter } from 'rxjs';
import { HeroPublicoComponent } from '../hero-publico/hero-publico.component';
import { ScoreResumenComponent } from '../score-resumen/score-resumen.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';

/** Chrome de página para /cotizador-publico: sin sesión ni sidebar, así que le da al Cotizador
 * el fondo, el ancho centrado, la portada con la foto de Oaxaca, una barra fija estilo Apple (logo y
 * accesos a los planos públicos) y el footer que normalmente pone PanelLayoutComponent. También monta
 * el toast/confirm globales — aquí fuera de PanelLayoutComponent, /cotizador-publico/lotes (apartar/
 * liberar un lote sin sesión) los necesita igual que cualquier pantalla del panel interno. */
@Component({
  selector: 'app-cotizador-publico-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, LucideMap, LucideTrophy, HeroPublicoComponent, ScoreResumenComponent, ConfirmDialogComponent, ToastContainerComponent],
  templateUrl: './cotizador-publico-layout.component.html',
})
export class CotizadorPublicoLayoutComponent {
  private readonly router = inject(Router);
  readonly currentYear = new Date().getFullYear();

  /** La portada (foto a pantalla completa) va en el cotizador y en Lotes; el Score de ventas entra directo, sin ella,
   * para que su link compartido muestre el podio de inmediato. */
  readonly conPortada = signal(!esScore(window.location.pathname));
  /** El resumen del score (top 5) va debajo de la portada solo en el cotizador, no en Lotes. */
  readonly enCotizador = signal(esCotizador(window.location.pathname));

  /** true mientras la barra está encima de la foto de la portada: ahí va transparente con texto blanco;
   * después, translúcida con el texto del tema. Sin portada, siempre translúcida. */
  readonly sobreHero = signal(this.conPortada());

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        this.enCotizador.set(esCotizador(e.urlAfterRedirects));
        const conPortada = !esScore(e.urlAfterRedirects);
        if (conPortada !== this.conPortada()) {
          this.conPortada.set(conPortada);
          // Al entrar o salir del score el contenido cambia de lugar (con o sin portada): se arranca desde arriba.
          window.scrollTo({ top: 0 });
        }
        this.revisarBarra();
      });
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  revisarBarra(): void {
    // La portada mide un alto de pantalla; la barra cambia justo antes de que la foto termine de salir.
    const sobre = this.conPortada() && window.scrollY < window.innerHeight - 64;
    if (sobre !== this.sobreHero()) this.sobreHero.set(sobre);
  }
}

function esCotizador(url: string): boolean {
  return /^\/cotizador-publico\/?(?:[?#]|$)/.test(url);
}

function esScore(url: string): boolean {
  return /^\/cotizador-publico\/score(?:[/?#]|$)/.test(url);
}
