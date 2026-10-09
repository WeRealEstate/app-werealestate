import { Component, computed, input, model } from '@angular/core';
import { LucideX } from '@lucide/angular';
import { ClienteLista } from '../../core/models/cliente.model';
import { ClienteSelectorComponent } from '../cliente-selector/cliente-selector.component';

export const MAX_COPROPIETARIOS = 4;

/** Copropiedad de una venta: hasta 4 clientes más, además del principal (5 en total). */
@Component({
  selector: 'app-copropietarios-editor',
  imports: [ClienteSelectorComponent, LucideX],
  templateUrl: './copropietarios-editor.component.html',
})
export class CopropietariosEditorComponent {
  /** Casillas de copropietarios; una casilla en null está vacía (todavía sin elegir). */
  readonly copropietarios = model<(ClienteLista | null)[]>([]);
  /** El cliente principal de la venta, para avisar si se repite. */
  readonly principalId = input<number | null>(null);

  readonly max = MAX_COPROPIETARIOS;
  readonly puedeAgregar = computed(() => this.copropietarios().length < MAX_COPROPIETARIOS);

  /** Ids repetidos (entre sí o con el principal): se marcan en rojo y bloquean el guardado en el padre. */
  readonly repetidos = computed(() => {
    const vistos = new Set<number>();
    const principal = this.principalId();
    if (principal !== null) vistos.add(principal);
    const dup = new Set<number>();
    for (const c of this.copropietarios()) {
      if (!c) continue;
      if (vistos.has(c.id)) dup.add(c.id);
      vistos.add(c.id);
    }
    return dup;
  });

  agregar(): void {
    if (this.puedeAgregar()) this.copropietarios.update((l) => [...l, null]);
  }

  quitar(i: number): void {
    this.copropietarios.update((l) => l.filter((_, k) => k !== i));
  }

  elegir(i: number, c: ClienteLista | null): void {
    this.copropietarios.update((l) => l.map((x, k) => (k === i ? c : x)));
  }
}

/** Ids de los copropietarios ya elegidos (ignora casillas vacías). */
export function idsCopropietarios(lista: (ClienteLista | null)[]): number[] {
  return lista.filter((c): c is ClienteLista => c !== null).map((c) => c.id);
}

/** true si hay clientes repetidos entre los copropietarios y el principal. */
export function hayCopropietariosRepetidos(lista: (ClienteLista | null)[], principalId: number | null): boolean {
  const vistos = new Set<number>(principalId !== null ? [principalId] : []);
  for (const c of lista) {
    if (!c) continue;
    if (vistos.has(c.id)) return true;
    vistos.add(c.id);
  }
  return false;
}
