import { Component, effect, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NumeracionVenta } from '../../core/models/venta.model';
import { VentasService } from '../../core/services/ventas.service';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

const ESPERA_CONSULTA_MS = 300;

/** Campo "Número de venta" del formulario de alta y de modificación. En alta, vacío = automático (el
 * siguiente: mayor + 1); en modificación arranca con el número actual (`numeroActual`). Mientras se
 * escribe avisa si el número ya lo tiene otra venta (y cuántas se recorrerían +1 al guardar) o si deja
 * huecos. El valor (`numero`) es null cuando no hay nada escrito (= automático / sin cambios). */
@Component({
  selector: 'app-numero-venta',
  standalone: true,
  imports: [FormsModule, MonedaInputDirective],
  templateUrl: './numero-venta.component.html',
})
export class NumeroVentaComponent {
  private readonly ventasService = inject(VentasService);

  readonly numero = model<number | null>(null);
  /** Edición: el id de la venta (para no avisar que "choca" consigo misma) y su número actual. */
  readonly excluirId = input<number | null>(null);
  readonly numeroActual = input<number | null>(null);

  readonly info = signal<NumeracionVenta | null>(null);
  readonly siguiente = signal<number | null>(null);
  readonly consultando = signal(false);

  private temporizador: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect((onCleanup) => {
      const n = this.numero();
      const excluir = this.excluirId();
      clearTimeout(this.temporizador);
      this.info.set(null);
      this.temporizador = setTimeout(() => void this.consultar(n, excluir), n ? ESPERA_CONSULTA_MS : 0);
      onCleanup(() => clearTimeout(this.temporizador));
    });
  }

  private async consultar(n: number | null, excluir: number | null): Promise<void> {
    this.consultando.set(true);
    try {
      const r = await this.ventasService.numeracion(n, excluir);
      this.siguiente.set(r.siguiente);
      // Solo vale si el campo sigue igual a como se consultó.
      if (this.numero() === n) this.info.set(r);
    } catch {
      this.info.set(null);
    } finally {
      this.consultando.set(false);
    }
  }

  /** true = el número escrito es el que la venta ya tiene (en modificación): no hay nada que avisar. */
  esElMismo(): boolean {
    const n = this.numero();
    return n !== null && n === this.numeroActual();
  }

  fechaCorta(iso: string | null): string {
    if (!iso) return '';
    const [a, m, d] = iso.split('-');
    return `${d}/${m}/${a}`;
  }
}
