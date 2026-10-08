import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CargaInicialService } from './core/services/carga-inicial.service';
import { AccesibilidadTecladoService } from './core/services/accesibilidad-teclado.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
})
export class App {
  constructor() {
    inject(AccesibilidadTecladoService).iniciar();
    inject(CargaInicialService);
  }
}
