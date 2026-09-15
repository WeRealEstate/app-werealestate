import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeroPublicoComponent } from '../hero-publico/hero-publico.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';

/** Chrome de página para /cotizador-publico: sin sesión ni sidebar, así que le da al Cotizador
 * el fondo, el ancho centrado, el hero (espejo de cotizador.weinversiones.com) y el footer de
 * marca que normalmente pone PanelLayoutComponent. Sin esto el Cotizador se renderiza pegado al
 * borde del navegador, sin aire ni identidad. También monta el toast/confirm globales — aquí
 * fuera de PanelLayoutComponent, /cotizador-publico/lotes (apartar/liberar un lote sin sesión)
 * los necesita igual que cualquier pantalla del panel interno. */
@Component({
  selector: 'app-cotizador-publico-layout',
  standalone: true,
  imports: [RouterOutlet, HeroPublicoComponent, ConfirmDialogComponent, ToastContainerComponent],
  templateUrl: './cotizador-publico-layout.component.html',
})
export class CotizadorPublicoLayoutComponent {
  readonly currentYear = new Date().getFullYear();
}
