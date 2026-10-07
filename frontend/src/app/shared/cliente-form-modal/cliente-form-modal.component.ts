import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideX } from '@lucide/angular';
import { UbicacionDocumento, UBICACION_DOCUMENTO_LABELS } from '../../core/models/asesor-externo.model';
import {
  Cliente,
  ClienteRequest,
  ESTADO_CIVIL_LABELS,
  EstadoCivil,
  FUENTE_CLIENTE_LABELS,
  FuenteCliente,
} from '../../core/models/cliente.model';
import { UsuarioResumen } from '../../core/models/lead.model';
import { AsesorExterno } from '../../core/models/asesor-externo.model';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { ClientesService } from '../../core/services/clientes.service';
import { UsuariosService } from '../../core/services/usuarios.service';

/** Alta y edición de un cliente (ficha completa). Se usa en /panel/clientes, en su ficha y en el selector de la venta. */
@Component({
  selector: 'app-cliente-form-modal',
  imports: [ReactiveFormsModule, LucideX],
  templateUrl: './cliente-form-modal.component.html',
})
export class ClienteFormModalComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly clientesService = inject(ClientesService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresService = inject(AsesoresExternosService);

  /** null = alta. */
  readonly cliente = input<Cliente | null>(null);
  /** Datos con los que arranca un alta (por ejemplo lo que se escribió en el buscador). */
  readonly nombreInicial = input('');
  readonly guardado = output<Cliente>();
  readonly cerrado = output<void>();
  /** Ya existía uno igual: el padre puede ofrecer usarlo. */
  readonly duplicado = output<number>();

  readonly estados = Object.keys(ESTADO_CIVIL_LABELS) as EstadoCivil[];
  readonly estadoLabels = ESTADO_CIVIL_LABELS;
  readonly fuentes = Object.keys(FUENTE_CLIENTE_LABELS) as FuenteCliente[];
  readonly fuenteLabels = FUENTE_CLIENTE_LABELS;
  readonly ubicaciones: UbicacionDocumento[] = ['DRIVE', 'FISICO', 'AMBOS', 'NO_TIENE'];
  readonly ubicacionLabels = UBICACION_DOCUMENTO_LABELS;
  readonly hoy = new Date().toISOString().slice(0, 10);

  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly duplicadoId = signal<number | null>(null);
  readonly usuarios = signal<UsuarioResumen[]>([]);
  readonly asesores = signal<AsesorExterno[]>([]);
  readonly esEdicion = computed(() => this.cliente() !== null);

  readonly form = this.fb.group({
    nombre: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    apellidoPaterno: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    apellidoMaterno: this.fb.control('', { nonNullable: true }),
    fechaNacimiento: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono2: this.fb.control('', { nonNullable: true }),
    correo: this.fb.control('', { nonNullable: true, validators: [Validators.email] }),
    curp: this.fb.control('', { nonNullable: true, validators: [Validators.maxLength(18)] }),
    rfc: this.fb.control('', { nonNullable: true, validators: [Validators.maxLength(13)] }),
    lugarNacimiento: this.fb.control('', { nonNullable: true }),
    nacionalidad: this.fb.control('', { nonNullable: true }),
    estadoCivil: this.fb.control<EstadoCivil | ''>('', { nonNullable: true }),
    ocupacion: this.fb.control('', { nonNullable: true }),
    calle: this.fb.control('', { nonNullable: true }),
    colonia: this.fb.control('', { nonNullable: true }),
    municipio: this.fb.control('', { nonNullable: true }),
    estado: this.fb.control('', { nonNullable: true }),
    codigoPostal: this.fb.control('', { nonNullable: true }),
    beneficiarioNombre: this.fb.control('', { nonNullable: true }),
    beneficiarioParentesco: this.fb.control('', { nonNullable: true }),
    fuente: this.fb.control<FuenteCliente | ''>('', { nonNullable: true }),
    fuenteDetalle: this.fb.control('', { nonNullable: true }),
    captadoPor: this.fb.control('', { nonNullable: true }),
    notas: this.fb.control('', { nonNullable: true }),
    expedienteUbicacion: this.fb.control<UbicacionDocumento | ''>('', { nonNullable: true }),
    expedienteDriveUrl: this.fb.control('', { nonNullable: true }),
    activo: this.fb.control(true, { nonNullable: true }),
  });

  ngOnInit(): void {
    const c = this.cliente();
    if (c) {
      this.form.reset({
        nombre: c.nombre,
        apellidoPaterno: c.apellidoPaterno ?? '',
        apellidoMaterno: c.apellidoMaterno ?? '',
        fechaNacimiento: c.fechaNacimiento ?? '',
        telefono: c.telefono ?? '',
        telefono2: c.telefono2 ?? '',
        correo: c.correo ?? '',
        curp: c.curp ?? '',
        rfc: c.rfc ?? '',
        lugarNacimiento: c.lugarNacimiento ?? '',
        nacionalidad: c.nacionalidad ?? '',
        estadoCivil: c.estadoCivil ?? '',
        ocupacion: c.ocupacion ?? '',
        calle: c.calle ?? '',
        colonia: c.colonia ?? '',
        municipio: c.municipio ?? '',
        estado: c.estado ?? '',
        codigoPostal: c.codigoPostal ?? '',
        beneficiarioNombre: c.beneficiarioNombre ?? '',
        beneficiarioParentesco: c.beneficiarioParentesco ?? '',
        fuente: c.fuente ?? '',
        fuenteDetalle: c.fuenteDetalle ?? '',
        captadoPor: c.captadoPorTipo === 'USUARIO' ? `U-${c.captadoPorId}` : c.captadoPorTipo === 'ASESOR' ? `A-${c.captadoPorId}` : '',
        notas: c.notas ?? '',
        expedienteUbicacion: c.expedienteUbicacion ?? '',
        expedienteDriveUrl: c.expedienteDriveUrl ?? '',
        activo: c.activo,
      });
    } else {
      const partes = this.nombreInicial().trim().split(/\s+/).filter(Boolean);
      if (partes.length > 0) {
        this.form.patchValue({ nombre: partes.slice(0, partes.length > 2 ? partes.length - 2 : 1).join(' ') });
      }
    }
    this.usuariosService.paraVenta().then((u) => this.usuarios.set(u)).catch(() => undefined);
    this.asesoresService.listarActivos().then((a) => this.asesores.set(a)).catch(() => undefined);
  }

  async guardar(): Promise<void> {
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const texto = (s: string): string | null => s.trim() || null;
    const request: ClienteRequest = {
      nombre: v.nombre.trim(),
      apellidoPaterno: v.apellidoPaterno.trim(),
      apellidoMaterno: texto(v.apellidoMaterno),
      fechaNacimiento: v.fechaNacimiento,
      telefono: v.telefono.trim(),
      telefono2: texto(v.telefono2),
      correo: texto(v.correo),
      curp: texto(v.curp)?.toUpperCase() ?? null,
      rfc: texto(v.rfc)?.toUpperCase() ?? null,
      lugarNacimiento: texto(v.lugarNacimiento),
      nacionalidad: texto(v.nacionalidad),
      estadoCivil: v.estadoCivil || null,
      ocupacion: texto(v.ocupacion),
      calle: texto(v.calle),
      colonia: texto(v.colonia),
      municipio: texto(v.municipio),
      estado: texto(v.estado),
      codigoPostal: texto(v.codigoPostal),
      beneficiarioNombre: texto(v.beneficiarioNombre),
      beneficiarioParentesco: texto(v.beneficiarioParentesco),
      fuente: v.fuente || null,
      fuenteDetalle: texto(v.fuenteDetalle),
      captadoPorUsuarioId: v.captadoPor.startsWith('U-') ? Number(v.captadoPor.slice(2)) : null,
      captadoPorAsesorId: v.captadoPor.startsWith('A-') ? Number(v.captadoPor.slice(2)) : null,
      notas: texto(v.notas),
      expedienteUbicacion: v.expedienteUbicacion || null,
      expedienteDriveUrl: texto(v.expedienteDriveUrl),
      activo: this.esEdicion() ? v.activo : null,
    };

    this.guardando.set(true);
    this.error.set(null);
    this.duplicadoId.set(null);
    try {
      const c = this.cliente();
      const resultado = c ? await this.clientesService.actualizar(c.id, request) : await this.clientesService.crear(request);
      this.guardado.emit(resultado);
    } catch (e) {
      const err = e as HttpErrorResponse;
      if (err.status === 409 && err.error?.clienteId) {
        this.duplicadoId.set(err.error.clienteId);
      }
      this.error.set(err.error?.message ?? 'No se pudo guardar el cliente.');
    } finally {
      this.guardando.set(false);
    }
  }

  usarExistente(): void {
    const id = this.duplicadoId();
    if (id !== null) this.duplicado.emit(id);
  }
}
