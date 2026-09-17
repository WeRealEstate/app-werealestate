import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Desarrollo } from '../../../core/models/lead.model';
import {
  ESTADO_LOTE_LABELS,
  ESTADO_LOTE_PIN_CLASSES,
  EstadoLote,
  Lote,
  PlanoDesarrollo,
} from '../../../core/models/lote.model';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { ToastService } from '../../../core/services/toast.service';

/** Editor + visor del plano interactivo de un desarrollo: un admin sube la imagen (el plano de
 * ventas que ya usan en marketing) y ubica el pin de cada lote con un par de clics; a partir de
 * ahí el color de cada pin sigue el estado real del lote, para todos los roles. No requiere
 * coordenadas geográficas ni el archivo original (CAD/KML): solo dónde cae cada lote dentro de esa
 * imagen. */
@Component({
  selector: 'app-plano',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './plano.component.html',
})
export class PlanoComponent {
  private readonly lotesService = inject(LotesService);
  private readonly leadsService = inject(LeadsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly pinClases = ESTADO_LOTE_PIN_CLASSES;
  readonly estadosLote = Object.keys(ESTADO_LOTE_LABELS) as EstadoLote[];
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly desarrolloId = signal<number | null>(null);
  readonly plano = signal<PlanoDesarrollo | null>(null);
  readonly isLoading = signal(false);
  readonly subiendoPlano = signal(false);

  readonly modoEdicion = signal(false);
  readonly loteAUbicarId = signal<number | null>(null);
  readonly loteActivo = signal<Lote | null>(null);

  readonly lotesSinUbicar = computed(() => this.plano()?.lotes.filter((l) => l.mapaX == null) ?? []);
  readonly totalLotes = computed(() => this.plano()?.lotes.length ?? 0);
  readonly totalUbicados = computed(() => this.totalLotes() - this.lotesSinUbicar().length);

  constructor() {
    this.leadsService.listarDesarrollos().then((desarrollos) => {
      this.desarrollos.set(desarrollos);
      if (desarrollos.length > 0) {
        this.desarrolloId.set(desarrollos[0].id);
        this.cargarMapa();
      }
    });
  }

  onDesarrolloChange(valor: string): void {
    this.desarrolloId.set(valor === '' ? null : +valor);
    this.loteActivo.set(null);
    this.loteAUbicarId.set(null);
    this.cargarMapa();
  }

  async cargarMapa(): Promise<void> {
    const id = this.desarrolloId();
    if (id === null) return;
    this.isLoading.set(true);
    try {
      const plano = await this.lotesService.obtenerMapa(id);
      this.plano.set(plano);
    } catch {
      this.toast.error('No se pudo cargar el plano de este desarrollo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  toggleModoEdicion(): void {
    this.modoEdicion.update((v) => !v);
    this.loteAUbicarId.set(null);
    this.loteActivo.set(null);
  }

  async onArchivoPlanoSeleccionado(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    const id = this.desarrolloId();
    if (!archivo || id === null) return;

    this.subiendoPlano.set(true);
    try {
      await this.leadsService.subirPlano(id, archivo);
      await this.cargarMapa();
      this.toast.success('Plano actualizado.');
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo subir el plano. Intenta de nuevo.',
      );
    } finally {
      this.subiendoPlano.set(false);
      input.value = '';
    }
  }

  onSeleccionarLoteAUbicar(valor: string): void {
    this.loteAUbicarId.set(valor === '' ? null : +valor);
  }

  /** En modo edición, con un lote elegido, un clic sobre la imagen fija su posición exacta (en %
   * del ancho/alto, no en píxeles, para que sirva a cualquier tamaño de pantalla) y la guarda de
   * inmediato — el lote sigue seleccionado después para poder corregir con otro clic si hace
   * falta; Enter confirma y pasa al siguiente lote sin ubicar (ver onEnterKey). */
  async onClickImagen(event: MouseEvent): Promise<void> {
    if (!this.modoEdicion()) return;
    const loteId = this.loteAUbicarId();
    if (loteId === null) return;

    const contenedor = event.currentTarget as HTMLElement;
    const rect = contenedor.getBoundingClientRect();
    const x = Math.min(100, Math.max(0, ((event.clientX - rect.left) / rect.width) * 100));
    const y = Math.min(100, Math.max(0, ((event.clientY - rect.top) / rect.height) * 100));

    try {
      const actualizado = await this.lotesService.actualizarPosicionMapa(loteId, x, y);
      this.plano.update((p) => (p ? { ...p, lotes: p.lotes.map((l) => (l.id === loteId ? actualizado : l)) } : p));
    } catch {
      this.toast.error('No se pudo ubicar el lote en el plano.');
    }
  }

  /** Enter avanza al siguiente lote sin ubicar (o al primero, si no hay ninguno elegido todavía),
   * saltándose los que ya tienen pin; así el flujo completo es clic-clic-Enter-clic-Enter... sin
   * volver a tocar el selector. Se ignora si el foco está en el <select> para no interferir con su
   * navegación nativa por teclado. */
  @HostListener('document:keydown.enter', ['$event'])
  onEnterKey(event: Event): void {
    if (!this.modoEdicion() || document.activeElement instanceof HTMLSelectElement) return;
    event.preventDefault();
    this.avanzarSiguienteLote();
  }

  /** Botón "Siguiente lote" en la plantilla: mismo salto que Enter, para quien prefiera el mouse. */
  avanzarSiguienteLote(): void {
    const lotes = this.plano()?.lotes ?? [];
    if (lotes.length === 0) return;

    const actualId = this.loteAUbicarId();
    const indiceActual = actualId === null ? -1 : lotes.findIndex((l) => l.id === actualId);
    for (let i = 1; i <= lotes.length; i++) {
      const candidato = lotes[(indiceActual + i) % lotes.length];
      if (candidato.mapaX == null) {
        this.loteAUbicarId.set(candidato.id);
        return;
      }
    }
    this.loteAUbicarId.set(null);
    this.toast.success('Todos los lotes están ubicados.');
  }

  onClickPin(lote: Lote, event: MouseEvent): void {
    event.stopPropagation();
    if (this.modoEdicion()) {
      this.loteAUbicarId.set(lote.id);
    } else {
      this.loteActivo.set(this.loteActivo()?.id === lote.id ? null : lote);
    }
  }

  async quitarPin(lote: Lote): Promise<void> {
    try {
      const actualizado = await this.lotesService.actualizarPosicionMapa(lote.id, null, null);
      this.plano.update((p) =>
        p ? { ...p, lotes: p.lotes.map((l) => (l.id === lote.id ? actualizado : l)) } : p,
      );
      this.loteActivo.set(null);
    } catch {
      this.toast.error('No se pudo quitar el pin de este lote.');
    }
  }
}
