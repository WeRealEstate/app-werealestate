import { Directive, ElementRef, Input, OnInit } from '@angular/core';
import { animate } from 'motion';

/**
 * Animación de entrada para bloques que se insertan/quitan del DOM
 * (p. ej. dentro de un @if). Se dispara una sola vez, al montarse.
 */
@Directive({
  selector: '[weFadeIn]',
  standalone: true
})
export class FadeInDirective implements OnInit {

  @Input() weFadeInDelay = 0;

  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngOnInit(): void {
    const element = this.el.nativeElement;

    animate(
      element,
      {
        opacity: [0, 1],
        y: [12, 0]
      },
      {
        duration: 0.45,
        delay: this.weFadeInDelay,
        ease: [0.22, 1, 0.36, 1]
      }
    );
  }
}
