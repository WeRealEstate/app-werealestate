import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../core/services/toast.service';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { AsesorExterno, TipoAsesorExterno } from '../../core/models/asesor-externo.model';

/** Una posición disponible para asignar a un asesor independiente: línea 1 de un líder, o línea 2
 * de alguien que ya está en línea 1. El value codifica el destino ("L-{liderId}" / "2-{linea1Id}")
 * porque un <select> solo puede llevar un valor. */
interface PosicionEquipo {
  value: string;
  label: string;
}

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [FormsModule, RouterLink],
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

  /** Qué posición tiene elegida cada independiente en su <select>, antes de darle "Asignar". */
  readonly seleccionAsignar = signal<Record<number, string>>({});

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

  /** Línea 1 de cada líder, más línea 2 de cada asesor que ya está en línea 1 — nunca línea 3
   * (ver backend AsesorExternoService.aplicarJerarquia). */
  get posicionesDisponibles(): PosicionEquipo[] {
    const opciones: PosicionEquipo[] = this.lideres().map((lider) => ({
      value: `L-${lider.id}`,
      label: `Línea 1 de ${lider.nombre}`,
    }));

    for (const asesor of this.asesores()) {
      if (asesor.tipo === 'LINEA' && asesor.nivelLinea === 1) {
        opciones.push({
          value: `2-${asesor.id}`,
          label: `Línea 2 de ${asesor.nombre} (equipo ${asesor.liderDirectoNombre})`,
        });
      }
    }
    return opciones;
  }

  setSeleccion(asesorId: number, valor: string): void {
    this.seleccionAsignar.update((mapa) => ({ ...mapa, [asesorId]: valor }));
  }

  async hacerLider(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'LIDER', null);
  }

  async asignar(asesor: AsesorExterno): Promise<void> {
    const valor = this.seleccionAsignar()[asesor.id];
    if (!valor) return;
    const liderDirectoId = Number(valor.slice(valor.indexOf('-') + 1));
    await this.guardarTipo(asesor, 'LINEA', liderDirectoId);
  }

  async quitarDeEquipo(asesor: AsesorExterno): Promise<void> {
    await this.guardarTipo(asesor, 'INDEPENDIENTE', null);
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
      this.setSeleccion(asesor.id, '');
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
