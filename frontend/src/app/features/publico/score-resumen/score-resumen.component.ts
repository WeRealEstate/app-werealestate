import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideChevronRight, LucideTrophy } from '@lucide/angular';
import { PosicionScore, conPosiciones } from '../../../core/models/score.model';
import { ScoreService } from '../../../core/services/score.service';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';

const LUGARES_EN_RESUMEN = 5;

/** Resumen del Score de ventas debajo de la portada del cotizador público: los cinco primeros lugares de todo el
 * historial y el enlace al score completo (/cotizador-publico/score). Si el dispositivo tiene guardado el PIN de un
 * asesor, marca "Tú" o, si no está entre los cinco, dice en qué lugar va. Sin ninguna venta todavía (o si no carga),
 * no se muestra: el cotizador sigue igual. */
@Component({
  selector: 'app-score-resumen',
  standalone: true,
  imports: [RouterLink, LucideTrophy, LucideChevronRight],
  templateUrl: './score-resumen.component.html',
})
export class ScoreResumenComponent {
  private readonly service = inject(ScoreService);
  private readonly weFormsPublico = inject(WeFormsPublicoService);

  private readonly filas = signal<PosicionScore[]>([]);
  readonly top = computed(() => this.filas().slice(0, LUGARES_EN_RESUMEN));
  readonly miClave = signal<string | null>(null);
  /** Mi fila en todo el ranking (null si el dispositivo no tiene PIN o no tengo ventas). */
  readonly miFila = computed(() => {
    const clave = this.miClave();
    return clave ? (this.filas().find((f) => f.clave === clave) ?? null) : null;
  });
  readonly estoyEnElTop = computed(() => this.top().some((f) => f.clave === this.miClave()));
  /** Con un empate en el último lugar del resumen, alguien puede ir en ese mismo lugar sin caber en las cinco filas. */
  readonly empatoConElTop = computed(() => {
    const yo = this.miFila();
    const ultimo = this.top().at(-1);
    return !!yo && !!ultimo && yo.posicion <= ultimo.posicion;
  });

  constructor() {
    void this.cargar();
  }

  /** Oro, plata y bronce para los tres primeros lugares; neutro para el resto. */
  medalla(posicion: number): string {
    if (posicion === 1) return 'bg-amber-100 text-amber-700 ring-amber-300 dark:bg-amber-500/15 dark:text-amber-300 dark:ring-amber-400/40';
    if (posicion === 2) return 'bg-slate-100 text-slate-600 ring-slate-300 dark:bg-slate-500/20 dark:text-slate-300 dark:ring-slate-400/40';
    if (posicion === 3) return 'bg-orange-100 text-orange-700 ring-orange-300 dark:bg-orange-500/15 dark:text-orange-300 dark:ring-orange-400/40';
    return 'bg-surface-2 text-ink-muted ring-border';
  }

  private async cargar(): Promise<void> {
    try {
      // Sin fechas: todo el historial.
      const publico = await this.service.rankingPublico(undefined, undefined, this.weFormsPublico.pin());
      this.filas.set(conPosiciones(publico.ranking.asesores));
      this.miClave.set(publico.miClave);
      // El PIN guardado ya no sirve: se olvida para no volver a mandarlo (igual que la página del score).
      if (publico.pinRechazado) this.weFormsPublico.guardarPin(null);
    } catch {
      // Sin resumen: no estorba al cotizador.
    }
  }
}
