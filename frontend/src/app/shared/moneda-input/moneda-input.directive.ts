import { Directive, ElementRef, HostListener, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

const DECIMALES_MAX = 2;

/** Input de dinero con separador de miles mientras se escribe (1800.25 → "1,800.25"). El valor del
 * formulario sigue siendo un `number` (o null si está vacío), así que funciona igual con
 * `formControlName` y con `ngModel`. Pensado para `<input appMoneda>`: pone inputmode="decimal" en
 * lugar de type="number", que no puede mostrar comas. */
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
  ],
})
export class MonedaInputDirective implements ControlValueAccessor {
  private readonly el = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  private onChange: (valor: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(valor: number | null): void {
    this.el.value =
      valor === null || valor === undefined || Number.isNaN(valor) ? '' : formatear(String(valor));
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
    const significativosAntesDelCursor = limpiar(campo.value.slice(0, cursor)).length;

    const limpio = limpiar(campo.value);
    campo.value = formatear(limpio);

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

/** Deja solo dígitos y a lo sumo un punto decimal, con máximo DECIMALES_MAX decimales. */
function limpiar(texto: string): string {
  let resultado = texto.replace(/[^\d.]/g, '');
  const primerPunto = resultado.indexOf('.');
  if (primerPunto !== -1) {
    const entero = resultado.slice(0, primerPunto);
    const decimales = resultado
      .slice(primerPunto + 1)
      .replace(/\./g, '')
      .slice(0, DECIMALES_MAX);
    resultado = `${entero}.${decimales}`;
  }
  return resultado;
}

/** "1800.25" → "1,800.25"; conserva un punto final ("1800." → "1,800.") para poder seguir escribiendo. */
function formatear(limpio: string): string {
  const soloLimpio = limpiar(limpio);
  const [entero, decimales] = soloLimpio.split('.');
  const enteroSinCeros = entero.replace(/^0+(?=\d)/, '');
  const conComas = enteroSinCeros.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return decimales === undefined ? conComas : `${conComas}.${decimales}`;
}
