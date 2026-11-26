import { ClienteLista } from '../../../../core/models/cliente.model';
import { CopropietariosEditorComponent, hayCopropietariosRepetidos, idsCopropietarios } from '../../../../shared/copropietarios-editor/copropietarios-editor.component';
import { ClienteSelectorComponent } from '../../../../shared/cliente-selector/cliente-selector.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { LucideCircleAlert, LucideMail, LucidePencil, LucideTrash2 } from '@lucide/angular';
import { Amortizacion, cuentaAtraso, fechaCorta } from '../../../../core/models/amortizacion.model';
import { ReestructurarVentaComponent } from '../../../../shared/reestructurar-venta/reestructurar-venta.component';
import { CambiarLotesVentaComponent } from '../../../../shared/cambiar-lotes-venta/cambiar-lotes-venta.component';
import { TablaAmortizacionComponent } from '../../../../shared/tabla-amortizacion/tabla-amortizacion.component';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MESES_NOMBRE } from '../../../../core/utils/aportaciones';
import { AuthService } from '../../../../core/services/auth.service';
import { ConfirmService } from '../../../../core/services/confirm.service';
import { ToastService } from '../../../../core/services/toast.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AsesoresExternosService } from '../../../../core/services/asesores-externos.service';
import { BienvenidaCliente, LiquidacionAviso, MODALIDAD_PAGO_LABELS, ModalidadPago, ocultaEfectivo, PagoVenta, ReciboHistorial, Venta } from '../../../../core/models/venta.model';
import { CorreosVentaComponent } from '../../../../shared/correos-venta/correos-venta.component';
import { ReciboFormComponent } from '../../../../shared/recibo-form/recibo-form.component';
import { ContratoVentaComponent } from '../../../../shared/contrato-venta/contrato-venta.component';
import { RecibosVentaComponent } from '../../../../shared/recibos-venta/recibos-venta.component';
import { UsuarioResumen } from '../../../../core/models/lead.model';
import { AsesorExterno } from '../../../../core/models/asesor-externo.model';
import { asesorSeleccionDe, parseAsesorSeleccion } from '../venta-form/venta-form.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';
import { MonedaInputDirective } from '../../../../shared/moneda-input/moneda-input.directive';
import { fechaLarga, porcentajeDe, ultimaMensualidad } from '../../../../core/utils/financiamiento';
import { NumeroVentaComponent } from '../../../../shared/numero-venta/numero-venta.component';
import { dinero } from '../../../../core/utils/formato-pantalla';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Promocion } from '../../../../core/models/promocion.model';
import { PromocionesService } from '../../../../core/services/promociones.service';
import { bonoDe, motivoNoAplica } from '../../../../core/utils/promocion';

const SEGUNDOS_ESPERA_ELIMINAR = 10;

@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [CorreosVentaComponent, MonedaInputDirective, WeLoaderComponent, ReciboFormComponent, RecibosVentaComponent, ContratoVentaComponent, TablaAmortizacionComponent, ReestructurarVentaComponent, CambiarLotesVentaComponent, ReactiveFormsModule, RouterLink, ClienteSelectorComponent, CopropietariosEditorComponent, NumeroVentaComponent, LucideCircleAlert, LucideMail, LucidePencil, LucideTrash2],
  templateUrl: './venta-detalle.component.html',
})
export class VentaDetalleComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly ventasService = inject(VentasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly promocionesService = inject(PromocionesService);

  readonly venta = signal<Venta | null>(null);
  readonly asesoresInternos = signal<UsuarioResumen[]>([]);
  readonly asesoresExternos = signal<AsesorExterno[]>([]);
  readonly pagos = signal<PagoVenta[]>([]);
  readonly historialRecibos = signal<ReciboHistorial[]>([]);
  /** Tabla de amortización con el estado de cuenta a hoy (null si no se pudo calcular: la venta se ve igual). */
  readonly amortizacion = signal<Amortizacion | null>(null);
  /** Se queda como la dejó la persona aunque la venta se recargue (p. ej. al registrar un abono). */
  readonly amortizacionAbierta = signal(true);
  /** Botón de bienvenida: solo admin y administración, y solo en ventas anteriores a los envíos automáticos
   * (null en las demás o si no se pudo consultar). */
  readonly bienvenida = signal<BienvenidaCliente | null>(null);
  readonly enviandoBienvenida = signal(false);
  /** Botón de aviso de liquidación a finanzas: mismas condiciones que el de bienvenida. */
  readonly liquidacion = signal<LiquidacionAviso | null>(null);
  readonly enviandoLiquidacion = signal(false);
  /** Cambia al recargar la venta o al mandar un correo a mano: el registro de correos se vuelve a pedir. */
  readonly marcaCorreos = computed(() => [this.venta(), this.bienvenida(), this.liquidacion()]);
  readonly cuentaAtraso = cuentaAtraso;
  readonly fechaCorta = fechaCorta;
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  /** id estable de la venta (para escribir); se conoce al cargarla por el número de la dirección. */
  private ventaId: number | null = null;
  private numeroEnUrl!: string;

  /** Temporal: permite modificar los datos capturados de la venta (no sus lotes/precios). Se va a
   * quitar más adelante — avisan cuándo. */
  readonly modalidadLabels = MODALIDAD_PAGO_LABELS;
  readonly cambiandoLotes = signal(false);
  /** Esta cuenta no ve ventas en efectivo: tampoco se le ofrece esa modalidad (ver VisibilidadVentas). */
  readonly sinEfectivo = computed(() => ocultaEfectivo(this.auth.currentUser()?.email));
  readonly editando = signal(false);
  readonly isSavingEdicion = signal(false);
  readonly errorEdicion = signal<string | null>(null);
  readonly edicionForm = this.fb.group({
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    modalidadPago: this.fb.control<ModalidadPago | ''>('', { nonNullable: true }),
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

  // --- Promoción al modificar (12.17.0) ---
  /** Las de bono vigentes en la fecha de la venta (las mismas que ofrece Nueva venta). */
  readonly promocionesEdicion = signal<Promocion[]>([]);
  /** 'igual' = la promoción no se toca; null = quitarla; un id = cambiar a esa. */
  readonly promocionEdicion = signal<number | null | 'igual'>('igual');
  /** Cambiar la promoción mueve precio, saldo y comisión: se pide la contraseña (igual que «Cambiar lotes»). */
  readonly passwordPromocion = signal('');
  readonly cambiaPromocion = computed(() => this.promocionEdicion() !== 'igual');
  readonly promocionElegida = computed(() => {
    const id = this.promocionEdicion();
    return typeof id === 'number' ? (this.promocionesEdicion().find((p) => p.id === id) ?? null) : null;
  });
  /** Por qué la promoción elegida no aplica a los lotes de la venta (mismo criterio que el servidor). */
  readonly errorPromocionEdicion = computed<string | null>(() => {
    const promo = this.promocionElegida();
    const v = this.venta();
    if (!promo || !v) return null;
    return motivoNoAplica(promo, v.lotes.map((l) => ({ superficie: l.lote.superficie, desarrollo: l.lote.desarrollo.nombre })));
  });
  /** Precio de la venta con la promoción elegida: se le devuelve el bono que tenía y se resta el nuevo. */
  readonly precioConPromocion = computed(() => {
    const v = this.venta();
    if (!v) return 0;
    const promo = this.promocionElegida();
    const nuevo = promo && !this.errorPromocionEdicion() ? bonoDe(promo, v.lotes.length) : 0;
    return Math.round((v.precioVenta + (v.promocionBono ?? 0) - nuevo) * 100) / 100;
  });
  /** Las promociones vigentes cambian con la fecha de venta. */
  private readonly promocionesPorFecha = this.edicionForm.controls.fechaVenta.valueChanges
    .pipe(takeUntilDestroyed())
    .subscribe((fecha) => {
      if (this.editando()) void this.cargarPromocionesEdicion(fecha);
    });
  /** Número de venta en la edición: arranca con el actual; si se cambia, el servidor recorre las consecutivas. */
  readonly numeroEdicion = signal<number | null>(null);
  /** "S-" / "N-" de la venta, para mostrar los números del campo de edición. */
  readonly prefijoNumero = computed(() => {
    const e = this.venta()?.etiqueta ?? '';
    return e.includes('-') ? e.slice(0, e.indexOf('-') + 1) : '';
  });
  /** Venta registrada desde We Forms: marcar la fecha de venta como definitiva al guardar (cambiarla también la confirma). */
  readonly confirmarFechaEdicion = signal(false);

  // --- Depósito pendiente de aplicar (venta que nació de un registro de venta con un lote apartado con dinero) ---
  readonly mostrarDeposito = signal(false);
  readonly depositoModo = signal<'SALDO' | 'MENSUALIDAD'>('SALDO');
  readonly depositoFolio = signal('');
  readonly aplicandoDeposito = signal(false);
  readonly errorDeposito = signal<string | null>(null);

  // --- Eliminar venta: admin y administración. Estilo "permiso peligroso" de Android: el botón de confirmar se
  // habilita hasta que termina una cuenta regresiva de 10 s, y además pide la contraseña (que valida
  // el servidor). ---
  readonly puedeEliminar = computed(() => {
    const rol = this.auth.currentUser()?.rol;
    return rol === 'ADMIN' || rol === 'LIDER_AREA';
  });
  readonly mostrarEliminar = signal(false);
  readonly segundosEliminar = signal(0);
  readonly passwordEliminar = signal('');
  readonly eliminando = signal(false);
  readonly errorEliminar = signal<string | null>(null);
  readonly cuentaTerminada = computed(() => this.segundosEliminar() <= 0);
  private temporizadorEliminar: ReturnType<typeof setInterval> | undefined;

  abrirEliminar(): void {
    this.passwordEliminar.set('');
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
      await this.ventasService.eliminar(this.ventaId, this.passwordEliminar());
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
    this.numeroEnUrl = this.route.snapshot.paramMap.get('numero') ?? '';
    this.cargar();
    this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
    this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
  }

  /** Si el número de la venta cambió (se editó su fecha o se registró otra con fecha anterior), la
   * dirección se actualiza en su lugar para que un refresh o un enlace copiado abra esta misma venta. */
  private sincronizarDireccion(numero: string): void {
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
      // En las ventas con envíos automáticos no hay botones para mandar esos correos a mano.
      const aMano = this.puedeEliminar() && !venta.correosAutomaticos;
      const [pagos, historial, amortizacion, bienvenida, liquidacion] = await Promise.all([
        this.ventasService.listarPagos(venta.id),
        this.ventasService.historialRecibos(venta.id),
        this.ventasService.amortizacion(venta.id).catch(() => null),
        aMano ? this.ventasService.bienvenida(venta.id).catch(() => null) : Promise.resolve(null),
        aMano ? this.ventasService.liquidacion(venta.id).catch(() => null) : Promise.resolve(null),
      ]);
      this.venta.set(venta);
      this.pagos.set(pagos);
      this.historialRecibos.set(historial);
      this.amortizacion.set(amortizacion);
      this.bienvenida.set(bienvenida);
      this.liquidacion.set(liquidacion);
      this.sincronizarDireccion(venta.etiqueta);
    } catch {
      this.errorMessage.set('No se pudo cargar la venta.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Pide confirmación (a qué correo va; avisa si ya se envió antes), manda la bienvenida y refresca el estado. */
  async enviarBienvenida(): Promise<void> {
    const b = this.bienvenida();
    const v = this.venta();
    if (!b || !v || !b.puedeEnviar || this.enviandoBienvenida()) return;
    const yaEnviada = b.ultimo
      ? ` Ya se envió antes el ${this.fechaHora(b.ultimo.enviadoEn)}${b.ultimo.usuario ? ` (por ${b.ultimo.usuario})` : ''}.`
      : '';
    const confirmado = await this.confirmService.confirm({
      titulo: b.ultimo ? 'Reenviar correo de bienvenida' : 'Enviar correo de bienvenida',
      mensaje: `Se enviará el correo de bienvenida de SAMAI Campestre a ${b.correoDestino}.${yaEnviada}`,
      textoConfirmar: b.ultimo ? 'Reenviar' : 'Enviar',
    });
    if (!confirmado) return;
    this.enviandoBienvenida.set(true);
    try {
      this.bienvenida.set(await this.ventasService.enviarBienvenida(v.id));
      this.toast.success(`Correo de bienvenida enviado a ${b.correoDestino}.`);
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo enviar el correo de bienvenida.',
      );
    } finally {
      this.enviandoBienvenida.set(false);
    }
  }

  /** Pide confirmación (a quién va cada correo; avisa si ya se enviaron antes), manda los correos de liquidación y refresca. */
  async enviarLiquidacion(): Promise<void> {
    const l = this.liquidacion();
    const v = this.venta();
    if (!l || !v || !l.puedeEnviar || this.enviandoLiquidacion()) return;
    const yaEnviado = l.ultimo
      ? ` Ya se envió antes el ${this.fechaHora(l.ultimo.enviadoEn)}${l.ultimo.usuario ? ` (por ${l.ultimo.usuario})` : ''}; finanzas lo recibirá otra vez.`
      : '';
    const confirmado = await this.confirmService.confirm({
      titulo: l.ultimo ? 'Volver a avisar a finanzas' : 'Avisar a finanzas',
      mensaje: `Se enviará a finanzas el aviso «¡Es hora de realizar una carta finiquito!». Al cliente no se le envía nada.${yaEnviado}`,
      textoConfirmar: l.ultimo ? 'Reenviar' : 'Enviar',
    });
    if (!confirmado) return;
    this.enviandoLiquidacion.set(true);
    try {
      this.liquidacion.set(await this.ventasService.enviarLiquidacion(v.id));
      this.toast.success('Aviso de liquidación enviado a finanzas.');
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo enviar el aviso de liquidación.',
      );
    } finally {
      this.enviandoLiquidacion.set(false);
    }
  }

  /** "17/11/2026 14:05" a partir de "2026-11-17T14:05:00" (sin pasar por Date: no se corre por la zona horaria). */
  fechaHora(iso: string): string {
    return `${fechaCorta(iso)} ${iso.slice(11, 16)}`;
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

  /** Fecha de la última mensualidad según plazo, día de pago y mes de la primera. Si hubo una pausa de mensualidades,
   * la del último renglón numerado de la tabla (ya recorrida). */
  terminaEn(v: Venta): string | null {
    const ultima = this.ultimoPagoTrasPausa();
    if (ultima) return fechaLarga(ultima.fecha);
    const f = ultimaMensualidad(v.fechaVenta, v.plazoMeses, v.diaPago, v.primeraMensualidadMesVenta);
    return f ? fechaLarga(f) : null;
  }

  /** Con una pausa de mensualidades vigente: la pausa más reciente (desde qué número y su día de pago) y la fecha del
   * último pago numerado del plan vigente. null sin pausas. */
  readonly ultimoPagoTrasPausa = computed(() => {
    const a = this.amortizacion();
    const pausa = a?.pausas?.at(-1);
    if (!a || !pausa) return null;
    const tramo = Math.max(0, ...a.detalleMensual.map((f) => f.tramo));
    const filas = a.detalleMensual.filter((f) => f.tramo === tramo && f.numero !== null && f.fecha);
    const ultima = filas.at(-1);
    if (!ultima?.fecha) return null;
    const [y, m, d] = ultima.fecha.split('-').map(Number);
    return { fecha: new Date(y, m - 1, d), numero: pausa.numero, dia: Number(pausa.fechaNueva.slice(8, 10)) };
  });

  money(valor: number): string {
    return dinero(valor);
  }

  /** "2026-09-12" → "12/09/2026", sin pasar por Date (que la desfasa un día por la zona horaria). */
  fecha(iso: string): string {
    return fechaCorta(iso);
  }

  // --- Modificar venta (temporal) ---
  readonly clienteEdicion = signal<ClienteLista | null>(null);
  readonly copropietariosEdicion = signal<(ClienteLista | null)[]>([]);

  abrirEdicion(): void {
    const v = this.venta();
    if (!v) return;
    this.clienteEdicion.set(
      v.clienteId === null ? null : { id: v.clienteId, numeros: [], nombreCompleto: v.cliente, telefono: null, correo: null, activo: true, datosIncompletos: false, compras: 0, desarrollos: [], saldoPendiente: 0, atrasoMonto: 0, pagosAtrasados: 0, mensualidadesAtrasadas: 0 },
    );
    this.edicionForm.reset({
      asesor: asesorSeleccionDe(v.asesor),
      fechaVenta: v.fechaVenta,
      formaPago: v.formaPago,
      modalidadPago: v.modalidadPago ?? '',
      mensualidad: v.mensualidad,
      plazoMeses: v.plazoMeses,
      engancheLabel: v.engancheLabel ?? '',
      enganche: v.enganche,
      diaPago: v.diaPago,
      notas: v.notas ?? '',
    });
    this.copropietariosEdicion.set(
      v.copropietarios.map((c) => ({ id: c.id, numeros: [], nombreCompleto: c.nombreCompleto, telefono: null, correo: null, activo: true, datosIncompletos: false, compras: 0, desarrollos: [], saldoPendiente: 0, atrasoMonto: 0, pagosAtrasados: 0, mensualidadesAtrasadas: 0 })),
    );
    this.edicionPrimeraMensualidadMesVenta.set(v.primeraMensualidadMesVenta);
    this.errorEdicion.set(null);
    this.numeroEdicion.set(this.venta()?.numero ?? null);
    this.confirmarFechaEdicion.set(false);
    this.promocionEdicion.set('igual');
    this.passwordPromocion.set('');
    void this.cargarPromocionesEdicion(v.fechaVenta);
    this.editando.set(true);
  }

  private async cargarPromocionesEdicion(fecha: string): Promise<void> {
    try {
      this.promocionesEdicion.set(fecha ? await this.promocionesService.paraVenta(fecha) : []);
    } catch {
      this.promocionesEdicion.set([]);
    }
    const id = this.promocionEdicion();
    if (typeof id === 'number' && !this.promocionesEdicion().some((p) => p.id === id)) {
      this.promocionEdicion.set('igual');
      this.recalcularMensualidadEdicion();
    }
  }

  /** Nombre y bono que le tocaría a esta venta con esa promoción. */
  etiquetaPromocionEdicion(p: Promocion): string {
    const bono = bonoDe(p, this.venta()?.lotes.length ?? 0);
    return bono > 0 ? `${p.nombre} · bono $${dinero(bono)}` : p.nombre;
  }

  onPromocionEdicion(valor: string): void {
    this.promocionEdicion.set(valor === 'igual' ? 'igual' : valor === '' ? null : Number(valor));
    this.recalcularMensualidadEdicion();
  }

  /** Al cambiar la promoción la mensualidad se recalcula con el precio nuevo (saldo ÷ meses sin aportación, como al
   * aplicar un depósito); el campo sigue editable. */
  private recalcularMensualidadEdicion(): void {
    const v = this.venta();
    const plazo = this.edicionForm.controls.plazoMeses.value;
    if (!v || !plazo) return;
    const precio = this.cambiaPromocion() ? this.precioConPromocion() : v.precioVenta;
    const enganche = this.edicionForm.controls.enganche.value ?? 0;
    const aportado = v.aportaciones.reduce((suma, a) => suma + a.monto, 0);
    const meses = plazo - v.aportaciones.length;
    const saldo = precio - enganche - aportado;
    if (meses > 0 && saldo > 0) {
      this.edicionForm.controls.mensualidad.setValue(Math.round((saldo / meses) * 100) / 100);
    }
  }

  abrirDeposito(): void {
    this.depositoModo.set('SALDO');
    this.depositoFolio.set('');
    this.errorDeposito.set(null);
    this.mostrarDeposito.set(true);
  }

  async aplicarDeposito(): Promise<void> {
    const v = this.venta();
    if (!v || this.aplicandoDeposito()) return;
    if (this.depositoModo() === 'SALDO' && !this.depositoFolio().trim()) {
      this.errorDeposito.set('Ingresa el folio del dinero recibido al apartar el lote.');
      return;
    }
    this.aplicandoDeposito.set(true);
    this.errorDeposito.set(null);
    try {
      const actualizada = await this.ventasService.aplicarDeposito(v.id, {
        aplicarA: this.depositoModo(),
        folio: this.depositoModo() === 'SALDO' ? this.depositoFolio().trim() : undefined,
      });
      this.venta.set(actualizada);
      this.mostrarDeposito.set(false);
      this.toast.success('Depósito aplicado.');
      const [pagos, historial] = await Promise.all([
        this.ventasService.listarPagos(v.id),
        this.ventasService.historialRecibos(v.id),
      ]);
      this.pagos.set(pagos);
      this.historialRecibos.set(historial);
    } catch (error) {
      this.errorDeposito.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo aplicar el depósito.',
      );
    } finally {
      this.aplicandoDeposito.set(false);
    }
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
    const promocion = this.promocionEdicion();
    if (this.cambiaPromocion()) {
      const motivo = this.errorPromocionEdicion();
      if (motivo) {
        this.errorEdicion.set(motivo);
        return;
      }
      if (!this.passwordPromocion()) {
        this.errorEdicion.set('Escribe tu contraseña para cambiar la promoción.');
        return;
      }
    }

    this.isSavingEdicion.set(true);
    this.errorEdicion.set(null);
    const v = this.edicionForm.getRawValue();

    try {
      const actualizada = await this.ventasService.actualizar(this.ventaId!, {
        clienteId: this.clienteEdicion()?.id ?? null,
        numero: this.numeroEdicion(),
        confirmarFecha: this.confirmarFechaEdicion() || undefined,
        copropietariosIds: idsCopropietarios(this.copropietariosEdicion()),
        ...parseAsesorSeleccion(v.asesor),
        fechaVenta: v.fechaVenta,
        formaPago: v.formaPago,
        modalidadPago: v.modalidadPago || null,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
        engancheLabel: v.engancheLabel?.trim() || null,
        enganche: v.enganche,
        diaPago: v.diaPago as number,
        primeraMensualidadMesVenta: this.edicionPrimeraMensualidadMesVenta(),
        notas: v.notas?.trim() || null,
        ...(this.cambiaPromocion()
          ? { cambiarPromocion: true, promocionId: typeof promocion === 'number' ? promocion : null, password: this.passwordPromocion() }
          : {}),
      });
      this.venta.set(actualizada);
      this.sincronizarDireccion(actualizada.etiqueta);
      this.editando.set(false);
      this.toast.success('Venta actualizada.');
      // Con otra promoción cambiaron precio, saldo y comisión: se recarga todo (tabla de amortización incluida).
      if (this.cambiaPromocion()) void this.cargar();
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
