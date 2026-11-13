import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** Ventana "Acceso de asesor" del plano público: el PIN en 4 casillas (acepta pegar el PIN completo, avanza solo
 * y retrocede con Borrar). No verifica nada: avisa con `entrar` y el plano llama a la API y muestra el `error`.
 * Se crea cada vez que se abre, así que siempre arranca con las casillas vacías. */
@Component({
  selector: 'app-acceso-asesor-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './acceso-asesor-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccesoAsesorModalComponent {
  readonly error = input<string | null>(null);
  readonly verificando = input(false);
  /** Se escribió algo en una casilla: el error anterior ya no aplica. */
  readonly escribio = output<void>();
  readonly entrar = output<string>();
  readonly cerrar = output<void>();

  readonly casillas = signal<string[]>(['', '', '', '']);
  readonly pin = computed(() => this.casillas().join(''));

  /** Escribe en una casilla (acepta pegar el PIN completo) y pasa a la siguiente. */
  escribir(indice: number, valor: string, evento: Event): void {
    const limpio = valor.toUpperCase().replace(/[^A-Z0-9]/g, '');
    const casillas = [...this.casillas()];
    if (!limpio) {
      casillas[indice] = '';
      this.casillas.set(casillas);
      (evento.target as HTMLInputElement).value = '';
      return;
    }
    for (let i = 0; i < limpio.length && indice + i < 4; i++) casillas[indice + i] = limpio[i];
    this.casillas.set(casillas);
    this.escribio.emit();
    const inputs = this.casillasDelPin(evento.target as HTMLInputElement);
    inputs.forEach((el, i) => (el.value = casillas[i]));
    inputs[Math.min(indice + limpio.length, 3)]?.focus();
  }

  tecla(indice: number, evento: KeyboardEvent): void {
    const input = evento.target as HTMLInputElement;
    if (evento.key === 'Backspace' && !input.value && indice > 0) {
      const inputs = this.casillasDelPin(input);
      const casillas = [...this.casillas()];
      casillas[indice - 1] = '';
      this.casillas.set(casillas);
      inputs[indice - 1].value = '';
      inputs[indice - 1].focus();
      evento.preventDefault();
    } else if (evento.key === 'ArrowLeft' && indice > 0) {
      (input.previousElementSibling as HTMLInputElement | null)?.focus();
    } else if (evento.key === 'ArrowRight' && indice < 3) {
      (input.nextElementSibling as HTMLInputElement | null)?.focus();
    }
  }

  private casillasDelPin(casilla: HTMLInputElement): HTMLInputElement[] {
    return Array.from((casilla.closest('[data-pin]') as HTMLElement).querySelectorAll('input'));
  }
}
