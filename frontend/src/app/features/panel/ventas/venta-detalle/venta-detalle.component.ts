import { ClienteLista } from '../../../../core/models/cliente.model';
import { CopropietariosEditorComponent, hayCopropietariosRepetidos, idsCopropietarios } from '../../../../shared/copropietarios-editor/copropietarios-editor.component';
import { ClienteSelectorComponent } from '../../../../shared/cliente-selector/cliente-selector.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { LucidePencil, LucideTrash2 } from '@lucide/angular';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MESES_NOMBRE } from '../../../../core/utils/aportaciones';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AsesoresExternosService } from '../../../../core/services/asesores-externos.service';
import { PagoVenta, Venta } from '../../../../core/models/venta.model';
import { UsuarioResumen } from '../../../../core/models/lead.model';
import { AsesorExterno } from '../../../../core/models/asesor-externo.model';
import { asesorSeleccionDe, parseAsesorSeleccion } from '../venta-form/venta-form.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';
import { MonedaInputDirective } from '../../../../shared/moneda-input/moneda-input.directive';
import { fechaLarga, porcentajeDe, ultimaMensualidad } from '../../../../core/utils/financiamiento';

const SEGUNDOS_ESPERA_ELIMINAR = 10;

@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [MonedaInputDirective, WeLoaderComponent, ReactiveFormsModule, RouterLink, ClienteSelectorComponent, CopropietariosEditorComponent, LucidePencil, LucideTrash2],
  templateUrl: './venta-detalle.component.html',
})
export class VentaDetalleComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly ventasService = inject(VentasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly venta = signal<Venta | null>(null);
  readonly asesoresInternos = signal<UsuarioResumen[]>([]);
  readonly asesoresExternos = signal<AsesorExterno[]>([]);
  readonly pagos = signal<PagoVenta[]>([]);
  readonly isLoading = signal(true);
  readonly isSavingPago = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly pagoError = signal<string | null>(null);

  /** id estable de la venta (para escribir); se conoce al cargarla por el número de la dirección. */
  private ventaId: number | null = null;
  private numeroEnUrl!: number;

  readonly pagoForm = this.fb.group({
    fecha: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    monto: this.fb.control<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    folio: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(50)] }),
    notas: this.fb.control(''),
  });

  /** Temporal: permite modificar los datos capturados de la venta (no sus lotes/precios). Se va a
   * quitar más adelante — avisan cuándo. */
  readonly editando = signal(false);
  readonly isSavingEdicion = signal(false);
  readonly errorEdicion = signal<string | null>(null);
  readonly edicionForm = this.fb.group({
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    mensualidad: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    plazoMeses: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    engancheLabel: this.fb.control(''),
    enganche: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    diaPago: this.fb.control<number | null>(null, {
      validators: [Validators.required, Validators.min(1), Validators.max(31)],
    }),
    notas: this.fb.control(''),
  });
  readonly edicionPrimeraMensualidadMesVenta = signal(false);

  // --- Eliminar venta: solo admin. Estilo "permiso peligroso" de Android: el botón de confirmar se
  // habilita hasta que termina una cuenta regresiva de 10 s, y además pide la contraseña (que valida
  // el servidor). ---
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');
  readonly mostrarEliminar = signal(false);
  readonly segundosEliminar = signal(0);
  readonly passwordEliminar = signal('');
  readonly liberarLotes = signal(true);
  readonly eliminando = signal(false);
  readonly errorEliminar = signal<string | null>(null);
  readonly cuentaTerminada = computed(() => this.segundosEliminar() <= 0);
  private temporizadorEliminar: ReturnType<typeof setInterval> | undefined;

  abrirEliminar(): void {
    this.passwordEliminar.set('');
    this.liberarLotes.set(true);
    this.errorEliminar.set(null);
    this.segundosEliminar.set(SEGUNDOS_ESPERA_ELIMINAR);
    this.mostrarEliminar.set(true);
    clearInterval(this.temporizadorEliminar);
    this.temporizadorEliminar = setInterval(() => {
      this.segundosEliminar.update((s) => Math.max(0, s - 1));
      if (this.segundosEliminar() <= 0) clearInterval(this.temporizadorEliminar);
    }, 1000);
  }

  cerrarEliminar(): void {
    if (this.eliminando()) return;
    clearInterval(this.temporizadorEliminar);
    this.mostrarEliminar.set(false);
  }

  async confirmarEliminar(): Promise<void> {
    if (!this.ventaId || !this.cuentaTerminada() || !this.passwordEliminar() || this.eliminando()) return;
    this.eliminando.set(true);
    this.errorEliminar.set(null);
    try {
      await this.ventasService.eliminar(this.ventaId, this.passwordEliminar(), this.liberarLotes());
      clearInterval(this.temporizadorEliminar);
      this.mostrarEliminar.set(false);
      this.toast.success('Venta eliminada.');
      await this.router.navigate(['/panel/ventas']);
    } catch (error) {
      this.errorEliminar.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo eliminar la venta. Intenta de nuevo.',
      );
    } finally {
      this.eliminando.set(false);
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.temporizadorEliminar);
  }

  ngOnInit(): void {
    this.numeroEnUrl = Number(this.route.snapshot.paramMap.get('numero'));
    this.cargar();
    this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
    this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
  }

  /** Si el número de la venta cambió (se editó su fecha o se registró otra con fecha anterior), la
   * dirección se actualiza en su lugar para que un refresh o un enlace copiado abra esta misma venta. */
  private sincronizarDireccion(numero: number): void {
    if (numero === this.numeroEnUrl) return;
    this.numeroEnUrl = numero;
    this.location.replaceState(`/panel/ventas/${numero}`);
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      // La primera vez se busca por el número de la dirección; después, por el id estable (el número
      // pudo moverse si alguien registró una venta con fecha anterior mientras esta pantalla estaba abierta).
      const venta =
        this.ventaId === null
          ? await this.ventasService.obtenerPorNumero(this.numeroEnUrl)
          : await this.ventasService.obtener(this.ventaId);
      this.ventaId = venta.id;
      const pagos = await this.ventasService.listarPagos(venta.id);
      this.venta.set(venta);
      this.pagos.set(pagos);
      this.sincronizarDireccion(venta.numero);
    } catch {
      this.errorMessage.set('No se pudo cargar la venta.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async registrarPago(): Promise<void> {
    if (this.pagoForm.invalid || this.isSavingPago()) {
      this.pagoForm.markAllAsTouched();
      return;
    }

    this.isSavingPago.set(true);
    this.pagoError.set(null);
    const v = this.pagoForm.getRawValue();

    try {
      await this.ventasService.registrarPago(this.ventaId!, {
        fecha: v.fecha,
        monto: v.monto!,
        folio: v.folio.trim(),
        notas: v.notas?.trim() || null,
      });
      // El saldo pendiente se recalcula en el servidor; volvemos a cargar todo para que quede
      // consistente en vez de intentar restar a mano aquí.
      await this.cargar();
      this.pagoForm.reset({ fecha: new Date().toISOString().slice(0, 10), monto: null, folio: '', notas: '' });
      this.toast.success('Abono registrado.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar el abono.';
      this.pagoError.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.isSavingPago.set(false);
    }
  }

  totalAportaciones(): number {
    return (this.venta()?.aportaciones ?? []).reduce((suma, a) => suma + a.monto, 0);
  }

  nombreMes(mes: number): string {
    return MESES_NOMBRE[mes - 1];
  }

  /** Enganche como % del precio de la venta (solo lectura). */
  pctEnganche(v: Venta): number | null {
    return porcentajeDe(v.enganche, v.precioVenta);
  }

  /** Fecha de la última mensualidad según plazo, día de pago y mes de la primera. */
  terminaEn(v: Venta): string | null {
    const f = ultimaMensualidad(v.fechaVenta, v.plazoMeses, v.diaPago, v.primeraMensualidadMesVenta);
    return f ? fechaLarga(f) : null;
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  // --- Modificar venta (temporal) ---
  readonly clienteEdicion = signal<ClienteLista | null>(null);
  readonly copropietariosEdicion = signal<(ClienteLista | null)[]>([]);

  abrirEdicion(): void {
    const v = this.venta();
    if (!v) return;
    this.clienteEdicion.set(
      v.clienteId === null ? null : { id: v.clienteId, nombreCompleto: v.cliente, telefono: null, correo: null, activo: true, datosIncompletos: false, compras: 0, desarrollos: [], saldoPendiente: 0 },
    );
    this.edicionForm.reset({
      asesor: asesorSeleccionDe(v.asesor),
      fechaVenta: v.fechaVenta,
      formaPago: v.formaPago,
      mensualidad: v.mensualidad,
      plazoMeses: v.plazoMeses,
      engancheLabel: v.engancheLabel ?? '',
      enganche: v.enganche,
      diaPago: v.diaPago,
      notas: v.notas ?? '',
    });
    this.copropietariosEdicion.set(
      v.copropietarios.map((c) => ({ id: c.id, nombreCompleto: c.nombreCompleto, telefono: null, correo: null, activo: true, datosIncompletos: false, compras: 0, desarrollos: [], saldoPendiente: 0 })),
    );
    this.edicionPrimeraMensualidadMesVenta.set(v.primeraMensualidadMesVenta);
    this.errorEdicion.set(null);
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    this.editando.set(false);
  }

  async guardarEdicion(): Promise<void> {
    if (hayCopropietariosRepetidos(this.copropietariosEdicion(), this.clienteEdicion()?.id ?? null)) {
      this.errorEdicion.set('Hay clientes repetidos entre el principal y los copropietarios.');
      return;
    }
    if (this.edicionForm.invalid || this.isSavingEdicion()) {
      this.edicionForm.markAllAsTouched();
      return;
    }

    this.isSavingEdicion.set(true);
    this.errorEdicion.set(null);
    const v = this.edicionForm.getRawValue();

    try {
      const actualizada = await this.ventasService.actualizar(this.ventaId!, {
        clienteId: this.clienteEdicion()?.id ?? null,
        copropietariosIds: idsCopropietarios(this.copropietariosEdicion()),
        ...parseAsesorSeleccion(v.asesor),
        fechaVenta: v.fechaVenta,
        formaPago: v.formaPago,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
        engancheLabel: v.engancheLabel?.trim() || null,
        enganche: v.enganche,
        diaPago: v.diaPago as number,
        primeraMensualidadMesVenta: this.edicionPrimeraMensualidadMesVenta(),
        notas: v.notas?.trim() || null,
      });
      this.venta.set(actualizada);
      this.sincronizarDireccion(actualizada.numero);
      this.editando.set(false);
      this.toast.success('Venta actualizada.');
    } catch (error) {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo actualizar la venta.';
      this.errorEdicion.set(mensaje);
      this.toast.error(mensaje);
    } finally {
      this.isSavingEdicion.set(false);
    }
  }
}
