import { Directive, ElementRef, Input, OnChanges, SimpleChanges } from '@angular/core';
import { animate } from 'motion';

/**
 * Anima con un "pop" sutil cada vez que cambia el valor asociado.
 * Pensado para montos que se recalculan (mensualidad, totales)
 * sin que el bloque que los contiene entre/salga del DOM.
 */
@Directive({
  selector: '[weValuePulse]',
  standalone: true
})
export class ValuePulseDirective implements OnChanges {

  @Input('weValuePulse') value: unknown;

  private hasInitialized = false;

  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.hasInitialized) {
      this.hasInitialized = true;
      return;
    }

    const change = changes['value'];

    if (!change || change.currentValue === change.previousValue) {
      return;
    }

    animate(
      this.el.nativeElement,
      { scale: [1, 1.08, 1] },
      {
        duration: 0.4,
        ease: [0.22, 1, 0.36, 1]
      }
    );
  }
}
