import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventosCalendarioService } from '../../../core/services/eventos-calendario.service';
import { TareasService } from '../../../core/services/tareas.service';
import { LeadsService } from '../../../core/services/leads.service';
import { ToastService } from '../../../core/services/toast.service';
import { EventoCalendario } from '../../../core/models/evento-calendario.model';
import { TIPO_SEGUIMIENTO_LABELS } from '../../../core/models/lead.model';
import { etiquetaDuracion } from '../../../core/utils/fecha-hora';

type ItemTipo = 'PERSONAL' | 'TAREA' | 'SEGUIMIENTO';

interface CalendarItem {
  tipo: ItemTipo;
  id: number;
  iso: string;
  titulo: string;
  subtitulo: string | null;
  leadId: number | null;
  completada: boolean;
}

interface CalendarDay {
  date: Date;
  iso: string;
  dayNumber: number;
  inMonth: boolean;
  isToday: boolean;
}

const DIAS_SEMANA = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const DIAS_SEMANA_COMPLETOS = [
  'domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado',
];
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const TIPO_DOT_CLASS: Record<ItemTipo, string> = {
  PERSONAL: 'bg-[#a78bfa] dark:bg-[#8b5cf6]',
  TAREA: 'bg-[#38bdf8] dark:bg-[#0284c7]',
  SEGUIMIENTO: 'bg-[#f59e0b] dark:bg-[#d97706]',
};

function toIso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function parseIsoDateOnly(value: string): Date {
  // "2026-09-05" -> Date local a medianoche, sin el desfase de interpretar la fecha como UTC.
  const [y, m, d] = value.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './calendario.component.html',
})
export class CalendarioComponent {
  private readonly eventosService = inject(EventosCalendarioService);
  private readonly tareasService = inject(TareasService);
  private readonly leadsService = inject(LeadsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly diasSemana = DIAS_SEMANA;
  readonly tipoDotClass = TIPO_DOT_CLASS;
  readonly tipoSeguimientoLabels = TIPO_SEGUIMIENTO_LABELS;

  readonly isLoading = signal(true);
  readonly isGuardando = signal(false);
  readonly editandoId = signal<number | null>(null);

  readonly eventos = signal<EventoCalendario[]>([]);
  readonly tareaItems = signal<CalendarItem[]>([]);
  readonly seguimientoItems = signal<CalendarItem[]>([]);

  readonly hoy = new Date();
  readonly viewMonth = signal(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1));
  readonly selectedDate = signal(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate()));

  readonly hayTareaItems = computed(() => this.tareaItems().length > 0);
  readonly haySeguimientoItems = computed(() => this.seguimientoItems().length > 0);

  readonly monthLabel = computed(() => {
    const d = this.viewMonth();
    const mes = MESES[d.getMonth()];
    return `${mes.charAt(0).toUpperCase()}${mes.slice(1)} ${d.getFullYear()}`;
  });

  readonly personalItems = computed<CalendarItem[]>(() =>
    this.eventos().map((e) => ({
      tipo: 'PERSONAL' as const,
      id: e.id,
      iso: e.fecha.slice(0, 10),
      titulo: e.titulo,
      subtitulo: e.descripcion,
      leadId: null,
      completada: false,
    })),
  );

  readonly itemsByDay = computed<Map<string, CalendarItem[]>>(() => {
    const mapa = new Map<string, CalendarItem[]>();
    for (const item of [...this.personalItems(), ...this.tareaItems(), ...this.seguimientoItems()]) {
      const lista = mapa.get(item.iso) ?? [];
      lista.push(item);
      mapa.set(item.iso, lista);
    }
    return mapa;
  });

  readonly calendarDays = computed<CalendarDay[]>(() => {
    const mes = this.viewMonth();
    const primerDiaMes = new Date(mes.getFullYear(), mes.getMonth(), 1);
    const ultimoDiaMes = new Date(mes.getFullYear(), mes.getMonth() + 1, 0);

    // Lunes de la semana que contiene el primer día del mes (getDay: 0=domingo).
    const offsetInicio = (primerDiaMes.getDay() + 6) % 7;
    const inicio = new Date(primerDiaMes);
    inicio.setDate(inicio.getDate() - offsetInicio);

    const offsetFin = (7 - ((ultimoDiaMes.getDay() + 6) % 7) - 1) % 7;
    const fin = new Date(ultimoDiaMes);
    fin.setDate(fin.getDate() + offsetFin);

    const hoyIso = toIso(this.hoy);
    const dias: CalendarDay[] = [];
    for (let d = new Date(inicio); d <= fin; d.setDate(d.getDate() + 1)) {
      const dia = new Date(d);
      const iso = toIso(dia);
      dias.push({
        date: dia,
        iso,
        dayNumber: dia.getDate(),
        inMonth: dia.getMonth() === mes.getMonth(),
        isToday: iso === hoyIso,
      });
    }
    return dias;
  });

  readonly selectedDateLabel = computed(() => {
    const d = this.selectedDate();
    const dia = DIAS_SEMANA_COMPLETOS[d.getDay()];
    const mes = MESES[d.getMonth()];
    return `${dia.charAt(0).toUpperCase()}${dia.slice(1)} ${d.getDate()} de ${mes}`;
  });

  readonly selectedIso = computed(() => toIso(this.selectedDate()));
  readonly selectedDayItems = computed(() => this.itemsByDay().get(this.selectedIso()) ?? []);

  readonly form = this.fb.group({
    titulo: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: this.fb.control('', { nonNullable: true }),
    fecha: this.fb.control(toIso(this.selectedDate()), { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.cargar();

    // Si la persona escribe la fecha directamente en el campo (en vez de dar
    // clic en un día de la cuadrícula), el calendario de arriba debe seguirla:
    // se mueve al mes correspondiente y resalta ese día. Los cambios que el
    // propio componente hace al campo (seleccionarDia, editarEvento, etc.) se
    // emiten con emitEvent:false para no disparar esto en un loop.
    this.form.controls.fecha.valueChanges.subscribe((value) => {
      if (!value) return;
      const nueva = parseIsoDateOnly(value);
      this.selectedDate.set(nueva);
      this.viewMonth.set(new Date(nueva.getFullYear(), nueva.getMonth(), 1));
    });
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      const [eventos, misTareas, tareasCreadas, proximos] = await Promise.all([
        this.eventosService.listar(),
        this.tareasService.misTareas(),
        this.tareasService.tareasCreadas(),
        this.leadsService.proximosSeguimientos(),
      ]);

      this.eventos.set(eventos);

      const tareasPorId = new Map<number, (typeof misTareas)[number]>();
      for (const t of [...misTareas, ...tareasCreadas]) tareasPorId.set(t.id, t);
      this.tareaItems.set(
        [...tareasPorId.values()]
          .filter((t) => t.fechaLimite)
          .map((t) => ({
            tipo: 'TAREA' as const,
            id: t.id,
            iso: t.fechaLimite!.slice(0, 10),
            titulo: t.titulo,
            subtitulo: `${t.asignadoA.nombre}${t.completada ? ' · completada' : ''}`,
            leadId: null,
            completada: t.completada,
          })),
      );

      this.seguimientoItems.set(
        proximos.map((s) => {
          const fecha = new Date(s.proximoSeguimiento);
          const horaHHmm = `${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`;
          const duracion = s.duracionMinutos ? ` · ${etiquetaDuracion(s.duracionMinutos)}` : '';
          return {
            tipo: 'SEGUIMIENTO' as const,
            id: s.id,
            iso: s.proximoSeguimiento.slice(0, 10),
            titulo: s.leadNombreCliente,
            subtitulo: `${this.tipoSeguimientoLabels[s.tipo]} · ${s.asesor.nombre} · ${horaHHmm}${duracion}`,
            leadId: s.leadId,
            completada: false,
          };
        }),
      );
    } catch {
      this.toast.error('No se pudo cargar el calendario. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  mesAnterior(): void {
    const d = this.viewMonth();
    this.viewMonth.set(new Date(d.getFullYear(), d.getMonth() - 1, 1));
  }

  mesSiguiente(): void {
    const d = this.viewMonth();
    this.viewMonth.set(new Date(d.getFullYear(), d.getMonth() + 1, 1));
  }

  irAHoy(): void {
    this.viewMonth.set(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1));
    this.seleccionarDia(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate()));
  }

  seleccionarDia(date: Date): void {
    this.selectedDate.set(date);
    this.editandoId.set(null);
    this.form.reset({ titulo: '', descripcion: '', fecha: toIso(date) }, { emitEvent: false });
  }

  diaClase(day: CalendarDay): string {
    const base = day.inMonth ? 'text-ink' : 'text-ink-muted';
    const seleccionado = day.iso === this.selectedIso() ? 'bg-we-primary/10' : 'hover:bg-surface-2';
    const hoy = day.isToday ? 'ring-1 ring-we-primary' : '';
    return `${base} ${seleccionado} ${hoy}`;
  }

  diaNumeroClase(day: CalendarDay): string {
    return day.isToday ? 'text-we-primary dark:text-we-blue-light' : '';
  }

  dotsForDay(iso: string): string[] {
    const items = this.itemsByDay().get(iso) ?? [];
    const tipos = new Set(items.map((i) => i.tipo));
    return [...tipos].map((t) => this.tipoDotClass[t]);
  }

  editarEvento(item: CalendarItem): void {
    const evento = this.eventos().find((e) => e.id === item.id);
    if (!evento) return;
    this.editandoId.set(evento.id);
    this.form.setValue(
      {
        titulo: evento.titulo,
        descripcion: evento.descripcion ?? '',
        fecha: evento.fecha.slice(0, 10),
      },
      { emitEvent: false },
    );
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.form.reset({ titulo: '', descripcion: '', fecha: this.selectedIso() }, { emitEvent: false });
  }

  async guardarEvento(): Promise<void> {
    if (this.form.invalid || this.isGuardando()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isGuardando.set(true);
    const v = this.form.getRawValue();
    const request = { titulo: v.titulo, descripcion: v.descripcion || null, fecha: v.fecha };
    try {
      const editandoId = this.editandoId();
      if (editandoId) {
        const actualizado = await this.eventosService.actualizar(editandoId, request);
        this.eventos.update((lista) => lista.map((e) => (e.id === actualizado.id ? actualizado : e)));
        this.toast.success('Evento actualizado.');
      } else {
        const nuevo = await this.eventosService.crear(request);
        this.eventos.update((lista) => [...lista, nuevo]);
        this.toast.success('Evento agregado a tu calendario.');
      }
      this.cancelarEdicion();
    } catch {
      this.toast.error('No se pudo guardar el evento. Intenta de nuevo.');
    } finally {
      this.isGuardando.set(false);
    }
  }

  async eliminarEvento(item: CalendarItem): Promise<void> {
    if (!confirm(`¿Eliminar "${item.titulo}" de tu calendario?`)) return;
    try {
      await this.eventosService.eliminar(item.id);
      this.eventos.update((lista) => lista.filter((e) => e.id !== item.id));
      if (this.editandoId() === item.id) this.cancelarEdicion();
      this.toast.success('Evento eliminado.');
    } catch {
      this.toast.error('No se pudo eliminar el evento.');
    }
  }
}
