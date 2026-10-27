import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideTrophy } from '@lucide/angular';
import { PosicionScore, RankingScore, TipoAsesorScore, conPosiciones } from '../../../core/models/score.model';
import { ScoreService } from '../../../core/services/score.service';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';

type Periodo = 'semana' | 'mes' | 'anio' | 'todo' | 'rango';
type FiltroTipo = 'TODOS' | TipoAsesorScore;

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Score de ventas: quién lleva más ventas en el periodo (ver backend ScoreService). Arranca en el mes en curso; el
 * podio muestra los tres primeros lugares y la tabla, a todos los que vendieron. */
@Component({
  selector: 'app-score',
  standalone: true,
  imports: [FormsModule, LucideTrophy, WeLoaderComponent],
  templateUrl: './score.component.html',
})
export class ScoreComponent {
  private readonly service = inject(ScoreService);

  readonly periodos: { valor: Periodo; etiqueta: string }[] = [
    { valor: 'semana', etiqueta: 'Esta semana' },
    { valor: 'mes', etiqueta: 'Este mes' },
    { valor: 'anio', etiqueta: 'Este año' },
    { valor: 'todo', etiqueta: 'Todo' },
    { valor: 'rango', etiqueta: 'Rango de fechas' },
  ];
  readonly filtros: { valor: FiltroTipo; etiqueta: string }[] = [
    { valor: 'TODOS', etiqueta: 'Todos' },
    { valor: 'INTERNO', etiqueta: 'Internos' },
    { valor: 'EXTERNO', etiqueta: 'Externos' },
  ];

  readonly periodo = signal<Periodo>('mes');
  readonly rangoDesde = signal('');
  readonly rangoHasta = signal('');
  readonly filtro = signal<FiltroTipo>('TODOS');

  readonly ranking = signal<RankingScore | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  /** Solo cuenta la respuesta de la última consulta: si cambian el periodo rápido, una vieja no pisa a la nueva. */
  private consulta = 0;

  /** El filtro Internos/Externos vuelve a numerar los lugares entre los que quedan. */
  readonly filas = computed<PosicionScore[]>(() => {
    const asesores = this.ranking()?.asesores ?? [];
    const filtro = this.filtro();
    return conPosiciones(filtro === 'TODOS' ? asesores : asesores.filter((a) => a.tipo === filtro));
  });
  readonly podio = computed(() => this.filas().slice(0, 3));

  readonly periodoLabel = computed(() => {
    const hoy = new Date();
    switch (this.periodo()) {
      case 'semana':
        return 'esta semana';
      case 'mes':
        return `${MESES[hoy.getMonth()]} ${hoy.getFullYear()}`;
      case 'anio':
        return `${hoy.getFullYear()}`;
      case 'todo':
        return 'todo el historial';
      case 'rango':
        return this.rangoDesde() && this.rangoHasta()
          ? `el periodo del ${fechaCorta(this.rangoDesde())} al ${fechaCorta(this.rangoHasta())}`
          : 'el rango elegido';
    }
  });

  constructor() {
    void this.cargar();
  }

  cambiarPeriodo(periodo: Periodo): void {
    if (periodo === this.periodo()) return;
    this.periodo.set(periodo);
    if (periodo === 'rango') {
      // Arranca con el mes en curso para que el rango ya muestre algo y solo haya que ajustarlo.
      if (!this.rangoDesde() || !this.rangoHasta()) {
        const { desde, hasta } = rangoDe('mes');
        this.rangoDesde.set(desde!);
        this.rangoHasta.set(hasta!);
      }
    }
    void this.cargar();
  }

  cambiarRango(campo: 'desde' | 'hasta', valor: string): void {
    (campo === 'desde' ? this.rangoDesde : this.rangoHasta).set(valor);
    void this.cargar();
  }

  /** Columnas del podio según cuántos hay (1 a 3), centrado cuando son menos de tres. */
  columnasPodio(): string {
    switch (this.podio().length) {
      case 1:
        return 'sm:mx-auto sm:max-w-xs sm:grid-cols-1';
      case 2:
        return 'sm:mx-auto sm:max-w-2xl sm:grid-cols-2';
      default:
        return 'sm:grid-cols-3';
    }
  }

  /** Con tres en el podio, en pantalla ancha el 1.º va al centro y más alto, el 2.º a la izquierda y el 3.º a la derecha. */
  lugarEnPodio(indice: number): string {
    if (this.podio().length < 3) return indice === 0 ? 'pb-6' : 'pb-4';
    return ['pb-6 sm:order-2 sm:pb-10', 'pb-4 sm:order-1', 'pb-4 sm:order-3'][indice];
  }

  claseTipo(tipo: TipoAsesorScore): string {
    return tipo === 'INTERNO' ? 'bg-we-primary/10 text-we-primary dark:bg-we-primary/20 dark:text-blue-300' : 'bg-surface-2 text-ink-muted';
  }

  /** Clases de la medalla de cada lugar del podio (oro, plata y bronce). */
  medalla(posicion: number): string {
    if (posicion === 1) return 'bg-amber-100 text-amber-700 ring-amber-300 dark:bg-amber-500/15 dark:text-amber-300 dark:ring-amber-400/40';
    if (posicion === 2) return 'bg-slate-100 text-slate-600 ring-slate-300 dark:bg-slate-500/20 dark:text-slate-300 dark:ring-slate-400/40';
    return 'bg-orange-100 text-orange-700 ring-orange-300 dark:bg-orange-500/15 dark:text-orange-300 dark:ring-orange-400/40';
  }

  private async cargar(): Promise<void> {
    const consulta = ++this.consulta;
    let desde: string | undefined;
    let hasta: string | undefined;
    if (this.periodo() === 'rango') {
      desde = this.rangoDesde() || undefined;
      hasta = this.rangoHasta() || undefined;
      if (desde && hasta && desde > hasta) {
        this.errorMessage.set('La fecha inicial no puede ser posterior a la final.');
        this.ranking.set(null);
        this.isLoading.set(false);
        return;
      }
    } else {
      ({ desde, hasta } = rangoDe(this.periodo()));
    }
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const ranking = await this.service.ranking(desde, hasta);
      if (consulta === this.consulta) this.ranking.set(ranking);
    } catch {
      if (consulta === this.consulta) {
        this.ranking.set(null);
        this.errorMessage.set('No se pudo cargar el score de ventas. Intenta de nuevo.');
      }
    } finally {
      if (consulta === this.consulta) this.isLoading.set(false);
    }
  }
}

/** Fechas yyyy-MM-dd del periodo según la fecha del dispositivo: la semana va de lunes a domingo. */
function rangoDe(periodo: Periodo): { desde?: string; hasta?: string } {
  const hoy = new Date();
  switch (periodo) {
    case 'semana': {
      const lunes = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - ((hoy.getDay() + 6) % 7));
      const domingo = new Date(lunes.getFullYear(), lunes.getMonth(), lunes.getDate() + 6);
      return { desde: isoDate(lunes), hasta: isoDate(domingo) };
    }
    case 'mes':
      return {
        desde: isoDate(new Date(hoy.getFullYear(), hoy.getMonth(), 1)),
        hasta: isoDate(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0)),
      };
    case 'anio':
      return { desde: `${hoy.getFullYear()}-01-01`, hasta: `${hoy.getFullYear()}-12-31` };
    default:
      return {};
  }
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** "2026-10-05" → "05/10/2026". */
function fechaCorta(iso: string): string {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}
