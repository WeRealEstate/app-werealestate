import { Injectable, signal } from '@angular/core';

/** aviso: algo pendiente o informativo que no es ni un éxito ni un error (p. ej. "Tienes 4 pendientes"). */
export type ToastType = 'success' | 'error' | 'aviso';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
  exiting: boolean;
}

const AUTO_DISMISS_MS = 3500;
const EXIT_ANIMATION_MS = 150;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 0;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.push(message, 'success');
  }

  error(message: string): void {
    this.push(message, 'error');
  }

  aviso(message: string): void {
    this.push(message, 'aviso');
  }

  dismiss(id: number): void {
    this.startExit(id);
  }

  private push(message: string, type: ToastType): void {
    const id = this.nextId++;
    this.toasts.update((lista) => [...lista, { id, message, type, exiting: false }]);
    setTimeout(() => this.startExit(id), AUTO_DISMISS_MS);
  }

  private startExit(id: number): void {
    this.toasts.update((lista) => lista.map((t) => (t.id === id ? { ...t, exiting: true } : t)));
    setTimeout(() => {
      this.toasts.update((lista) => lista.filter((t) => t.id !== id));
    }, EXIT_ANIMATION_MS);
  }
}
