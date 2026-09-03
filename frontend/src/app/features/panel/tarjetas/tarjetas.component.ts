import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ColumnasService } from '../../../core/services/columnas.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  ColumnaPersonalizada,
  Desarrollo,
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Lead,
  TIPO_SEGUIMIENTO_LABELS,
  TipoSeguimiento,
  UsuarioResumen,
} from '../../../core/models/lead.model';

/** Roles que efectivamente trabajan leads y por lo tanto pueden tener un tablero propio. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

/** Tope de tarjetas (leads activos) por asesor; debe coincidir con el límite del backend. */
export const MAX_TARJETAS_POR_ASESOR = 20;

/** Una columna fija (estado del lead) o una que el asesor agregó a su propio tablero. */
type Columna =
  | { tipo: 'estado'; estado: EstadoLead; nombre: string; leads: Lead[] }
  | { tipo: 'personalizada'; id: number; nombre: string; leads: Lead[] };

/** A dónde se movería la tarjeta si se confirma el movimiento pendiente. */
type Destino =
  | { tipo: 'estado'; estado: EstadoLead; nombre: string }
  | { tipo: 'personalizada'; id: number; nombre: string };

@Component({
  selector: 'app-tarjetas',
  standalone: true,
  imports: [RouterLink, DragDropModule, ReactiveFormsModule],
  templateUrl: './tarjetas.component.html',
})
export class TarjetasComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly columnasService = inject(ColumnasService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly estados = Object.keys(ESTADO_LEAD_LABELS) as EstadoLead[];
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly propioId = computed(() => this.auth.currentUser()?.id);
  readonly maxTarjetas = MAX_TARJETAS_POR_ASESOR;

  readonly leads = signal<Lead[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly asesoresDisponibles = signal<UsuarioResumen[]>([]);
  readonly asesorSeleccionado = signal<number | null>(null);
  readonly columnasPersonalizadas = signal<ColumnaPersonalizada[]>([]);

  /** Para el selector de admin: su propio "tablero" primero, luego el resto del equipo. */
  readonly opcionesTablero = computed<UsuarioResumen[]>(() => {
    const actual = this.auth.currentUser();
    const propio: UsuarioResumen[] = actual ? [{ id: actual.id, nombre: actual.nombre }] : [];
    return [...propio, ...this.asesoresDisponibles()];
  });

  readonly leadsDelAsesor = computed(() => {
    const asesorId = this.asesorSeleccionado();
    if (asesorId === null) return [];
    return this.leads().filter((l) => l.asesor.id === asesorId);
  });

  readonly totalTarjetas = computed(() => this.leadsDelAsesor().length);
  readonly limiteAlcanzado = computed(() => this.totalTarjetas() >= this.maxTarjetas);

  readonly desarrollos = signal<Desarrollo[]>([]);

  readonly modalAbierto = signal(false);
  readonly isCreando = signal(false);
  readonly errorCreacion = signal<string | null>(null);
  readonly nuevaTarjetaForm = this.fb.group({
    nombreCliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
  });

  readonly modalColumnaAbierto = signal(false);
  readonly isCreandoColumna = signal(false);
  readonly errorColumna = signal<string | null>(null);
  readonly nuevaColumnaForm = this.fb.group({
    nombre: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly editandoColumnaId = signal<number | null>(null);
  readonly nombreEdicion = signal('');

  readonly tipoSeguimientoLabels = TIPO_SEGUIMIENTO_LABELS;
  readonly tiposSeguimiento = Object.keys(TIPO_SEGUIMIENTO_LABELS) as TipoSeguimiento[];

  /** Movimiento pendiente de confirmar: la tarjeta ya no se mueve sola con el drop, hay que registrar el seguimiento. */
  readonly panelMovimiento = signal<{ lead: Lead; destino: Destino } | null>(null);
  readonly isMoviendo = signal(false);
  readonly errorMovimiento = signal<string | null>(null);
  readonly movimientoForm = this.fb.group({
    tipo: this.fb.control<TipoSeguimiento>('OTRO', { nonNullable: true, validators: [Validators.required] }),
    nota: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    resultado: this.fb.control('', { nonNullable: true }),
    proximoSeguimiento: this.fb.control('', { nonNullable: true }),
  });

  readonly columnas = computed<Columna[]>(() => {
    const leadsAsesor = this.leadsDelAsesor();
    const fijas: Columna[] = this.estados.map((estado) => ({
      tipo: 'estado',
      estado,
      nombre: this.estadoLabels[estado],
      leads: leadsAsesor.filter((l) => l.estado === estado && l.columnaPersonalizadaId === null),
    }));
    const personalizadas: Columna[] = this.columnasPersonalizadas().map((col) => ({
      tipo: 'personalizada',
      id: col.id,
      nombre: col.nombre,
      leads: leadsAsesor.filter((l) => l.columnaPersonalizadaId === col.id),
    }));
    return [...fijas, ...personalizadas];
  });

  constructor() {
    const asesorParam = this.route.snapshot.queryParamMap.get('asesor');
    this.asesorSeleccionado.set(asesorParam ? +asesorParam : (this.auth.currentUser()?.id ?? null));
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [leads, desarrollos] = await Promise.all([this.leadsService.listar(), this.leadsService.listarDesarrollos()]);
      this.leads.set(leads);
      this.desarrollos.set(desarrollos);
      if (this.esAdmin()) {
        const usuarios = await this.usuariosService.listar();
        this.asesoresDisponibles.set(
          usuarios
            .filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol))
            .sort((a, b) => a.nombre.localeCompare(b.nombre)),
        );
      }
      await this.cargarColumnas();
    } catch {
      this.errorMessage.set('No se pudieron cargar las tarjetas. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async cargarColumnas(): Promise<void> {
    const asesorId = this.asesorSeleccionado();
    if (asesorId === null) {
      this.columnasPersonalizadas.set([]);
      return;
    }
    try {
      this.columnasPersonalizadas.set(await this.columnasService.listar(this.esAdmin() ? asesorId : undefined));
    } catch {
      // Las columnas personalizadas son una comodidad, no algo crítico: si falla, el tablero sigue con las fijas.
    }
  }

  async cambiarAsesor(valor: string): Promise<void> {
    this.asesorSeleccionado.set(valor === '' ? null : +valor);
    await this.cargarColumnas();
  }

  iniciales(nombre: string): string {
    return nombre
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0]?.toUpperCase())
      .join('');
  }

  trackColumna(_index: number, col: Columna): string {
    return col.tipo === 'estado' ? `estado-${col.estado}` : `personalizada-${col.id}`;
  }

  // --- Nueva tarjeta ---

  abrirModal(): void {
    if (this.limiteAlcanzado()) return;
    this.nuevaTarjetaForm.reset({ nombreCliente: '', telefono: '', desarrolloId: null });
    this.errorCreacion.set(null);
    this.modalAbierto.set(true);
  }

  cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  async crearTarjeta(): Promise<void> {
    if (this.nuevaTarjetaForm.invalid || this.isCreando()) {
      this.nuevaTarjetaForm.markAllAsTouched();
      return;
    }

    this.isCreando.set(true);
    this.errorCreacion.set(null);
    const v = this.nuevaTarjetaForm.getRawValue();

    try {
      const nuevo = await this.leadsService.crear({
        nombreCliente: v.nombreCliente,
        telefono: v.telefono,
        desarrolloId: v.desarrolloId!,
        asesorId: this.esAdmin() ? this.asesorSeleccionado() : null,
      });
      this.leads.update((lista) => [nuevo, ...lista]);
      this.toast.success(`${nuevo.nombreCliente} se agregó al tablero.`);
      this.modalAbierto.set(false);
    } catch (error) {
      this.errorCreacion.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear la tarjeta. Intenta de nuevo.',
      );
    } finally {
      this.isCreando.set(false);
    }
  }

  // --- Columnas personalizadas ---

  abrirModalColumna(): void {
    this.nuevaColumnaForm.reset({ nombre: '' });
    this.errorColumna.set(null);
    this.modalColumnaAbierto.set(true);
  }

  cerrarModalColumna(): void {
    this.modalColumnaAbierto.set(false);
  }

  async crearColumna(): Promise<void> {
    if (this.nuevaColumnaForm.invalid || this.isCreandoColumna()) {
      this.nuevaColumnaForm.markAllAsTouched();
      return;
    }

    this.isCreandoColumna.set(true);
    this.errorColumna.set(null);
    try {
      const nueva = await this.columnasService.crear(
        this.nuevaColumnaForm.getRawValue().nombre.trim(),
        this.esAdmin() ? this.asesorSeleccionado() : null,
      );
      this.columnasPersonalizadas.update((lista) => [...lista, nueva]);
      this.toast.success(`Columna "${nueva.nombre}" creada.`);
      this.modalColumnaAbierto.set(false);
    } catch (error) {
      this.errorColumna.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear la columna. Intenta de nuevo.',
      );
    } finally {
      this.isCreandoColumna.set(false);
    }
  }

  iniciarEdicionColumna(col: Extract<Columna, { tipo: 'personalizada' }>): void {
    this.editandoColumnaId.set(col.id);
    this.nombreEdicion.set(col.nombre);
  }

  cancelarEdicionColumna(): void {
    this.editandoColumnaId.set(null);
  }

  async guardarEdicionColumna(id: number): Promise<void> {
    const nombre = this.nombreEdicion().trim();
    if (!nombre) return;

    try {
      const actualizada = await this.columnasService.renombrar(id, nombre);
      this.columnasPersonalizadas.update((lista) => lista.map((c) => (c.id === id ? actualizada : c)));
      this.leads.update((lista) =>
        lista.map((l) => (l.columnaPersonalizadaId === id ? { ...l, columnaPersonalizadaNombre: actualizada.nombre } : l)),
      );
      this.editandoColumnaId.set(null);
    } catch {
      this.toast.error('No se pudo renombrar la columna.');
    }
  }

  async eliminarColumna(col: Extract<Columna, { tipo: 'personalizada' }>): Promise<void> {
    if (!confirm(`¿Eliminar la columna "${col.nombre}"? Sus tarjetas volverán a agruparse por su estado.`)) return;

    try {
      await this.columnasService.eliminar(col.id);
      this.columnasPersonalizadas.update((lista) => lista.filter((c) => c.id !== col.id));
      this.leads.update((lista) =>
        lista.map((l) =>
          l.columnaPersonalizadaId === col.id ? { ...l, columnaPersonalizadaId: null, columnaPersonalizadaNombre: null } : l,
        ),
      );
      this.toast.success(`Columna "${col.nombre}" eliminada.`);
    } catch {
      this.toast.error('No se pudo eliminar la columna.');
    }
  }

  // --- Archivar tarjeta ---

  async archivarTarjeta(lead: Lead): Promise<void> {
    if (!confirm(`¿Archivar a ${lead.nombreCliente}? Dejará de aparecer en el tablero, pero se conserva como métrica.`)) {
      return;
    }
    try {
      await this.leadsService.archivar(lead.id);
      this.leads.update((lista) => lista.filter((l) => l.id !== lead.id));
      this.toast.success(`${lead.nombreCliente} fue archivado.`);
    } catch {
      this.toast.error('No se pudo archivar el lead. Intenta de nuevo.');
    }
  }

  // --- Arrastrar y soltar: el drop solo abre el panel, el movimiento se confirma con un botón ---

  onDrop(event: CdkDragDrop<Lead[]>, col: Columna): void {
    if (event.previousContainer === event.container) return;
    const lead = event.previousContainer.data[event.previousIndex];

    const destino: Destino =
      col.tipo === 'estado' ? { tipo: 'estado', estado: col.estado, nombre: col.nombre } : { tipo: 'personalizada', id: col.id, nombre: col.nombre };

    this.panelMovimiento.set({ lead, destino });
    this.movimientoForm.reset({
      tipo: 'OTRO',
      nota: `Movido a "${destino.nombre}" desde el tablero de tarjetas.`,
      resultado: '',
      proximoSeguimiento: '',
    });
    this.errorMovimiento.set(null);
  }

  cancelarMovimiento(): void {
    this.panelMovimiento.set(null);
  }

  async confirmarMovimiento(): Promise<void> {
    const pendiente = this.panelMovimiento();
    if (!pendiente || this.movimientoForm.invalid || this.isMoviendo()) {
      this.movimientoForm.markAllAsTouched();
      return;
    }

    this.isMoviendo.set(true);
    this.errorMovimiento.set(null);
    const v = this.movimientoForm.getRawValue();
    const { lead, destino } = pendiente;
    const base = {
      tipo: v.tipo,
      nota: v.nota,
      resultado: v.resultado || null,
      proximoSeguimiento: v.proximoSeguimiento ? new Date(v.proximoSeguimiento).toISOString() : null,
    };

    try {
      const actualizado =
        destino.tipo === 'estado'
          ? await this.leadsService.mover(lead.id, { estado: destino.estado, ...base })
          : await this.leadsService.moverColumna(lead.id, { columnaPersonalizadaId: destino.id, ...base });

      this.leads.update((lista) => lista.map((l) => (l.id === lead.id ? actualizado : l)));
      this.toast.success(`${lead.nombreCliente} → ${destino.nombre}.`);
      this.panelMovimiento.set(null);
    } catch (error) {
      this.errorMovimiento.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo mover el lead. Intenta de nuevo.',
      );
    } finally {
      this.isMoviendo.set(false);
    }
  }
}
