import { Component, computed, input, output, signal } from '@angular/core';

/** El PIN de 4 letras/números del asesor, en 4 casillas (pega el PIN completo, avanza solo y retrocede con Borrar).
 * No verifica nada: avisa con `entrar` y quien lo usa llama a la API y muestra el `error`. */
@Component({
  selector: 'app-pin-publico',
  standalone: true,
  templateUrl: './pin-publico.component.html',
})
export class PinPublicoComponent {
  readonly error = input<string | null>(null);
  readonly cargando = input(false);
  readonly titulo = input('Acceso de asesor');
  readonly texto = input('Escribe tu PIN de 4 caracteres para continuar.');
  readonly entrar = output<string>();

  readonly casillas = signal<string[]>(['', '', '', '']);
  readonly pin = computed(() => this.casillas().join(''));

  escribir(indice: number, valor: string, evento: Event): void {
    const limpio = valor.toUpperCase().replace(/[^A-Z0-9]/g, '');
    const casillas = [...this.casillas()];
    const destino = evento.target as HTMLInputElement;
    if (!limpio) {
      casillas[indice] = '';
      this.casillas.set(casillas);
      destino.value = '';
      return;
    }
    for (let i = 0; i < limpio.length && indice + i < 4; i++) casillas[indice + i] = limpio[i];
    this.casillas.set(casillas);
    const inputs = Array.from((destino.closest('[data-pin]') as HTMLElement).querySelectorAll('input'));
    inputs.forEach((el, i) => (el.value = casillas[i]));
    inputs[Math.min(indice + limpio.length, 3)]?.focus();
  }

  tecla(indice: number, evento: KeyboardEvent): void {
    const input = evento.target as HTMLInputElement;
    if (evento.key === 'Backspace' && !input.value && indice > 0) {
      const inputs = Array.from((input.closest('[data-pin]') as HTMLElement).querySelectorAll('input'));
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

  confirmar(): void {
    if (this.pin().length === 4 && !this.cargando()) this.entrar.emit(this.pin());
  }
}
