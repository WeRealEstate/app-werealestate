import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { buscarEnLista, canonicoEnLista } from '../../core/utils/lista';

/** Elegir un elemento de una lista cerrada (países, estados) con buscador: no se puede escribir uno que no
 * exista. Funciona con formControlName / ngModel; el valor es el elemento elegido o '' si no hay ninguno. */
@Component({
  selector: 'app-lista-selector',
  templateUrl: './lista-selector.component.html',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ListaSelectorComponent), multi: true }],
})
export class ListaSelectorComponent implements ControlValueAccessor {
  /** Elementos que se pueden elegir, en el orden en que se muestran. */
  readonly lista = input.required<readonly string[]>();
  readonly inputId = input<string | null>(null);
  readonly placeholder = input('Escribe para buscar…');
  /** Borde rojo cuando el padre marca el campo como inválido. */
  readonly invalido = input(false);

  readonly valor = signal('');
  readonly texto = signal('');
  readonly abierto = signal(false);
  readonly deshabilitado = signal(false);
  readonly resaltado = signal(0);

  readonly opciones = () => buscarEnLista(this.lista(), this.texto() === this.valor() ? '' : this.texto());

  private alCambiar: (v: string) => void = () => {};
  private alTocar: () => void = () => {};

  writeValue(v: string | null): void {
    const limpio = v ?? '';
    this.valor.set(limpio);
    this.texto.set(limpio);
  }
  registerOnChange(fn: (v: string) => void): void {
    this.alCambiar = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }
  setDisabledState(d: boolean): void {
    this.deshabilitado.set(d);
  }

  abrir(): void {
    if (this.deshabilitado()) return;
    this.abierto.set(true);
    this.resaltado.set(0);
  }

  escribir(texto: string): void {
    this.texto.set(texto);
    this.abierto.set(true);
    this.resaltado.set(0);
    // Borrar todo el texto equivale a quitar la selección.
    if (texto.trim() === '' && this.valor() !== '') this.fijar('');
  }

  elegir(elemento: string): void {
    this.fijar(elemento);
    this.texto.set(elemento);
    this.abierto.set(false);
  }

  /** Al salir del campo: si lo escrito está en la lista se toma; si no, vuelve a lo que estaba elegido. */
  cerrar(): void {
    const exacto = canonicoEnLista(this.lista(), this.texto());
    if (exacto) {
      this.fijar(exacto);
      this.texto.set(exacto);
    } else {
      this.texto.set(this.valor());
    }
    this.abierto.set(false);
    this.alTocar();
  }

  teclado(evento: KeyboardEvent): void {
    const lista = this.opciones();
    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      this.abierto.set(true);
      this.resaltado.set(Math.min(this.resaltado() + 1, Math.max(lista.length - 1, 0)));
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.resaltado.set(Math.max(this.resaltado() - 1, 0));
    } else if (evento.key === 'Enter' && this.abierto()) {
      const elegido = lista[this.resaltado()];
      if (elegido) {
        evento.preventDefault();
        this.elegir(elegido);
      }
    } else if (evento.key === 'Escape') {
      this.texto.set(this.valor());
      this.abierto.set(false);
    }
  }

  private fijar(v: string): void {
    if (v === this.valor()) return;
    this.valor.set(v);
    this.alCambiar(v);
  }
}
