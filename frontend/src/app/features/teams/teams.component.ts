import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ToastService } from '../../core/services/toast.service';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { AsesorExterno, TipoAsesorExterno } from '../../core/models/asesor-externo.model';

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [DragDropModule, RouterLink],
  templateUrl: './teams.component.html',
})
export class TeamsComponent {
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);

  readonly asesores = signal<AsesorExterno[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly lideres = computed(() =>
    this.asesores()
      .filter((a) => a.tipo === 'LIDER')
      .sort((a, b) => a.nombre.localeCompare(b.nombre)),
  );

  readonly independientes = computed(() =>
    this.asesores()
      .filter((a) => a.tipo === 'INDEPENDIENTE')
      .sort((a, b) => a.nombre.localeCompare(b.nombre)),
  );

  /** Tarjeta con el detalle/acciones del asesor en el que se hizo clic; null = cerrada. Un mismo
   * clic nunca dispara esto Y un arrastre a la vez: CDK solo cuenta como drag si el puntero se
   * mueve más allá de su umbral, un clic simple sigue disparando (click) normal. */
  readonly detalleAbierto = signal<AsesorExterno | null>(null);

  /** Qué zona de destino tiene el cursor encima ahora mismo mientras se arrastra algo ("L-{id}" =
   * línea 1 de ese líder, "2-{id}" = línea 2 de ese asesor, "IND" = independientes) — para
   * resaltarla de forma inequívoca (ver onHoverEntered/Exited). La clase que CDK aplica sola
   * (cdk-drop-list-receiving) se enciende por igual en TODAS las zonas conectadas mientras dura
   * cualquier arrastre, no solo en la que está debajo del cursor. */
  readonly dropZoneHover = signal<string | null>(null);

  onHoverEntered(zona: string): void {
    this.dropZoneHover.set(zona);
  }

  onHoverExited(zona: string): void {
    if (this.dropZoneHover() === zona) this.dropZoneHover.set(null);
  }

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.asesores.set(await this.asesoresExternosService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar los equipos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  linea1De(liderId: number): AsesorExterno[] {
    return this.asesores()
      .filter((a) => a.tipo === 'LINEA' && a.nivelLinea === 1 && a.liderDirectoId === liderId)
      .sort((a, b) => a.nombre.localeCompare(b.nombre));
  }

  linea2De(linea1Id: number): AsesorExterno[] {
    return this.asesores()
      .filter((a) => a.tipo === 'LINEA' && a.nivelLinea === 2 && a.liderDirectoId === linea1Id)
      .sort((a, b) => a.nombre.localeCompare(b.nombre));
  }

  // --- Tarjeta de detalle: clic en cualquier nodo del árbol o del pool de independientes ---

  abrirDetalle(asesor: AsesorExterno): void {
    this.detalleAbierto.set(asesor);
  }

  cerrarDetalle(): void {
    this.detalleAbierto.set(null);
  }

  async hacerLiderDesdeDetalle(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'LIDER', null);
    this.cerrarDetalle();
  }

  async quitarDeEquipoDesdeDetalle(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'INDEPENDIENTE', null);
    this.cerrarDetalle();
  }

  // --- Arrastrar y soltar: soltar sobre un líder lo pone en línea 1, sobre alguien de línea 1 lo
  // pone en línea 2, sobre "Independientes" lo saca del equipo. Los líderes no se arrastran (son
  // la raíz de su equipo); todo lo demás sí, y reasignar es tan simple como soltarlo en otro lado
  // sin tener que "quitarlo" primero. ---

  onDropEnLider(event: CdkDragDrop<AsesorExterno[]>, liderId: number): void {
    this.moverA(event, 'LINEA', liderId);
  }

  onDropEnLinea1(event: CdkDragDrop<AsesorExterno[]>, linea1Id: number): void {
    this.moverA(event, 'LINEA', linea1Id);
  }

  onDropEnIndependientes(event: CdkDragDrop<AsesorExterno[]>): void {
    this.moverA(event, 'INDEPENDIENTE', null);
  }

  private moverA(event: CdkDragDrop<AsesorExterno[]>, tipo: TipoAsesorExterno, liderDirectoId: number | null): void {
    // No depender solo de (cdkDropListExited) para apagar el resaltado de "aquí cae": al soltar,
    // ya no aplica sin importar si ese evento llegó a dispararse.
    this.dropZoneHover.set(null);
    if (event.previousContainer === event.container) return;
    const asesor = event.item.data as AsesorExterno;
    void this.guardarTipo(asesor, tipo, liderDirectoId);
  }

  private async guardarTipo(
    asesor: AsesorExterno,
    tipo: TipoAsesorExterno,
    liderDirectoId: number | null,
  ): Promise<void> {
    this.savingId.set(asesor.id);
    try {
      const actualizado = await this.asesoresExternosService.actualizar(asesor.id, {
        nombre: asesor.nombre,
        celular: asesor.celular,
        correo: asesor.correo,
        activo: asesor.activo,
        tipo,
        liderDirectoId,
      });
      this.asesores.update((lista) => lista.map((a) => (a.id === actualizado.id ? actualizado : a)));
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo actualizar el equipo.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }
}
