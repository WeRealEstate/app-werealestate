import { Component, HostListener, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LucideMap } from '@lucide/angular';
import { HeroPublicoComponent } from '../hero-publico/hero-publico.component';
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
  imports: [RouterOutlet, RouterLink, LucideMap, HeroPublicoComponent, ConfirmDialogComponent, ToastContainerComponent],
  templateUrl: './cotizador-publico-layout.component.html',
})
export class CotizadorPublicoLayoutComponent {
  readonly currentYear = new Date().getFullYear();

  /** true mientras la barra está encima de la foto de la portada: ahí va transparente con texto blanco;
   * después, translúcida con el texto del tema. */
  readonly sobreHero = signal(true);

  @HostListener('window:scroll')
  @HostListener('window:resize')
  revisarBarra(): void {
    // La portada mide un alto de pantalla; la barra cambia justo antes de que la foto termine de salir.
    const sobre = window.scrollY < window.innerHeight - 64;
    if (sobre !== this.sobreHero()) this.sobreHero.set(sobre);
  }
}
