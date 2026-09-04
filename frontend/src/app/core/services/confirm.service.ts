import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  titulo?: string;
  mensaje: string;
  textoConfirmar?: string;
  textoCancelar?: string;
  /** Resalta el botón de confirmar en rojo, para acciones destructivas o irreversibles. */
  peligroso?: boolean;
}

interface ConfirmState extends Required<ConfirmOptions> {}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmState | null>(null);

  private resolver: ((valor: boolean) => void) | null = null;

  confirm(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.resolver = resolve;
      this.request.set({
        titulo: options.titulo ?? '¿Estás seguro?',
        mensaje: options.mensaje,
        textoConfirmar: options.textoConfirmar ?? 'Confirmar',
        textoCancelar: options.textoCancelar ?? 'Cancelar',
        peligroso: options.peligroso ?? false,
      });
    });
  }

  responder(valor: boolean): void {
    this.resolver?.(valor);
    this.resolver = null;
    this.request.set(null);
  }
}
