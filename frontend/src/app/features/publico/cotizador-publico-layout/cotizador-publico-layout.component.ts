import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Chrome de página para /cotizador-publico: sin sesión ni sidebar, así que le da al Cotizador
 * el fondo, el ancho centrado y el header/footer de marca que normalmente pone PanelLayoutComponent.
 * Sin esto el Cotizador se renderiza pegado al borde del navegador, sin aire ni identidad. */
@Component({
  selector: 'app-cotizador-publico-layout',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './cotizador-publico-layout.component.html',
})
export class CotizadorPublicoLayoutComponent {
  readonly currentYear = new Date().getFullYear();
}
