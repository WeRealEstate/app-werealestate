import { Directive, ElementRef, HostListener } from '@angular/core';
import { animate } from 'motion';

/**
 * Micro-interacción de hover/press con física de resorte,
 * pensada para botones y tarjetas seleccionables.
 */
@Directive({
  selector: '[weMotionPress]',
  standalone: true
})
export class PressDirective {

  constructor(private readonly el: ElementRef<HTMLButtonElement>) {}

  private get isDisabled(): boolean {
    return this.el.nativeElement.disabled === true;
  }

  @HostListener('pointerenter')
  onPointerEnter(): void {
    if (this.isDisabled) return;
    this.playScale(1.02);
  }

  @HostListener('pointerleave')
  onPointerLeave(): void {
    this.playScale(1);
  }

  @HostListener('pointerdown')
  onPointerDown(): void {
    if (this.isDisabled) return;
    this.playScale(0.97);
  }

  @HostListener('pointerup')
  onPointerUp(): void {
    if (this.isDisabled) return;
    this.playScale(1.02);
  }

  private playScale(scale: number): void {
    animate(
      this.el.nativeElement,
      { scale },
      { type: 'spring', stiffness: 420, damping: 28 }
    );
  }
}
