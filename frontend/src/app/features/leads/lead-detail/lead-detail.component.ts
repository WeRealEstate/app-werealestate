import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { EtiquetasService } from '../../../core/services/etiquetas.service';
import { ColumnasService } from '../../../core/services/columnas.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import {
  ColumnaPersonalizada,
  ESTADO_LEAD_LABELS,
  EstadoLead,
  Etiqueta,
  ETIQUETA_BADGE_CLASSES,
  ETIQUETA_COLORES,
  EtiquetaColor,
  ETIQUETA_SWATCH_CLASSES,
  Lead,
  Seguimiento,
  TIPO_SEGUIMIENTO_LABELS,
  TipoSeguimiento,
  UsuarioResumen,
} from '../../../core/models/lead.model';
import { Usuario } from '../../../core/models/user.model';
import {
  DURACION_OPCIONES,
  DURACION_POR_DEFECTO,
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../../core/utils/fecha-hora';

/** Roles que pueden recibir la reasignación de un lead: quienes trabajan leads, más el admin,
 * que también tiene su propia bolsa de leads. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA', 'ADMIN']);

@Component({
  selector: 'app-lead-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './lead-detail.component.html',
})
export class LeadDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly leadsService = inject(LeadsService);
  private readonly etiquetasService = inject(EtiquetasService);
  private readonly columnasService = inject(ColumnasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  readonly estadoLabels = ESTADO_LEAD_LABELS;
  readonly tipoLabels = TIPO_SEGUIMIENTO_LABELS;
  readonly estados = Object.keys(ESTADO_LEAD_LABELS) as EstadoLead[];
  readonly tipos = Object.keys(TIPO_SEGUIMIENTO_LABELS) as TipoSeguimiento[];
  readonly duracionOpciones = DURACION_OPCIONES;
  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly propioId = computed(() => this.auth.currentUser()?.id);
  /** Las tarjetas son por asesor (ver ColumnaPersonalizadaService.resolverAsesorId): solo se
   * pueden consultar las propias, o las de cualquiera si quien mira es admin. Un líder de área
   * viendo el lead de otro asesor no las ve — ni el selector de tarjeta aparece en ese caso. */
  readonly puedeMoverTarjeta = computed(() => {
    const l = this.lead();
    if (!l) return false;
    return this.esAdmin() || l.asesor.id === this.propioId();
  });
  readonly coloresEtiqueta = ETIQUETA_COLORES;
  readonly badgeClasesEtiqueta = ETIQUETA_BADGE_CLASSES;
  readonly swatchClasesEtiqueta = ETIQUETA_SWATCH_CLASSES;

  readonly lead = signal<Lead | null>(null);
  readonly catalogoEtiquetas = signal<Etiqueta[]>([]);
  readonly mostrarPickerEtiquetas = signal(false);
  readonly mostrarFormNuevaEtiqueta = signal(false);
  readonly nuevaEtiquetaNombre = signal('');
  readonly nuevaEtiquetaColor = signal<EtiquetaColor>('BLUE');
  readonly editandoEtiquetaId = signal<number | null>(null);
  readonly editEtiquetaNombre = signal('');
  readonly editEtiquetaColor = signal<EtiquetaColor>('BLUE');
  readonly isGuardandoEtiquetas = signal(false);
  readonly seguimientos = signal<Seguimiento[]>([]);
  readonly columnasDisponibles = signal<ColumnaPersonalizada[]>([]);
  readonly asesores = signal<Usuario[]>([]);
  readonly isLoading = signal(true);
  readonly isSavingEstado = signal(false);
  readonly isSavingAsesor = signal(false);
  readonly isSavingSeguimiento = signal(false);
  readonly isArchivando = signal(false);
  readonly isEliminando = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Los asesores asignables, más el dueño actual del lead si por algún motivo no está en esa lista. */
  readonly opcionesAsesor = computed<UsuarioResumen[]>(() => {
    const lista: UsuarioResumen[] = this.asesores();
    const actual = this.lead()?.asesor;
    if (!actual || lista.some((a) => a.id === actual.id)) return lista;
    return [...lista, actual].sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  private leadId!: number;

  readonly seguimientoForm = this.fb.group({
    tipo: this.fb.control<TipoSeguimiento>('LLAMADA', { nonNullable: true, validators: [Validators.required] }),
    nota: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    resultado: this.fb.control('', { nonNullable: true }),
    proximoSeguimiento: this.fb.control('', { nonNullable: true }),
    horaSeguimiento: this.fb.control(HORA_POR_DEFECTO, { nonNullable: true }),
    minutoSeguimiento: this.fb.control(MINUTO_POR_DEFECTO, { nonNullable: true }),
    duracionSeguimiento: this.fb.control(DURACION_POR_DEFECTO, { nonNullable: true }),
    /** Precargada con la tarjeta actual del lead (ver cargar()); cambiarla mueve el lead de
     * tarjeta en el mismo Pipeline al registrar este seguimiento (ver registrarSeguimiento()). */
    columnaPersonalizadaId: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    // Angular reutiliza esta misma instancia al navegar de un lead a otro (misma ruta,
    // distinto :id) — por eso el id se lee de forma reactiva y no solo una vez en el snapshot,
    // si no la página se quedaba mostrando el primer lead abierto al abrir otra notificación.
    this.route.paramMap.subscribe((params) => {
      this.leadId = Number(params.get('id'));
      this.cargar();
    });

    if (this.esAdmin()) {
      this.usuariosService.listar().then((usuarios) => {
        this.asesores.set(usuarios.filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol)));
      });
    }
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [lead, seguimientos] = await Promise.all([
        this.leadsService.obtener(this.leadId),
        this.leadsService.listarSeguimientos(this.leadId),
      ]);
      this.lead.set(lead);
      this.seguimientos.set(seguimientos);
      this.catalogoEtiquetas.set(await this.etiquetasService.listar(lead.asesor.id));
      this.seguimientoForm.controls.columnaPersonalizadaId.setValue(lead.columnaPersonalizadaId);
      this.columnasDisponibles.set(
        this.esAdmin() || lead.asesor.id === this.propioId()
          ? await this.columnasService.listar(lead.asesor.id)
          : [],
      );
    } catch {
      this.errorMessage.set('No se pudo cargar el lead.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async cambiarEstado(nuevoEstado: EstadoLead): Promise<void> {
    const actual = this.lead();
    if (!actual || actual.estado === nuevoEstado) return;

    this.isSavingEstado.set(true);
    try {
      const actualizado = await this.leadsService.actualizar(this.leadId, {
        nombreCliente: actual.nombreCliente,
        telefono: actual.telefono,
        email: actual.email,
        origen: actual.origen,
        desarrolloId: actual.desarrollo.id,
        estado: nuevoEstado,
        valorEstimado: actual.valorEstimado,
        edad: actual.edad,
        pais: actual.pais,
        estadoRepublica: actual.estadoRepublica,
      });
      this.lead.set(actualizado);
      this.toast.success(`Estado actualizado a "${this.estadoLabels[nuevoEstado]}".`);
    } catch {
      this.errorMessage.set('No se pudo actualizar el estado.');
      this.toast.error('No se pudo actualizar el estado.');
    } finally {
      this.isSavingEstado.set(false);
    }
  }

  async reasignar(nuevoAsesorId: number): Promise<void> {
    const actual = this.lead();
    if (!actual || actual.asesor.id === nuevoAsesorId) return;

    this.isSavingAsesor.set(true);
    this.errorMessage.set(null);
    try {
      const actualizado = await this.leadsService.reasignar(this.leadId, nuevoAsesorId);
      this.lead.set(actualizado);
      this.catalogoEtiquetas.set(await this.etiquetasService.listar(actualizado.asesor.id));
      this.mostrarPickerEtiquetas.set(false);
      this.toast.success(`Lead reasignado a ${actualizado.asesor.nombre}.`);
    } catch {
      this.errorMessage.set('No se pudo reasignar el lead.');
      this.toast.error('No se pudo reasignar el lead.');
    } finally {
      this.isSavingAsesor.set(false);
    }
  }

  async archivar(): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isArchivando()) return;
    const confirmado = await this.confirmService.confirm({
      titulo: 'Archivar lead',
      mensaje: `¿Archivar a ${actual.nombreCliente}? Dejará de aparecer en la lista activa, pero se conserva como métrica y puedes desarchivarlo cuando quieras.`,
      textoConfirmar: 'Archivar',
    });
    if (!confirmado) return;

    this.isArchivando.set(true);
    try {
      const actualizado = await this.leadsService.archivar(this.leadId);
      this.lead.set(actualizado);
      this.toast.success(`${actualizado.nombreCliente} fue archivado.`);
    } catch {
      this.toast.error('No se pudo archivar el lead.');
    } finally {
      this.isArchivando.set(false);
    }
  }

  async desarchivar(): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isArchivando()) return;

    this.isArchivando.set(true);
    try {
      const actualizado = await this.leadsService.desarchivar(this.leadId);
      this.lead.set(actualizado);
      this.toast.success(`${actualizado.nombreCliente} fue desarchivado.`);
    } catch {
      this.toast.error('No se pudo desarchivar el lead.');
    } finally {
      this.isArchivando.set(false);
    }
  }

  /** Borrado permanente, solo admin. A diferencia de archivar, esto no se puede deshacer. */
  async eliminar(): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isEliminando()) return;
    const confirmado = await this.confirmService.confirm({
      titulo: 'Borrar lead',
      mensaje: `¿Borrar a ${actual.nombreCliente} para siempre? Esto elimina también su bitácora de seguimientos y no se puede deshacer.`,
      textoConfirmar: 'Borrar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.isEliminando.set(true);
    try {
      await this.leadsService.eliminar(this.leadId);
      this.toast.success(`${actual.nombreCliente} fue borrado.`);
      this.router.navigateByUrl('/panel/leads');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo borrar el lead.';
      this.toast.error(mensaje);
    } finally {
      this.isEliminando.set(false);
    }
  }

  /** Si la tarjeta elegida en el formulario es distinta a la actual del lead, se registra vía
   * moverColumna (mismo endpoint que usa arrastrar una tarjeta en el Pipeline: mueve Y deja el
   * seguimiento en una sola operación); si no cambió, un crearSeguimiento normal — moverColumna
   * no crea el seguimiento cuando la tarjeta es la misma (ver LeadService.moverAColumnaPersonalizada). */
  async registrarSeguimiento(): Promise<void> {
    if (this.seguimientoForm.invalid || this.isSavingSeguimiento()) {
      this.seguimientoForm.markAllAsTouched();
      return;
    }

    this.isSavingSeguimiento.set(true);
    this.errorMessage.set(null);
    const v = this.seguimientoForm.getRawValue();
    const tarjetaActualId = this.lead()?.columnaPersonalizadaId ?? null;
    const cambiaTarjeta = this.puedeMoverTarjeta() && v.columnaPersonalizadaId !== tarjetaActualId;

    const datosSeguimiento = {
      tipo: v.tipo,
      nota: v.nota,
      resultado: v.resultado || null,
      proximoSeguimiento: v.proximoSeguimiento
        ? combinarFechaHora(v.proximoSeguimiento, v.horaSeguimiento, v.minutoSeguimiento)
        : null,
      duracionMinutos: v.proximoSeguimiento ? v.duracionSeguimiento : null,
    };

    try {
      let leadActualizado: Lead;
      if (cambiaTarjeta) {
        leadActualizado = await this.leadsService.moverColumna(this.leadId, {
          columnaPersonalizadaId: v.columnaPersonalizadaId,
          ...datosSeguimiento,
        });
        this.seguimientos.set(await this.leadsService.listarSeguimientos(this.leadId));
      } else {
        const nuevo = await this.leadsService.crearSeguimiento(this.leadId, datosSeguimiento);
        this.seguimientos.update((lista) => [nuevo, ...lista]);
        leadActualizado = await this.leadsService.obtener(this.leadId);
      }
      this.lead.set(leadActualizado);

      this.seguimientoForm.reset({
        tipo: 'LLAMADA',
        nota: '',
        resultado: '',
        proximoSeguimiento: '',
        horaSeguimiento: HORA_POR_DEFECTO,
        minutoSeguimiento: MINUTO_POR_DEFECTO,
        duracionSeguimiento: DURACION_POR_DEFECTO,
        columnaPersonalizadaId: leadActualizado.columnaPersonalizadaId,
      });
      this.toast.success(cambiaTarjeta ? 'Seguimiento registrado y tarjeta actualizada.' : 'Seguimiento registrado.');
    } catch {
      this.errorMessage.set('No se pudo registrar el seguimiento.');
      this.toast.error('No se pudo registrar el seguimiento.');
    } finally {
      this.isSavingSeguimiento.set(false);
    }
  }

  // --- Etiquetas ---

  togglePickerEtiquetas(): void {
    this.mostrarPickerEtiquetas.update((v) => !v);
    this.mostrarFormNuevaEtiqueta.set(false);
    this.editandoEtiquetaId.set(null);
  }

  tieneEtiqueta(etiquetaId: number): boolean {
    return this.lead()?.etiquetas.some((e) => e.id === etiquetaId) ?? false;
  }

  async toggleEtiquetaEnLead(etiqueta: Etiqueta): Promise<void> {
    const actual = this.lead();
    if (!actual || this.isGuardandoEtiquetas()) return;

    const idsActuales = actual.etiquetas.map((e) => e.id);
    const nuevosIds = idsActuales.includes(etiqueta.id)
      ? idsActuales.filter((id) => id !== etiqueta.id)
      : [...idsActuales, etiqueta.id];

    this.isGuardandoEtiquetas.set(true);
    try {
      const actualizado = await this.leadsService.asignarEtiquetas(this.leadId, nuevosIds);
      this.lead.set(actualizado);
    } catch {
      this.toast.error('No se pudo actualizar las etiquetas del lead.');
    } finally {
      this.isGuardandoEtiquetas.set(false);
    }
  }

  abrirFormNuevaEtiqueta(): void {
    this.mostrarFormNuevaEtiqueta.set(true);
    this.nuevaEtiquetaNombre.set('');
    this.nuevaEtiquetaColor.set('BLUE');
  }

  async crearEtiqueta(): Promise<void> {
    const actual = this.lead();
    const nombre = this.nuevaEtiquetaNombre().trim();
    if (!actual || !nombre || this.isGuardandoEtiquetas()) return;

    this.isGuardandoEtiquetas.set(true);
    try {
      const nueva = await this.etiquetasService.crear({
        nombre,
        color: this.nuevaEtiquetaColor(),
        asesorId: actual.asesor.id,
      });
      this.catalogoEtiquetas.update((lista) => [...lista, nueva].sort((a, b) => a.nombre.localeCompare(b.nombre)));
      this.mostrarFormNuevaEtiqueta.set(false);

      const nuevosIds = [...actual.etiquetas.map((e) => e.id), nueva.id];
      const actualizado = await this.leadsService.asignarEtiquetas(this.leadId, nuevosIds);
      this.lead.set(actualizado);

      this.toast.success(`Etiqueta "${nueva.nombre}" creada.`);
    } catch {
      this.toast.error('No se pudo crear la etiqueta.');
    } finally {
      this.isGuardandoEtiquetas.set(false);
    }
  }

  abrirEdicionEtiqueta(etiqueta: Etiqueta): void {
    this.editandoEtiquetaId.set(etiqueta.id);
    this.editEtiquetaNombre.set(etiqueta.nombre);
    this.editEtiquetaColor.set(etiqueta.color);
    this.mostrarFormNuevaEtiqueta.set(false);
  }

  cancelarEdicionEtiqueta(): void {
    this.editandoEtiquetaId.set(null);
  }

  async guardarEdicionEtiqueta(): Promise<void> {
    const id = this.editandoEtiquetaId();
    const nombre = this.editEtiquetaNombre().trim();
    if (id === null || !nombre) return;

    this.isGuardandoEtiquetas.set(true);
    try {
      const actualizada = await this.etiquetasService.actualizar(id, { nombre, color: this.editEtiquetaColor() });
      this.catalogoEtiquetas.update((lista) =>
        lista.map((e) => (e.id === id ? actualizada : e)).sort((a, b) => a.nombre.localeCompare(b.nombre)),
      );
      this.lead.update((l) => (l ? { ...l, etiquetas: l.etiquetas.map((e) => (e.id === id ? actualizada : e)) } : l));
      this.editandoEtiquetaId.set(null);
      this.toast.success('Etiqueta actualizada.');
    } catch {
      this.toast.error('No se pudo actualizar la etiqueta.');
    } finally {
      this.isGuardandoEtiquetas.set(false);
    }
  }

  async eliminarEtiqueta(etiqueta: Etiqueta): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar etiqueta',
      mensaje: `¿Eliminar la etiqueta "${etiqueta.nombre}" de tu catálogo? Esto no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.isGuardandoEtiquetas.set(true);
    try {
      await this.etiquetasService.eliminar(etiqueta.id);
      this.catalogoEtiquetas.update((lista) => lista.filter((e) => e.id !== etiqueta.id));
      this.lead.update((l) => (l ? { ...l, etiquetas: l.etiquetas.filter((e) => e.id !== etiqueta.id) } : l));
      this.toast.success(`Etiqueta "${etiqueta.nombre}" eliminada.`);
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar la etiqueta.';
      this.toast.error(mensaje);
    } finally {
      this.isGuardandoEtiquetas.set(false);
    }
  }
}
