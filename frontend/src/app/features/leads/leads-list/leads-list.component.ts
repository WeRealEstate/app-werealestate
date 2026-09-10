import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { descargarCsv } from '../../../core/utils/csv';
import { descargarExcel } from '../../../core/utils/excel';
import { generarPlantillaLeads } from '../../../core/utils/plantilla-leads';
import {
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Etiqueta,
  ETIQUETA_BADGE_CLASSES,
  Lead,
  PAIS_LABELS,
  UsuarioResumen,
} from '../../../core/models/lead.model';

/** Filtro de estado: un EstadoLead puntual, o 'FRIOS' para leads sin seguimiento reciente. */
type FiltroEstado = EstadoLead | 'FRIOS';

/** Roles que aparecen en el filtro por asesor: quienes trabajan leads, más el admin, que
 * también tiene su propia bolsa de leads. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA', 'ADMIN']);

/** Cuántos leads se cargan por lote. Al llegar al final, "Cargar más" trae el siguiente lote. */
const TAMANO_PAGINA = 10;

/** Para exportar se necesita todo lo que coincide con los filtros, no solo lo ya cargado en
 * pantalla; se pide de un jalón con un tamaño de página generoso. */
const TAMANO_EXPORTACION = 5000;

/** Espera esto antes de volver a consultar al servidor mientras el usuario sigue escribiendo. */
const DEBOUNCE_BUSQUEDA_MS = 350;

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
  readonly badgeClasesEtiqueta = ETIQUETA_BADGE_CLASSES;

  /** Leads acumulados de los lotes cargados hasta ahora, ya filtrados por el servidor. */
  readonly leads = signal<Lead[]>([]);
  readonly verArchivados = signal(false);
  readonly isLoading = signal(true);
  readonly isLoadingMore = signal(false);
  readonly hayMas = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly filtro = signal('');
  readonly asesorId = signal<number | null>(null);
  readonly estadoFiltro = signal<FiltroEstado | null>(null);
  readonly etiquetaFiltro = signal<number | null>(null);
  readonly asesoresDisponibles = signal<UsuarioResumen[]>([]);
  readonly totalFrios = signal(0);

  private pagina = 0;
  private debounceHandle: ReturnType<typeof setTimeout> | undefined;

  /** Etiquetas presentes en los leads ya cargados (son privadas por asesor: no hay un catálogo
   * único que listar de todo el equipo). */
  readonly etiquetasDisponibles = computed<Etiqueta[]>(() => {
    const porId = new Map<number, Etiqueta>();
    for (const lead of this.leads()) {
      for (const et of lead.etiquetas) {
        porId.set(et.id, et);
      }
    }
    return [...porId.values()].sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  readonly hayFiltrosActivos = computed(
    () =>
      this.filtro().trim().length > 0 ||
      this.estadoFiltro() !== null ||
      this.asesorId() !== null ||
      this.etiquetaFiltro() !== null,
  );

  constructor() {
    if (this.route.snapshot.queryParamMap.get('frios') === '1') {
      this.estadoFiltro.set('FRIOS');
    }
    this.cargar();
    if (this.esAdmin()) {
      this.cargarAsesores();
      this.cargarTotalFrios();
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

  /** El contador de "leads fríos" es independiente de lo que esté cargado en pantalla: se calcula
   * sobre el total real del equipo, no solo sobre el lote visible. */
  private async cargarTotalFrios(): Promise<void> {
    try {
      const frios = await this.leadsService.listarFrios();
      this.totalFrios.set(frios.length);
    } catch {
      // No crítico: si falla, simplemente no se muestra el aviso.
    }
  }

  onFiltroInput(valor: string): void {
    this.filtro.set(valor);
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.recargarDesdeInicio(), DEBOUNCE_BUSQUEDA_MS);
  }

  onAsesorChange(valor: string): void {
    this.asesorId.set(valor === '' ? null : +valor);
    this.recargarDesdeInicio();
  }

  onEtiquetaChange(valor: string): void {
    this.etiquetaFiltro.set(valor === '' ? null : +valor);
    this.recargarDesdeInicio();
  }

  toggleSoloFrios(): void {
    this.estadoFiltro.update((v) => (v === 'FRIOS' ? null : 'FRIOS'));
    this.recargarDesdeInicio();
  }

  cambiarEstadoFiltro(valor: string): void {
    this.estadoFiltro.set(valor === '' ? null : (valor as FiltroEstado));
    this.recargarDesdeInicio();
  }

  toggleArchivados(): void {
    this.verArchivados.update((v) => !v);
    this.recargarDesdeInicio();
  }

  private recargarDesdeInicio(): void {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const resultado = await this.buscarPagina(0, TAMANO_PAGINA);
      this.leads.set(resultado.contenido);
      this.hayMas.set(resultado.hayMas);
      this.pagina = 0;
    } catch {
      this.errorMessage.set('No se pudieron cargar los leads. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cargarMas(): Promise<void> {
    if (this.isLoadingMore() || !this.hayMas()) return;
    this.isLoadingMore.set(true);
    try {
      const siguiente = this.pagina + 1;
      const resultado = await this.buscarPagina(siguiente, TAMANO_PAGINA);
      this.leads.update((actuales) => [...actuales, ...resultado.contenido]);
      this.hayMas.set(resultado.hayMas);
      this.pagina = siguiente;
    } catch {
      this.errorMessage.set('No se pudieron cargar más leads.');
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  private buscarPagina(pagina: number, tamano: number) {
    const estado = this.estadoFiltro();
    return this.leadsService.buscarPaginado({
      busqueda: this.filtro().trim() || undefined,
      estado: estado ?? undefined,
      asesorId: this.asesorId() ?? undefined,
      etiquetaId: this.etiquetaFiltro() ?? undefined,
      archivados: this.verArchivados(),
      pagina,
      tamano,
    });
  }

  private datosExportacion(leads: Lead[]) {
    const filas = leads.map((l) => ({
      cliente: l.nombreCliente,
      telefono: l.telefono,
      email: l.email ?? '',
      desarrollo: l.desarrolloDetalle ? `${l.desarrollo.nombre} (${l.desarrolloDetalle})` : l.desarrollo.nombre,
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

    const encabezados = {
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
      valorEstimado: 'Presupuesto',
      fechaCreacion: 'Fecha de creación',
      fechaUltimoContacto: 'Último contacto',
      diasSinContacto: 'Días sin contacto',
      frio: 'Lead frío',
    };

    return { filas, encabezados };
  }

  /** La exportación trae TODOS los leads que coinciden con los filtros actuales, no solo los que
   * ya están cargados en pantalla (podrían ser solo los primeros 10). */
  private async obtenerTodosLosFiltrados(): Promise<Lead[]> {
    const resultado = await this.buscarPagina(0, TAMANO_EXPORTACION);
    return resultado.contenido;
  }

  async exportarCsv(): Promise<void> {
    const leads = await this.obtenerTodosLosFiltrados();
    const { filas, encabezados } = this.datosExportacion(leads);
    descargarCsv(`leads_${new Date().toISOString().slice(0, 10)}.csv`, encabezados, filas);
  }

  async exportarExcel(): Promise<void> {
    const leads = await this.obtenerTodosLosFiltrados();
    const { filas, encabezados } = this.datosExportacion(leads);
    await descargarExcel(`leads_${new Date().toISOString().slice(0, 10)}.xlsx`, encabezados, filas, 'Leads');
  }

  async descargarPlantilla(): Promise<void> {
    const desarrollos = await this.leadsService.listarDesarrollos();
    await generarPlantillaLeads(desarrollos.map((d) => d.nombre));
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
