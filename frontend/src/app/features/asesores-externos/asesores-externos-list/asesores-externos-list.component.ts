import { Component, computed, inject, signal } from '@angular/core';
import { LucideEye, LucideExternalLink, LucidePencil, LucideTrash2 } from '@lucide/angular';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { UsuarioResumen } from '../../../core/models/lead.model';
import { AsesoresExternosService } from '../../../core/services/asesores-externos.service';
import {
  AsesorExterno,
  AsesorExternoUpdateRequest,
  AsesorFicha,
  EXPERIENCIAS_ASESOR,
  EXPERIENCIA_ASESOR_LABELS,
  ExperienciaAsesor,
  UBICACION_DOCUMENTO_LABELS,
  UbicacionDocumento,
  ESTADO_CONTRATO_CLASES,
  ESTADO_CONTRATO_LABELS,
  ESTADOS_CONTRATO,
  EstadoContratoAsesor,
} from '../../../core/models/asesor-externo.model';

type FiltroDesarrollo = '' | 'SAMAI' | 'NANUU' | 'AMBOS' | 'NINGUNO';

const ordenarAsesoresPorNombre = (asesores: AsesorExterno[]): AsesorExterno[] =>
  [...asesores].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-MX', { sensitivity: 'base' }));

@Component({
  selector: 'app-asesores-externos-list',
  standalone: true,
  imports: [FormsModule, RouterLink, LucideEye, LucideExternalLink, LucidePencil, LucideTrash2],
  templateUrl: './asesores-externos-list.component.html',
})
export class AsesoresExternosListComponent {
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly asesores = signal<AsesorExterno[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  // --- Tarjeta flotante para agregar uno nuevo: el botón "+ Agregar" siempre está habilitado
  // (no depende de ningún campo) y solo abre el formulario; el propio formulario valida al guardar. ---
  readonly mostrarModalCrear = signal(false);
  readonly nuevoNombre = signal('');
  readonly nuevoCelular = signal('');
  readonly nuevoCorreo = signal('');
  readonly isCreando = signal(false);
  readonly errorCreacion = signal<string | null>(null);

  readonly nuevoContratoEstado = signal<EstadoContratoAsesor>('VIGENTE');
  readonly nuevoFechaFirma = signal('');
  readonly nuevoFechaVencimiento = signal('');
  readonly nuevoAccesoSamai = signal(true);
  readonly nuevoAccesoNanuu = signal(true);

  readonly experiencias = EXPERIENCIAS_ASESOR;
  readonly experienciaLabels = EXPERIENCIA_ASESOR_LABELS;
  readonly ubicacionLabels = UBICACION_DOCUMENTO_LABELS;
  readonly ubicacionesContrato: UbicacionDocumento[] = ['FISICO', 'DRIVE', 'AMBOS', 'NO_TIENE'];
  readonly ubicacionesExpediente: UbicacionDocumento[] = ['FISICO', 'DRIVE', 'AMBOS'];

  // --- Ficha del asesor (ojo): datos secundarios, en un modal de lectura que se puede editar ---
  readonly fichaDe = signal<AsesorExterno | null>(null);
  readonly ficha = signal<AsesorFicha | null>(null);
  readonly cargandoFicha = signal(false);
  readonly editandoFicha = signal(false);
  readonly guardandoFicha = signal(false);
  readonly errorFicha = signal<string | null>(null);
  readonly usuariosParaFicha = signal<UsuarioResumen[]>([]);

  readonly fExperiencia = signal<ExperienciaAsesor | ''>('');
  readonly fContrato = signal<UbicacionDocumento | ''>('');
  readonly fContratoUrl = signal('');
  /** '' = sin definir, 'SI' = aplica, 'NO' = no aplica. */
  readonly fExpedienteAplica = signal<'' | 'SI' | 'NO'>('');
  readonly fExpedienteUbicacion = signal<UbicacionDocumento | ''>('');
  readonly fExpedienteUrl = signal('');
  /** '' = sin definir, 'U-id' usuario, 'A-id' asesor externo, 'OTRO' = escribir un nombre / evento. */
  readonly fTraidoPor = signal('');
  readonly fTraidoPorOtro = signal('');
  readonly fNotas = signal('');

  /** Los demás asesores externos, para elegir quién trajo a este. */
  readonly otrosAsesores = computed(() => this.asesores().filter((a) => a.id !== this.fichaDe()?.id));

  readonly estadosContrato = ESTADOS_CONTRATO;
  readonly contratoLabels = ESTADO_CONTRATO_LABELS;
  readonly contratoClases = ESTADO_CONTRATO_CLASES;

  // Filtros de la lista: por estado de contrato (efectivo) y por a qué planos tiene acceso.
  readonly filtroContrato = signal<'' | EstadoContratoAsesor>('');
  readonly filtroDesarrollo = signal<FiltroDesarrollo>('');
  readonly asesoresFiltrados = computed(() => {
    const contrato = this.filtroContrato();
    const desarrollo = this.filtroDesarrollo();
    return this.asesores().filter((a) => {
      if (contrato && a.contratoEstadoEfectivo !== contrato) return false;
      switch (desarrollo) {
        case 'SAMAI':
          return a.accesoSamai && !a.accesoNanuu;
        case 'NANUU':
          return a.accesoNanuu && !a.accesoSamai;
        case 'AMBOS':
          return a.accesoSamai && a.accesoNanuu;
        case 'NINGUNO':
          return !a.accesoSamai && !a.accesoNanuu;
        default:
          return true;
      }
    });
  });

  readonly editandoId = signal<number | null>(null);
  readonly contratoEnEdicion = signal<EstadoContratoAsesor>('VIGENTE');
  readonly fechaFirmaEnEdicion = signal('');
  readonly fechaVencimientoEnEdicion = signal('');
  readonly accesoSamaiEnEdicion = signal(true);
  readonly accesoNanuuEnEdicion = signal(true);
  readonly nombreEnEdicion = signal('');
  readonly celularEnEdicion = signal('');
  readonly correoEnEdicion = signal('');

  constructor() {
    this.cargar();
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.asesores.set(ordenarAsesoresPorNombre(await this.asesoresExternosService.listar()));
    } catch {
      this.errorMessage.set('No se pudieron cargar los asesores externos. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  abrirModalCrear(): void {
    this.nuevoNombre.set('');
    this.nuevoCelular.set('');
    this.nuevoCorreo.set('');
    this.nuevoContratoEstado.set('VIGENTE');
    this.nuevoFechaFirma.set('');
    this.nuevoFechaVencimiento.set('');
    this.nuevoAccesoSamai.set(true);
    this.nuevoAccesoNanuu.set(true);
    this.errorCreacion.set(null);
    this.mostrarModalCrear.set(true);
  }

  cerrarModalCrear(): void {
    this.mostrarModalCrear.set(false);
  }

  async crear(): Promise<void> {
    const nombre = this.nuevoNombre().trim();
    const celular = this.nuevoCelular().trim();
    const correo = this.nuevoCorreo().trim();
    if (!nombre || !celular || this.isCreando()) {
      this.errorCreacion.set('Nombre y celular son obligatorios.');
      return;
    }

    const firma = this.nuevoFechaFirma();
    const vencimiento = this.nuevoFechaVencimiento();
    if (firma && vencimiento && vencimiento < firma) {
      this.errorCreacion.set('La fecha de vencimiento no puede ser anterior a la de firma.');
      return;
    }

    this.isCreando.set(true);
    this.errorCreacion.set(null);
    try {
      const creado = await this.asesoresExternosService.crear({
        nombre,
        celular,
        correo: correo || null,
        contratoEstado: this.nuevoContratoEstado(),
        contratoFechaFirma: firma || null,
        contratoFechaVencimiento: vencimiento || null,
        accesoSamai: this.nuevoAccesoSamai(),
        accesoNanuu: this.nuevoAccesoNanuu(),
      });
      this.asesores.update((lista) => ordenarAsesoresPorNombre([...lista, creado]));
      this.mostrarModalCrear.set(false);
      this.toast.success(`${creado.nombre} fue agregado.`);
    } catch (error) {
      this.errorCreacion.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo agregar el asesor externo.',
      );
    } finally {
      this.isCreando.set(false);
    }
  }

  async abrirFicha(asesor: AsesorExterno): Promise<void> {
    this.fichaDe.set(asesor);
    this.ficha.set(null);
    this.editandoFicha.set(false);
    this.errorFicha.set(null);
    this.cargandoFicha.set(true);
    try {
      this.ficha.set(await this.asesoresExternosService.ficha(asesor.id));
    } catch {
      this.errorFicha.set('No se pudo cargar la ficha. Intenta de nuevo.');
    } finally {
      this.cargandoFicha.set(false);
    }
  }

  cerrarFicha(): void {
    this.fichaDe.set(null);
    this.editandoFicha.set(false);
  }

  async editarFicha(): Promise<void> {
    const f = this.ficha();
    if (!f) return;
    if (this.usuariosParaFicha().length === 0) {
      this.usuariosService.paraVenta().then((u) => this.usuariosParaFicha.set(u)).catch(() => undefined);
    }
    this.fExperiencia.set(f.experiencia ?? '');
    this.fContrato.set(f.contratoCopia ?? '');
    this.fContratoUrl.set(f.contratoDriveUrl ?? '');
    this.fExpedienteAplica.set(f.expedienteAplica === null ? '' : f.expedienteAplica ? 'SI' : 'NO');
    this.fExpedienteUbicacion.set(f.expedienteUbicacion ?? '');
    this.fExpedienteUrl.set(f.expedienteDriveUrl ?? '');
    this.fTraidoPor.set(
      f.traidoPorTipo === 'USUARIO' ? `U-${f.traidoPorId}` : f.traidoPorTipo === 'ASESOR' ? `A-${f.traidoPorId}` : f.traidoPorTipo === 'OTRO' ? 'OTRO' : '',
    );
    this.fTraidoPorOtro.set(f.traidoPorTipo === 'OTRO' ? (f.traidoPorNombre ?? '') : '');
    this.fNotas.set(f.notas ?? '');
    this.errorFicha.set(null);
    this.editandoFicha.set(true);
  }

  async guardarFicha(): Promise<void> {
    const asesor = this.fichaDe();
    if (!asesor || this.guardandoFicha()) return;
    const quien = this.fTraidoPor();
    const otro = this.fTraidoPorOtro().trim();
    if (quien === 'OTRO' && !otro) {
      this.errorFicha.set('Escribe quién lo trajo o dónde se captó (por ejemplo, "Captado en evento Expo").');
      return;
    }
    const contrato = this.fContrato() || null;
    const aplica = this.fExpedienteAplica();
    this.guardandoFicha.set(true);
    this.errorFicha.set(null);
    try {
      const guardada = await this.asesoresExternosService.actualizarFicha(asesor.id, {
        experiencia: this.fExperiencia() || null,
        contratoCopia: contrato,
        contratoDriveUrl: this.fContratoUrl().trim() || null,
        expedienteAplica: aplica === '' ? null : aplica === 'SI',
        expedienteUbicacion: aplica === 'SI' ? this.fExpedienteUbicacion() || null : null,
        expedienteDriveUrl: this.fExpedienteUrl().trim() || null,
        traidoPorUsuarioId: quien.startsWith('U-') ? Number(quien.slice(2)) : null,
        traidoPorAsesorId: quien.startsWith('A-') ? Number(quien.slice(2)) : null,
        traidoPorOtro: quien === 'OTRO' ? otro : null,
        notas: this.fNotas().trim() || null,
      });
      this.ficha.set(guardada);
      this.editandoFicha.set(false);
      this.toast.success('Ficha guardada.');
    } catch (error) {
      this.errorFicha.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo guardar la ficha.',
      );
    } finally {
      this.guardandoFicha.set(false);
    }
  }

  async toggleActivo(asesor: AsesorExterno): Promise<void> {
    await this.guardar(asesor, {
      nombre: asesor.nombre,
      celular: asesor.celular,
      correo: asesor.correo,
      activo: !asesor.activo,
      tipo: asesor.tipo,
      liderDirectoId: asesor.liderDirectoId,
    });
  }

  abrirEdicion(asesor: AsesorExterno): void {
    this.editandoId.set(asesor.id);
    this.nombreEnEdicion.set(asesor.nombre);
    this.celularEnEdicion.set(asesor.celular ?? '');
    this.correoEnEdicion.set(asesor.correo ?? '');
    this.contratoEnEdicion.set(asesor.contratoEstado);
    this.fechaFirmaEnEdicion.set(asesor.contratoFechaFirma ?? '');
    this.fechaVencimientoEnEdicion.set(asesor.contratoFechaVencimiento ?? '');
    this.accesoSamaiEnEdicion.set(asesor.accesoSamai);
    this.accesoNanuuEnEdicion.set(asesor.accesoNanuu);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
  }

  async guardarEdicion(asesor: AsesorExterno): Promise<void> {
    const nombre = this.nombreEnEdicion().trim();
    if (!nombre) return;
    const firma = this.fechaFirmaEnEdicion();
    const vencimiento = this.fechaVencimientoEnEdicion();
    if (firma && vencimiento && vencimiento < firma) {
      this.toast.error('La fecha de vencimiento no puede ser anterior a la de firma.');
      return;
    }
    await this.guardar(asesor, {
      nombre,
      celular: this.celularEnEdicion().trim() || null,
      correo: this.correoEnEdicion().trim() || null,
      activo: asesor.activo,
      tipo: asesor.tipo,
      liderDirectoId: asesor.liderDirectoId,
      contratoEstado: this.contratoEnEdicion(),
      contratoFechaFirma: firma || null,
      contratoFechaVencimiento: vencimiento || null,
      accesoSamai: this.accesoSamaiEnEdicion(),
      accesoNanuu: this.accesoNanuuEnEdicion(),
    });
    this.editandoId.set(null);
  }

  private async guardar(asesor: AsesorExterno, cambios: AsesorExternoUpdateRequest): Promise<void> {
    this.savingId.set(asesor.id);
    try {
      const actualizado = await this.asesoresExternosService.actualizar(asesor.id, cambios);
      this.asesores.update((lista) => ordenarAsesoresPorNombre(lista.map((a) => (a.id === actualizado.id ? actualizado : a))));
    } catch {
      this.toast.error('No se pudo actualizar el asesor externo.');
    } finally {
      this.savingId.set(null);
    }
  }

  /** "2026-03-15" → "15/03/2026" sin pasar por Date (evita el corrimiento de zona horaria). */
  fechaCorta(iso: string | null): string {
    if (!iso) return '';
    const [anio, mes, dia] = iso.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  /** Solo informativo (la jerarquía se maneja en /panel/teams): para que se entienda de un vistazo
   * por qué un asesor no se deja eliminar o cambiar de tipo (ver AsesorExternoService.eliminar). */
  equipoLabel(asesor: AsesorExterno): string {
    if (asesor.tipo === 'LIDER') return 'Líder';
    if (asesor.tipo === 'LINEA') return `Línea ${asesor.nivelLinea} · ${asesor.liderDirectoNombre}`;
    return '—';
  }

  async eliminar(asesor: AsesorExterno): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Eliminar asesor externo',
      mensaje: `¿Eliminar a ${asesor.nombre} definitivamente? Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!confirmado) return;

    this.savingId.set(asesor.id);
    try {
      await this.asesoresExternosService.eliminar(asesor.id);
      this.asesores.update((lista) => lista.filter((a) => a.id !== asesor.id));
      this.toast.success(`${asesor.nombre} fue eliminado.`);
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar el asesor externo.';
      this.toast.error(mensaje);
    } finally {
      this.savingId.set(null);
    }
  }
}
