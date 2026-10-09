import { Directive, ElementRef, HostListener, inject } from '@angular/core';

/** Al salir de un campo de correo lo deja sin espacios y en minúsculas (" Juan@Gmail.COM " →
 * "juan@gmail.com"). Se activa solo en todo `<input type="email">` de los componentes que lo importan;
 * avisa al formulario con un evento `input`, así funciona igual con formControlName y ngModel. */
@Directive({
  selector: 'input[type=email]',
  standalone: true,
})
export class CorreoInputDirective {
  private readonly el = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  /** Solo se normaliza lo que la persona escribió: abrir y salir de un correo ya guardado no lo toca. */
  private editado = false;

  @HostListener('input', ['$event'])
  alEscribir(evento: Event): void {
    if (evento.isTrusted) this.editado = true;
  }

  @HostListener('blur')
  normalizar(): void {
    if (!this.editado) return;
    this.editado = false;
    const limpio = this.el.value.replace(/\s+/g, '').toLowerCase();
    if (limpio === this.el.value) return;
    this.el.value = limpio;
    this.el.dispatchEvent(new Event('input', { bubbles: true }));
  }
}
