import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { descargarCsv } from '../../../core/utils/csv';
import {
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Lead,
  PAIS_LABELS,
  UsuarioResumen,
} from '../../../core/models/lead.model';

/** Filtro de estado: un EstadoLead puntual, o 'FRIOS' para leads sin seguimiento reciente. */
type FiltroEstado = EstadoLead | 'FRIOS';

/** Roles que efectivamente trabajan leads y por lo tanto aparecen en el filtro por asesor. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

@Component({
  selector: 'app-leads-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './leads-list.component.html',
})
export class LeadsListComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly estados = Object.keys(ESTADO_LEAD_LABELS) as EstadoLead[];
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly leads = signal<Lead[]>([]);
  readonly archivados = signal<Lead[]>([]);
  readonly verArchivados = signal(false);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');
  readonly asesorId = signal<number | null>(null);
  readonly estadoFiltro = signal<FiltroEstado | null>(null);
  readonly asesoresDisponibles = signal<UsuarioResumen[]>([]);

  private archivadosCargados = false;

  readonly leadsFiltrados = computed(() => {
    const term = this.filtro().trim().toLowerCase();
    const asesorId = this.asesorId();
    const estadoFiltro = this.estadoFiltro();
    const fuente = this.verArchivados() ? this.archivados() : this.leads();
    return fuente.filter((l) => {
      if (estadoFiltro === 'FRIOS' && !l.frio) return false;
      if (estadoFiltro && estadoFiltro !== 'FRIOS' && l.estado !== estadoFiltro) return false;
      if (asesorId !== null && l.asesor.id !== asesorId) return false;
      if (term && !l.nombreCliente.toLowerCase().includes(term)) return false;
      return true;
    });
  });

  readonly totalFrios = computed(() => this.leads().filter((l) => l.frio).length);

  constructor() {
    if (this.route.snapshot.queryParamMap.get('frios') === '1') {
      this.estadoFiltro.set('FRIOS');
    }
    this.cargar();
    if (this.esAdmin()) {
      this.cargarAsesores();
    }
  }

  private async cargarAsesores(): Promise<void> {
    try {
      const usuarios = await this.usuariosService.listar();
      this.asesoresDisponibles.set(
        usuarios
          .filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol))
          .sort((a, b) => a.nombre.localeCompare(b.nombre)),
      );
    } catch {
      // El filtro por asesor es una comodidad, no algo crítico: si falla, simplemente no se muestra.
    }
  }

  toggleSoloFrios(): void {
    this.estadoFiltro.update((v) => (v === 'FRIOS' ? null : 'FRIOS'));
  }

  cambiarEstadoFiltro(valor: string): void {
    this.estadoFiltro.set(valor === '' ? null : (valor as FiltroEstado));
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.leads.set(await this.leadsService.listar());
    } catch {
      this.errorMessage.set('No se pudieron cargar los leads. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleArchivados(): Promise<void> {
    const nuevoValor = !this.verArchivados();
    this.verArchivados.set(nuevoValor);
    if (nuevoValor && !this.archivadosCargados) {
      this.isLoading.set(true);
      this.errorMessage.set(null);
      try {
        this.archivados.set(await this.leadsService.listarArchivados());
        this.archivadosCargados = true;
      } catch {
        this.errorMessage.set('No se pudieron cargar los leads archivados.');
      } finally {
        this.isLoading.set(false);
      }
    }
  }

  exportarCsv(): void {
    const filas = this.leadsFiltrados().map((l) => ({
      cliente: l.nombreCliente,
      telefono: l.telefono,
      email: l.email ?? '',
      desarrollo: l.desarrollo.nombre,
      estado: this.estadoLabels[l.estado],
      asesor: l.asesor.nombre,
      edad: l.edad ?? '',
      pais: l.pais ? PAIS_LABELS[l.pais] : '',
      estadoRepublica: l.estadoRepublica ?? '',
      origen: l.origen ?? '',
      valorEstimado: l.valorEstimado ?? '',
      fechaCreacion: l.fechaCreacion,
      fechaUltimoContacto: l.fechaUltimoContacto,
      diasSinContacto: l.diasSinContacto,
      frio: l.frio ? 'Sí' : 'No',
    }));

    descargarCsv(
      `leads_${new Date().toISOString().slice(0, 10)}.csv`,
      {
        cliente: 'Cliente',
        telefono: 'Teléfono',
        email: 'Correo',
        desarrollo: 'Desarrollo',
        estado: 'Estado',
        asesor: 'Asesor',
        edad: 'Edad',
        pais: 'País',
        estadoRepublica: 'Estado (República)',
        origen: 'Origen',
        valorEstimado: 'Valor estimado',
        fechaCreacion: 'Fecha de creación',
        fechaUltimoContacto: 'Último contacto',
        diasSinContacto: 'Días sin contacto',
        frio: 'Lead frío',
      },
      filas,
    );
  }

  badgeClass(estado: Lead['estado']): string {
    switch (estado) {
      case 'NUEVO':
        return 'bg-we-blue-light/20 text-we-primary dark:text-we-blue-light';
      case 'CONTACTADO':
        return 'bg-surface-2 text-ink-muted';
      case 'INTERESADO':
        return 'bg-we-primary/15 text-we-primary dark:text-we-blue-light';
      case 'CITA_AGENDADA':
        return 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300';
      case 'NEGOCIACION':
        return 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300';
      case 'CERRADO_GANADO':
        return 'bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-300';
      case 'CERRADO_PERDIDO':
        return 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300';
    }
  }
}
