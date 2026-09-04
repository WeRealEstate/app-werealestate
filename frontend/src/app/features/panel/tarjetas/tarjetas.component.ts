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
import { ConfirmService } from '../../../core/services/confirm.service';
import {
  ColumnaPersonalizada,
  Desarrollo,
  Lead,
  TIPO_SEGUIMIENTO_LABELS,
  TipoSeguimiento,
  UsuarioResumen,
} from '../../../core/models/lead.model';
import {
  DURACION_OPCIONES,
  DURACION_POR_DEFECTO,
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';

/** Roles que efectivamente trabajan leads y por lo tanto pueden tener un tablero propio. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

/** Tope de tarjetas (columnas 100% personalizadas) por asesor; debe coincidir con el límite del backend. */
export const MAX_TARJETAS_POR_ASESOR = 20;

/**
 * Paleta de acento para las tarjetas del tablero: un color fijo por id (no por posición,
 * para que no "salte" al reordenar o eliminar otra tarjeta), usado en su encabezado, su
 * badge de conteo y el resaltado de "aquí se suelta" al arrastrar. "Sin asignar" usa el
 * azul de marca en vez de rotar la paleta, para leerse como el cajón neutral que es.
 */
const TARJETA_ACCENTS = [
  'oklch(66% 0.19 18)', // rosa
  'oklch(70% 0.17 55)', // naranja
  'oklch(78% 0.15 90)', // ámbar
  'oklch(68% 0.15 155)', // esmeralda
  'oklch(66% 0.12 195)', // verde azulado
  'oklch(68% 0.14 235)', // azul cielo
  'oklch(62% 0.19 295)', // violeta
  'oklch(64% 0.2 335)', // fucsia
];
const ACENTO_SIN_ASIGNAR = 'var(--color-we-primary)';

/** Una tarjeta del tablero: un apartado que el asesor creó libremente (p.ej. "Nuevo", "Esperando papeles"). */
type Tarjeta = { id: number; nombre: string; leads: Lead[] };

/** A dónde se movería el lead si se confirma el movimiento pendiente. */
type Destino = { tipo: 'tarjeta'; id: number; nombre: string } | { tipo: 'sin-asignar' };

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
  private readonly confirmService = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

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

  /** Solo las tarjetas (columnas personalizadas) cuentan para el tope; "Sin asignar" no es una tarjeta real. */
  readonly totalTarjetas = computed(() => this.columnasPersonalizadas().length);
  readonly limiteAlcanzado = computed(() => this.totalTarjetas() >= this.maxTarjetas);
  readonly cercaDelLimite = computed(() => !this.limiteAlcanzado() && this.totalTarjetas() / this.maxTarjetas >= 0.7);

  readonly acentoSinAsignar = ACENTO_SIN_ASIGNAR;

  /** Id del lead que acaba de aterrizar en su nueva columna: se resalta un instante y luego se apaga solo. */
  readonly leadRecienMovidoId = signal<number | null>(null);

  /**
   * Columna que el cursor tiene justo encima mientras se arrastra un lead. La clase que aplica
   * CDK automáticamente (cdk-drop-list-receiving) se enciende por igual en TODAS las columnas
   * conectadas mientras dura cualquier arrastre, no solo en la que está debajo del cursor — así
   * que este estado se rastrea a mano con (cdkDropListEntered)/(cdkDropListExited) para que el
   * resaltado de "aquí cae" señale una sola columna a la vez.
   */
  readonly columnaHoverId = signal<number | 'sin-asignar' | null>(null);

  readonly desarrollos = signal<Desarrollo[]>([]);

  readonly modalLeadAbierto = signal(false);
  readonly isCreandoLead = signal(false);
  readonly errorCreacionLead = signal<string | null>(null);
  readonly nuevoLeadForm = this.fb.group({
    nombreCliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
  });

  readonly modalTarjetaAbierto = signal(false);
  readonly isCreandoTarjeta = signal(false);
  readonly errorTarjeta = signal<string | null>(null);
  readonly nuevaTarjetaForm = this.fb.group({
    nombre: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly editandoTarjetaId = signal<number | null>(null);
  readonly nombreEdicion = signal('');

  readonly tipoSeguimientoLabels = TIPO_SEGUIMIENTO_LABELS;
  readonly tiposSeguimiento = Object.keys(TIPO_SEGUIMIENTO_LABELS) as TipoSeguimiento[];
  readonly duracionOpciones = DURACION_OPCIONES;
  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;

  /** Movimiento pendiente de confirmar: el lead no se mueve solo con el drop, hay que registrar el seguimiento. */
  readonly panelMovimiento = signal<{ lead: Lead; destino: Destino } | null>(null);
  readonly isMoviendo = signal(false);
  readonly errorMovimiento = signal<string | null>(null);
  readonly movimientoForm = this.fb.group({
    tipo: this.fb.control<TipoSeguimiento>('OTRO', { nonNullable: true, validators: [Validators.required] }),
    nota: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    resultado: this.fb.control('', { nonNullable: true }),
    proximoSeguimiento: this.fb.control('', { nonNullable: true }),
    horaSeguimiento: this.fb.control(HORA_POR_DEFECTO, { nonNullable: true }),
    minutoSeguimiento: this.fb.control(MINUTO_POR_DEFECTO, { nonNullable: true }),
    duracionSeguimiento: this.fb.control(DURACION_POR_DEFECTO, { nonNullable: true }),
  });

  /** Leads que no están en ninguna tarjeta todavía; siempre visibles, no cuenta para el tope de 20. */
  readonly sinAsignar = computed(() => this.leadsDelAsesor().filter((l) => l.columnaPersonalizadaId === null));

  readonly tarjetas = computed<Tarjeta[]>(() => {
    const leadsAsesor = this.leadsDelAsesor();
    return this.columnasPersonalizadas().map((col) => ({
      id: col.id,
      nombre: col.nombre,
      leads: leadsAsesor.filter((l) => l.columnaPersonalizadaId === col.id),
    }));
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
      await this.cargarTarjetas();
    } catch {
      this.errorMessage.set('No se pudo cargar el tablero. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async cargarTarjetas(): Promise<void> {
    const asesorId = this.asesorSeleccionado();
    if (asesorId === null) {
      this.columnasPersonalizadas.set([]);
      return;
    }
    try {
      this.columnasPersonalizadas.set(await this.columnasService.listar(this.esAdmin() ? asesorId : undefined));
    } catch {
      this.columnasPersonalizadas.set([]);
    }
  }

  async cambiarAsesor(valor: string): Promise<void> {
    this.asesorSeleccionado.set(valor === '' ? null : +valor);
    await this.cargarTarjetas();
  }

  iniciales(nombre: string): string {
    return nombre
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0]?.toUpperCase())
      .join('');
  }

  trackTarjeta(_index: number, tarjeta: Tarjeta): number {
    return tarjeta.id;
  }

  /** Color de acento de una tarjeta, estable por id (no por posición en la lista). */
  accentColor(tarjetaId: number): string {
    return TARJETA_ACCENTS[tarjetaId % TARJETA_ACCENTS.length];
  }

  // --- Nuevo lead: siempre entra a "Sin asignar", sin tope ---

  abrirModalLead(): void {
    this.nuevoLeadForm.reset({ nombreCliente: '', telefono: '', desarrolloId: null });
    this.errorCreacionLead.set(null);
    this.modalLeadAbierto.set(true);
  }

  cerrarModalLead(): void {
    this.modalLeadAbierto.set(false);
  }

  async crearLead(): Promise<void> {
    if (this.nuevoLeadForm.invalid || this.isCreandoLead()) {
      this.nuevoLeadForm.markAllAsTouched();
      return;
    }

    this.isCreandoLead.set(true);
    this.errorCreacionLead.set(null);
    const v = this.nuevoLeadForm.getRawValue();

    try {
      const nuevo = await this.leadsService.crear({
        nombreCliente: v.nombreCliente,
        telefono: v.telefono,
        desarrolloId: v.desarrolloId!,
        asesorId: this.esAdmin() ? this.asesorSeleccionado() : null,
      });
      this.leads.update((lista) => [nuevo, ...lista]);
      this.toast.success(`${nuevo.nombreCliente} se agregó al tablero.`);
      this.modalLeadAbierto.set(false);
    } catch (error) {
      this.errorCreacionLead.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear el lead. Intenta de nuevo.',
      );
    } finally {
      this.isCreandoLead.set(false);
    }
  }

  // --- Tarjetas (columnas 100% personalizadas), tope de 20 ---

  abrirModalTarjeta(): void {
    if (this.limiteAlcanzado()) return;
    this.nuevaTarjetaForm.reset({ nombre: '' });
    this.errorTarjeta.set(null);
    this.modalTarjetaAbierto.set(true);
  }

  cerrarModalTarjeta(): void {
    this.modalTarjetaAbierto.set(false);
  }

  async crearTarjeta(): Promise<void> {
    if (this.nuevaTarjetaForm.invalid || this.isCreandoTarjeta()) {
      this.nuevaTarjetaForm.markAllAsTouched();
      return;
    }

    this.isCreandoTarjeta.set(true);
    this.errorTarjeta.set(null);
    try {
      const nueva = await this.columnasService.crear(
        this.nuevaTarjetaForm.getRawValue().nombre.trim(),
        this.esAdmin() ? this.asesorSeleccionado() : null,
      );
      this.columnasPersonalizadas.update((lista) => [...lista, nueva]);
      this.toast.success(`Tarjeta "${nueva.nombre}" creada.`);
      this.modalTarjetaAbierto.set(false);
    } catch (error) {
      this.errorTarjeta.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear la tarjeta. Intenta de nuevo.',
      );
    } finally {
      this.isCreandoTarjeta.set(false);
    }
  }

  iniciarEdicionTarjeta(tarjeta: Tarjeta): void {
    this.editandoTarjetaId.set(tarjeta.id);
    this.nombreEdicion.set(tarjeta.nombre);
  }

  cancelarEdicionTarjeta(): void {
    this.editandoTarjetaId.set(null);
  }

  async guardarEdicionTarjeta(id: number): Promise<void> {
    const nombre = this.nombreEdicion().trim();
    if (!nombre) return;

    try {
      const actualizada = await this.columnasService.renombrar(id, nombre);
      this.columnasPersonalizadas.update((lista) => lista.map((c) => (c.id === id ? actualizada : c)));
      this.leads.update((lista) =>
        lista.map((l) => (l.columnaPersonalizadaId === id ? { ...l, columnaPersonalizadaNombre: actualizada.nombre } : l)),
      );
      this.editandoTarjetaId.set(null);
    } catch {
      this.toast.error('No se pudo renombrar la tarjeta.');
    }
  }

  async eliminarTarjeta(tarjeta: Tarjeta): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar tarjeta',
      mensaje: `¿Eliminar la tarjeta "${tarjeta.nombre}"? Sus leads pasarán a "Sin asignar".`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    try {
      await this.columnasService.eliminar(tarjeta.id);
      this.columnasPersonalizadas.update((lista) => lista.filter((c) => c.id !== tarjeta.id));
      this.leads.update((lista) =>
        lista.map((l) =>
          l.columnaPersonalizadaId === tarjeta.id ? { ...l, columnaPersonalizadaId: null, columnaPersonalizadaNombre: null } : l,
        ),
      );
      this.toast.success(`Tarjeta "${tarjeta.nombre}" eliminada.`);
    } catch {
      this.toast.error('No se pudo eliminar la tarjeta.');
    }
  }

  // --- Archivar lead ---

  async archivarLead(lead: Lead): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Archivar lead',
      mensaje: `¿Archivar a ${lead.nombreCliente}? Dejará de aparecer en el tablero, pero se conserva como métrica.`,
      textoConfirmar: 'Archivar',
    });
    if (!confirmado) return;
    try {
      await this.leadsService.archivar(lead.id);
      this.leads.update((lista) => lista.filter((l) => l.id !== lead.id));
      this.toast.success(`${lead.nombreCliente} fue archivado.`);
    } catch {
      this.toast.error('No se pudo archivar el lead. Intenta de nuevo.');
    }
  }

  // --- Arrastrar y soltar: el drop solo abre el panel, el movimiento se confirma con un botón ---

  onDropTarjeta(event: CdkDragDrop<Lead[]>, tarjeta: Tarjeta): void {
    this.abrirPanelMovimiento(event, { tipo: 'tarjeta', id: tarjeta.id, nombre: tarjeta.nombre });
  }

  onDropSinAsignar(event: CdkDragDrop<Lead[]>): void {
    this.abrirPanelMovimiento(event, { tipo: 'sin-asignar' });
  }

  /** Marca cuál columna tiene el cursor encima ahora mismo, para el resaltado de "aquí cae". */
  onColumnaHoverEntered(id: number | 'sin-asignar'): void {
    this.columnaHoverId.set(id);
  }

  onColumnaHoverExited(id: number | 'sin-asignar'): void {
    if (this.columnaHoverId() === id) this.columnaHoverId.set(null);
  }

  private abrirPanelMovimiento(event: CdkDragDrop<Lead[]>, destino: Destino): void {
    this.columnaHoverId.set(null);
    if (event.previousContainer === event.container) return;
    const lead = event.previousContainer.data[event.previousIndex];
    const nombreDestino = destino.tipo === 'tarjeta' ? destino.nombre : 'Sin asignar';

    this.panelMovimiento.set({ lead, destino });
    this.movimientoForm.reset({
      tipo: 'OTRO',
      nota: `Movido a "${nombreDestino}" desde el tablero de tarjetas.`,
      resultado: '',
      proximoSeguimiento: '',
      horaSeguimiento: HORA_POR_DEFECTO,
      minutoSeguimiento: MINUTO_POR_DEFECTO,
      duracionSeguimiento: DURACION_POR_DEFECTO,
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
    const nombreDestino = destino.tipo === 'tarjeta' ? destino.nombre : 'Sin asignar';

    try {
      const actualizado = await this.leadsService.moverColumna(lead.id, {
        columnaPersonalizadaId: destino.tipo === 'tarjeta' ? destino.id : null,
        tipo: v.tipo,
        nota: v.nota,
        resultado: v.resultado || null,
        proximoSeguimiento: v.proximoSeguimiento
          ? combinarFechaHora(v.proximoSeguimiento, v.horaSeguimiento, v.minutoSeguimiento)
          : null,
        duracionMinutos: v.proximoSeguimiento ? v.duracionSeguimiento : null,
      });

      this.leads.update((lista) => lista.map((l) => (l.id === lead.id ? actualizado : l)));
      this.toast.success(`${lead.nombreCliente} → ${nombreDestino}.`);
      this.panelMovimiento.set(null);

      this.leadRecienMovidoId.set(lead.id);
      setTimeout(() => {
        if (this.leadRecienMovidoId() === lead.id) this.leadRecienMovidoId.set(null);
      }, 900);
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
