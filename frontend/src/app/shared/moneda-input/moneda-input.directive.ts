import { Directive, ElementRef, HostListener, forwardRef, inject, input, numberAttribute } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';

const DECIMALES_POR_DEFECTO = 2;

/** Input numérico con separador de miles mientras se escribe (1800.25 → "1,800.25"). El valor del
 * formulario sigue siendo un `number` (o null si está vacío), así que funciona igual con
 * `formControlName` y con `ngModel`. Pensado para `<input appMoneda>`: pone inputmode="decimal" en
 * lugar de type="number", que no puede mostrar comas.
 *
 * - `decimales`: cuántos decimales admite (2 por defecto; 0 = solo enteros; 4 para superficies).
 * - `min` / `max`: los mismos atributos de siempre; como type="text" ya no activa los validadores
 *   nativos de Angular, esta directiva los aplica ella misma (mismos errores `min` / `max`). */
@Directive({
  selector: 'input[appMoneda]',
  standalone: true,
  host: { type: 'text', inputmode: 'decimal', autocomplete: 'off' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MonedaInputDirective),
      multi: true,
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MonedaInputDirective),
      multi: true,
    },
  ],
})
export class MonedaInputDirective implements ControlValueAccessor, Validator {
  private readonly el = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  readonly decimales = input(DECIMALES_POR_DEFECTO, { transform: numberAttribute });
  readonly min = input<number | undefined, unknown>(undefined, { transform: numeroOpcional });
  readonly max = input<number | undefined, unknown>(undefined, { transform: numeroOpcional });

  validate(control: AbstractControl): ValidationErrors | null {
    const valor = control.value;
    if (valor === null || valor === undefined || valor === '' || Number.isNaN(Number(valor))) return null;
    const numero = Number(valor);
    const min = this.min();
    const max = this.max();
    if (min !== undefined && numero < min) return { min: { min, actual: numero } };
    if (max !== undefined && numero > max) return { max: { max, actual: numero } };
    return null;
  }

  private onChange: (valor: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(valor: number | null): void {
    this.el.value =
      valor === null || valor === undefined || Number.isNaN(valor)
        ? ''
        : formatear(String(valor), this.decimales());
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.el.disabled = deshabilitado;
  }

  @HostListener('blur')
  alSalir(): void {
    this.onTouched();
  }

  @HostListener('input')
  alEscribir(): void {
    const campo = this.el;
    const cursor = campo.selectionStart ?? campo.value.length;
    // Cuántos caracteres "reales" (dígitos y punto) había antes del cursor: las comas se reacomodan
    // al formatear, así que el cursor se recoloca contando esos, no por posición de texto.
    const significativosAntesDelCursor = limpiar(campo.value.slice(0, cursor), this.decimales()).length;

    const limpio = limpiar(campo.value, this.decimales());
    campo.value = formatear(limpio, this.decimales());

    let posicion = 0;
    let contados = 0;
    while (posicion < campo.value.length && contados < significativosAntesDelCursor) {
      if (campo.value[posicion] !== ',') contados++;
      posicion++;
    }
    campo.setSelectionRange(posicion, posicion);

    const numero = limpio === '' || limpio === '.' ? null : Number(limpio);
    this.onChange(numero === null || Number.isNaN(numero) ? null : numero);
  }
}

function numeroOpcional(valor: unknown): number | undefined {
  if (valor === null || valor === undefined || valor === '') return undefined;
  const n = Number(valor);
  return Number.isNaN(n) ? undefined : n;
}

/** Deja solo dígitos y a lo sumo un punto decimal, con máximo `decimalesMax` decimales (0 = sin punto). */
function limpiar(texto: string, decimalesMax: number): string {
  let resultado = texto.replace(/[^\d.]/g, '');
  const primerPunto = resultado.indexOf('.');
  if (primerPunto !== -1 && decimalesMax === 0) return resultado.slice(0, primerPunto);
  if (primerPunto !== -1) {
    const entero = resultado.slice(0, primerPunto);
    const decimales = resultado
      .slice(primerPunto + 1)
      .replace(/\./g, '')
      .slice(0, decimalesMax);
    resultado = `${entero}.${decimales}`;
  }
  return resultado;
}

/** "1800.25" → "1,800.25"; conserva un punto final ("1800." → "1,800.") para poder seguir escribiendo. */
function formatear(limpio: string, decimalesMax: number): string {
  const soloLimpio = limpiar(limpio, decimalesMax);
  const [entero, decimales] = soloLimpio.split('.');
  const enteroSinCeros = entero.replace(/^0+(?=\d)/, '');
  const conComas = enteroSinCeros.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return decimales === undefined ? conComas : `${conComas}.${decimales}`;
}

/** Para campos que formatean a mano (cotizador, promociones): mismas reglas que la directiva. */
export const limpiarNumero = limpiar;
export const formatearNumero = formatear;

/** Valor numérico de un texto ya limpio ("" o "." = 0). */
export function numeroDeTexto(limpio: string): number {
  return limpio === '' || limpio === '.' ? 0 : Number(limpio);
}
