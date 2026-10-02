import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AccesibilidadTecladoService } from './core/services/accesibilidad-teclado.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
})
export class App {
  constructor() {
    inject(AccesibilidadTecladoService).iniciar();
  }
}
