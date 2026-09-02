import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo, ESTADO_LEAD_LABELS, EstadoLead, Lead, UsuarioResumen } from '../../../core/models/lead.model';

/** Roles que efectivamente trabajan leads y por lo tanto pueden tener un tablero propio. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

/** Tope de tarjetas (leads activos) por asesor; debe coincidir con el límite del backend. */
export const MAX_TARJETAS_POR_ASESOR = 20;

interface Columna {
  estado: EstadoLead;
  leads: Lead[];
}

@Component({
  selector: 'app-tarjetas',
  standalone: true,
  imports: [RouterLink, DragDropModule, ReactiveFormsModule],
  templateUrl: './tarjetas.component.html',
})
export class TarjetasComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
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

  readonly columnas = computed<Columna[]>(() =>
    this.estados.map((estado) => ({
      estado,
      leads: this.leadsDelAsesor().filter((l) => l.estado === estado),
    })),
  );

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
    } catch {
      this.errorMessage.set('No se pudieron cargar las tarjetas. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  cambiarAsesor(valor: string): void {
    this.asesorSeleccionado.set(valor === '' ? null : +valor);
  }

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

  iniciales(nombre: string): string {
    return nombre
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0]?.toUpperCase())
      .join('');
  }

  async onDrop(event: CdkDragDrop<Lead[]>, nuevoEstado: EstadoLead): Promise<void> {
    if (event.previousContainer === event.container) return;

    const lead = event.previousContainer.data[event.previousIndex];
    const estadoAnterior = lead.estado;

    this.leads.update((lista) => lista.map((l) => (l.id === lead.id ? { ...l, estado: nuevoEstado } : l)));

    try {
      await this.leadsService.mover(lead.id, nuevoEstado);
      this.toast.success(`${lead.nombreCliente} → ${this.estadoLabels[nuevoEstado]}.`);
    } catch {
      this.leads.update((lista) => lista.map((l) => (l.id === lead.id ? { ...l, estado: estadoAnterior } : l)));
      this.toast.error('No se pudo mover el lead. Intenta de nuevo.');
    }
  }
}
