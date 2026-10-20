import { Component, computed, input, output } from '@angular/core';
import { LucideImages } from '@lucide/angular';
import { MaterialImagen, MaterialItem } from '../../core/models/material.model';
import { ImagenDiferidaComponent } from '../imagen-diferida/imagen-diferida.component';

/** Portada de una galería en su tarjeta: hasta 4 miniaturas en mosaico (1, 2, 3 o 4 celdas según cuántas haya) y
 * un "+N" cuando son más. Tocar una celda avisa con `abrir` y el índice de esa imagen, para que el visor empiece
 * ahí. `cargarMiniatura` cambia entre el panel (sesión) y el plano público (PIN). */
@Component({
  selector: 'app-material-galeria-mosaico',
  standalone: true,
  imports: [ImagenDiferidaComponent, LucideImages],
  template: `
    @if (primeras().length === 0) {
      <button
        type="button"
        (click)="abrir.emit(0)"
        [attr.aria-label]="'Galería vacía: ' + item().titulo"
        class="flex aspect-[16/10] w-full flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-border bg-surface-2 text-ink-muted"
      >
        <svg lucideImages [size]="30" aria-hidden="true"></svg>
        <span class="text-xs font-semibold">Galería sin imágenes</span>
      </button>
    } @else {
      <div
        class="grid aspect-[16/10] w-full gap-0.5 overflow-hidden rounded-lg border border-border bg-border"
        [class.grid-cols-1]="primeras().length === 1"
        [class.grid-cols-2]="primeras().length > 1"
        [class.grid-rows-2]="primeras().length > 2"
        data-testid="material-mosaico"
      >
        @for (img of primeras(); track img.id; let i = $index) {
          <button
            type="button"
            (click)="abrir.emit(i)"
            [attr.aria-label]="'Ver imagen ' + (i + 1) + ' de ' + item().imagenes.length + ' de ' + item().titulo"
            class="group relative block min-h-0 overflow-hidden focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-we-primary"
            [class.row-span-2]="primeras().length === 3 && i === 0"
          >
            <app-imagen-diferida
              [cargar]="cargadorDe(img)"
              class="transition-transform duration-300 group-hover:scale-105 motion-reduce:transition-none"
            />
            @if (i === primeras().length - 1 && restantes() > 0) {
              <span class="absolute inset-0 flex items-center justify-center bg-black/50 text-lg font-semibold text-white">
                +{{ restantes() }}
              </span>
            }
          </button>
        }
      </div>
    }
  `,
})
export class MaterialGaleriaMosaicoComponent {
  readonly item = input.required<MaterialItem>();
  readonly cargarMiniatura = input.required<(m: MaterialItem, imagen: MaterialImagen) => Promise<Blob>>();
  readonly abrir = output<number>();

  readonly primeras = computed(() => this.item().imagenes.slice(0, 4));
  readonly restantes = computed(() => Math.max(0, this.item().imagenes.length - 4));

  cargadorDe(imagen: MaterialImagen): () => Promise<Blob> {
    return () => this.cargarMiniatura()(this.item(), imagen);
  }
}
