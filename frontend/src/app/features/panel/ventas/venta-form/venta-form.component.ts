import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { LucideCheck, LucideX } from '@lucide/angular';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClienteLista } from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { CopropietariosEditorComponent, hayCopropietariosRepetidos, idsCopropietarios } from '../../../../shared/copropietarios-editor/copropietarios-editor.component';
import { ClienteSelectorComponent } from '../../../../shared/cliente-selector/cliente-selector.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Desarrollo, UsuarioResumen } from '../../../../core/models/lead.model';
import { ESTADO_LOTE_LABELS, Lote } from '../../../../core/models/lote.model';
import { AsesorExterno } from '../../../../core/models/asesor-externo.model';
import { MedioFirma, ModalidadPago } from '../../../../core/models/venta.model';
import { AsesoresExternosService } from '../../../../core/services/asesores-externos.service';
import { LeadsService } from '../../../../core/services/leads.service';
import { LotesService } from '../../../../core/services/lotes.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { VentasService } from '../../../../core/services/ventas.service';
import { MonedaInputDirective } from '../../../../shared/moneda-input/moneda-input.directive';
import { AportacionesSelectorComponent } from '../../../../shared/aportaciones-selector/aportaciones-selector.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';
import { fechaLarga, porcentajeDe, ultimaMensualidad } from '../../../../core/utils/financiamiento';
import {
  Aportacion,
  MESES_ABREVIADO,
  MESES_NOMBRE,
  aportacionesValidas,
  mensualidadConAportaciones,
  totalAportaciones,
} from '../../../../core/utils/aportaciones';
import {
  SIN_FORMA_PAGO,
  SeleccionPago,
  TipoPago,
  alternarFormaPago,
  anualidadesComoAportaciones,
  estaBloqueado,
  estaMarcado,
  etiquetaEntrada,
  etiquetaFormaPago,
  hayFormaPago,
  llevaEntrada,
  maximoAnualidades,
  unidadPlazo,
} from '../../../../core/utils/formas-pago';
import { NumeroVentaComponent } from '../../../../shared/numero-venta/numero-venta.component';

/** Codifica la selección del <select> de asesor como un solo string ("U-5" / "E-3") porque un
 * formControl reactivo solo puede llevar un valor, y el asesor es uno de dos tipos distintos (ver
 * parseAsesorSeleccion). */
export function parseAsesorSeleccion(valor: string): { usuarioAsesorId: number | null; asesorExternoId: number | null } {
  if (valor.startsWith('U-')) return { usuarioAsesorId: Number(valor.slice(2)), asesorExternoId: null };
  if (valor.startsWith('E-')) return { usuarioAsesorId: null, asesorExternoId: Number(valor.slice(2)) };
  return { usuarioAsesorId: null, asesorExternoId: null };
}

/** Inverso de parseAsesorSeleccion: arma el valor del <select> a partir del asesor ya guardado en
 * una venta, para preseleccionarlo al editar (ver VentaDetalleComponent.abrirEdicion). */
export function asesorSeleccionDe(asesor: { id: number; externo: boolean }): string {
  return `${asesor.externo ? 'E' : 'U'}-${asesor.id}`;
}

/** Cómo se aplica a la venta el dinero que ya se había recibido al apartar un lote (ver
 * Lote.montoApartado): 'mensualidad' lo suma al enganche antes de calcular la mensualidad (baja el
 * pago mensual, mismo plazo); 'saldo' la deja igual y registra el monto como abono inicial en
 * cuanto se crea la venta (el saldo pendiente baja de entrada, el pago mensual no cambia). */
type AplicarDepositoA = 'mensualidad' | 'saldo';

/** Suficiente para traer todos los lotes de un desarrollo de un jalón: son cientos, no miles. */
const TAMANO_LOTES_DESARROLLO = 1000;

interface LoteAgregado {
  lote: Lote;
  precio: number;
}

/** Mismas formas de pago que el Cotizador, combinables igual (ver core/utils/formas-pago). */
const TIPO_PAGO_OPCIONES: { value: TipoPago; label: string; sublabel: string }[] = [
  { value: 'msi', label: 'Sin enganche', sublabel: 'Meses sin intereses' },
  { value: 'downpayment', label: 'Con enganche', sublabel: 'Monto libre' },
  { value: 'initial', label: 'Pago inicial', sublabel: 'Baja la mensualidad' },
  { value: 'annualities', label: 'Con anualidades', sublabel: 'Una por año, mismo mes' },
  { value: 'aportaciones', label: 'Con aportaciones', sublabel: 'Varias, cada una en su mes' },
  { value: 'cash', label: 'Contado', sublabel: 'Pago único' },
];

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [WeLoaderComponent, ReactiveFormsModule, FormsModule, RouterLink, LucideCheck, LucideX, MonedaInputDirective, AportacionesSelectorComponent, ClienteSelectorComponent, CopropietariosEditorComponent, NumeroVentaComponent],
  templateUrl: './venta-form.component.html',
})
export class VentaFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly ventasService = inject(VentasService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly asesoresExternosService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly clientesService = inject(ClientesService);

  /** El cliente elegido (o registrado) para esta venta; obligatorio. */
  readonly clienteElegido = signal<ClienteLista | null>(null);
  readonly clienteFaltante = signal(false);
  /** Número de venta escrito a mano; null = automático (el siguiente, mayor + 1). */
  readonly numeroVenta = signal<number | null>(null);
  /** Copropietarios elegidos (máximo 4 además del principal). */
  readonly copropietarios = signal<(ClienteLista | null)[]>([]);

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly desarrollos = signal<Desarrollo[]>([]);
  /** Solo se puede elegir entre asesores ya registrados — internos (usuarios activos) o externos
   * (ver AsesorExterno) — nunca texto libre (ver form.controls.asesor). */
  readonly asesoresInternos = signal<UsuarioResumen[]>([]);
  readonly asesoresExternos = signal<AsesorExterno[]>([]);
  readonly lotesDelDesarrollo = signal<Lote[]>([]);
  readonly isCargandoLotes = signal(false);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Lotes que ya se agregaron a esta venta (un cliente puede comprar varios en la misma
   * operación, con una sola mensualidad/plazo/saldo combinado). */
  readonly lotesAgregados = signal<LoteAgregado[]>([]);
  readonly precioTotal = computed(() => this.lotesAgregados().reduce((sum, l) => sum + l.precio, 0));

  /** Suma de lo que ya se había apartado con dinero en los lotes agregados (ver
   * Lote.montoApartado); 0 si ninguno tenía. */
  readonly depositoTotal = computed(() =>
    this.lotesAgregados().reduce((sum, l) => sum + (l.lote.montoApartado ?? 0), 0),
  );
  /** Solo aplica cuando hay depósito Y la venta tiene mensualidad (una venta de Contado no tiene
   * pago mensual que reducir: ahí el depósito siempre se registra como abono). null mientras no se
   * ha elegido — se pide explícito, no hay default silencioso para una decisión financiera. */
  readonly aplicarDepositoA = signal<AplicarDepositoA | null>(null);
  /** Comisión del asesor (5% por defecto) y, si la venta tiene copropietario (un segundo asesor), la suya;
   * el máximo de cada una es 10% del valor de la venta. */
  readonly maxComision = 10;
  readonly comisionPct = signal<number | null>(5);
  readonly coasesorActivo = signal(false);
  readonly coasesorSel = signal('');
  readonly coasesorPct = signal<number | null>(5);
  readonly comisionMonto = computed(() => (this.precioTotal() * (this.comisionPct() ?? 0)) / 100);
  readonly coasesorMonto = computed(() => (this.precioTotal() * (this.coasesorPct() ?? 0)) / 100);
  readonly comisionPctInvalida = computed(() => pctInvalido(this.comisionPct(), this.maxComision));
  readonly coasesorPctInvalida = computed(() => pctInvalido(this.coasesorPct(), this.maxComision));
  readonly coasesorIgualAlAsesor = computed(
    () => this.coasesorActivo() && !!this.coasesorSel() && this.coasesorSel() === this.asesorSeleccionado(),
  );

  alternarCoasesor(valor: boolean): void {
    this.coasesorActivo.set(valor);
    if (!valor) this.coasesorSel.set('');
  }

  /** Contrato firmado (y desde cuándo): sin él, la comisión de la venta no se puede entregar. */
  readonly hoyIso = new Date().toISOString().slice(0, 10);
  readonly contratoFirmado = signal(false);
  readonly contratoFecha = signal('');
  readonly contratoMedio = signal<MedioFirma | ''>('');
  readonly contratoDriveUrl = signal('');

  alternarContrato(valor: boolean): void {
    this.contratoFirmado.set(valor);
    if (valor && !this.contratoFecha()) this.contratoFecha.set(this.hoyIso);
  }

  /** Folio del recibo del depósito de apartado; se pide cuando ese dinero se registra como abono. */
  readonly folioDeposito = signal('');
  readonly registraDepositoComoAbono = computed(() => this.depositoTotal() > 0 && this.aplicarDepositoA() !== 'mensualidad');
  readonly requiereElegirDeposito = computed(() => this.depositoTotal() > 0 && this.mostrarPlazo());

  readonly tipoPagoOpciones = TIPO_PAGO_OPCIONES;
  /** Forma de pago: una base y, opcional, anualidades o aportaciones. Empieza vacía para que se elija. */
  readonly formaPagoSel = signal<SeleccionPago>(SIN_FORMA_PAGO);
  readonly mesesNombre = MESES_NOMBRE;

  /** Aportaciones del esquema "Con aportaciones" (varias por año, cada una con su mes y monto). */
  readonly aportaciones = signal<Aportacion[]>([]);
  readonly esAportaciones = computed(() => this.formaPagoSel().extra === 'aportaciones');
  readonly esAnualidades = computed(() => this.formaPagoSel().extra === 'annualities');
  readonly esContado = computed(() => this.formaPagoSel().base === 'cash');
  readonly totalAportaciones = computed(() => totalAportaciones(this.aportaciones()));
  /** Las aportaciones corren desde el mes de la venta durante el plazo (ver aniosElegibles). */
  readonly fechaVentaIso = signal(new Date().toISOString().slice(0, 10));
  readonly plazoActual = signal<number | null>(null);
  readonly inicioAportaciones = computed(() => {
    const [anio, mes] = this.fechaVentaIso().split('-').map(Number);
    return new Date(anio, (mes || 1) - 1, 1);
  });
  readonly montoEnganche = signal<number | null>(null);
  readonly diaPagoValor = signal<number | null>(new Date().getDate());

  /** Enganche como % del precio total (se deduce del monto; también se puede capturar al revés). */
  readonly enganchePct = computed(() => porcentajeDe(this.montoEnganche(), this.precioTotal()));

  /** Lo que se ve de un vistazo al armar el financiamiento: saldo y cuándo termina. */
  readonly resumenFinanciamiento = computed(() => {
    const precio = this.precioTotal();
    if (precio <= 0 || !hayFormaPago(this.formaPagoSel()) || this.esContado()) return null;
    const enganche = this.mostrarEnganche() ? (this.montoEnganche() ?? 0) : 0;
    const deposito = this.depositoTotal();
    const saldo = Math.max(0, Math.round((precio - enganche - deposito) * 100) / 100);
    const ultima = ultimaMensualidad(
      this.fechaVentaIso(),
      this.plazoActual(),
      this.diaPagoValor(),
      this.primeraMensualidadMesVenta(),
    );
    return { saldo, enganche, deposito, termina: ultima ? fechaLarga(ultima) : null };
  });

  /** Sin enganche/Con enganche usan "MSI" (meses sin intereses); pago inicial/anualidades usan
   * "meses" a secas — mismo texto que ya arma CotizadorComponent.paymentMethodLabel. */
  readonly unidadPlazo = computed(() => unidadPlazo(this.formaPagoSel()));
  readonly engancheLabelActual = computed(() => etiquetaEntrada(this.formaPagoSel()));
  readonly mostrarEnganche = computed(() => llevaEntrada(this.formaPagoSel()));
  readonly mostrarPlazo = computed(() => hayFormaPago(this.formaPagoSel()) && !this.esContado());

  // --- Anualidades: monto y mes de cada una; se guardan como aportaciones programadas (una por año) para
  // que quepan junto al enganche en la venta y la mensualidad salga bien. ---
  readonly montoAnualidad = signal<number | null>(null);
  readonly mesAnualidad = signal(12);
  /** null = el máximo que permite el plazo (como el Cotizador). */
  readonly numeroAnualidades = signal<number | null>(null);
  readonly maximoAnualidades = computed(() => maximoAnualidades(this.plazoActual() ?? 0));
  readonly anualidadesProgramadas = computed<Aportacion[]>(() => {
    const monto = this.montoAnualidad();
    const plazo = this.plazoActual() ?? 0;
    if (!this.esAnualidades() || !monto || monto <= 0 || plazo <= 0) return [];
    const cantidad = Math.min(this.numeroAnualidades() ?? this.maximoAnualidades(), this.maximoAnualidades());
    return anualidadesComoAportaciones(this.inicioAportaciones(), plazo, this.mesAnualidad(), monto, cantidad);
  });
  /** "Dic 2026 · Dic 2027": dónde quedan las anualidades, para revisarlas antes de guardar. */
  readonly anualidadesTexto = computed(() =>
    this.anualidadesProgramadas().map((a) => `${MESES_ABREVIADO[a.mes - 1]} ${a.anio}`).join(' · '),
  );

  /** Lo que se guarda como aportaciones de la venta: las elegidas mes por mes o las anualidades. */
  private aportacionesDeLaVenta(): Aportacion[] {
    if (this.esAportaciones()) return this.aportaciones();
    if (this.esAnualidades()) return this.anualidadesProgramadas();
    return [];
  }

  tipoPagoMarcado(tipo: TipoPago): boolean {
    return estaMarcado(this.formaPagoSel(), tipo);
  }

  tipoPagoBloqueado(tipo: TipoPago): boolean {
    return estaBloqueado(this.formaPagoSel(), tipo);
  }

  /** true: el mes de la venta cuenta como primera mensualidad; false: la primera cae el mes siguiente. */
  readonly primeraMensualidadMesVenta = signal(false);
  private diaPagoEditado = false;

  readonly form = this.fb.group({
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    // Opcional: efectivo o transferencia ('' = sin definir).
    modalidadPago: this.fb.control<ModalidadPago | ''>('', { nonNullable: true }),
    fechaVenta: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    // Opcionales: una venta de contado no los necesita.
    mensualidad: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    plazoMeses: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    // Día en que paga la mensualidad: arranca en el día de la fecha de venta y la sigue mientras no
    // se edite a mano (ver diaPagoEditado).
    diaPago: this.fb.control<number | null>(new Date().getDate(), {
      validators: [Validators.required, Validators.min(1), Validators.max(31)],
    }),
    notas: this.fb.control(''),
  });

  /** El asesor elegido arriba, como señal (para validar que el copropietario sea otra persona). */
  readonly asesorSeleccionado = toSignal(this.form.controls.asesor.valueChanges, { initialValue: '' });

  /** Mini-formulario para agregar un lote a la lista (separado del form principal: no se envía
   * directo, cada "Agregar" lo empuja a lotesAgregados). */
  readonly loteParaAgregarId = signal<number | null>(null);
  readonly precioParaAgregar = signal<number | null>(null);

  /** Lotes del desarrollo elegido que se pueden poner en la venta: los que no están ya en otra (el servidor tampoco lo
   * deja: un lote solo está en una venta) y que todavía no se agregaron a esta. */
  readonly lotesDisponiblesParaAgregar = computed<Lote[]>(() => {
    const agregadosIds = new Set(this.lotesAgregados().map((l) => l.lote.id));
    return this.lotesDelDesarrollo().filter((l) => l.ventaNumero == null && !agregadosIds.has(l.id));
  });

  constructor() {
    // Desde la ficha del cliente ("Nueva venta") llega ?clienteId=
    const clienteId = Number(this.route.snapshot.queryParamMap.get('clienteId'));
    if (clienteId) {
      this.clientesService.obtener(clienteId).then((c) =>
        this.clienteElegido.set({
          id: c.id, numeros: c.numeros ?? [], nombreCompleto: c.nombreCompleto, telefono: c.telefono, correo: c.correo, activo: c.activo,
          datosIncompletos: c.datosIncompletos, compras: c.compras,
          desarrollos: [...new Set(c.ventas.flatMap((v) => v.desarrollos))], saldoPendiente: c.saldoPendiente,
        }),
      ).catch(() => undefined);
    }
    // Desde Lotes o el Plano ("Registrar la venta" de un lote) llegan ?desarrolloId=&loteId=: ese lote ya agregado.
    const desarrolloDelLote = Number(this.route.snapshot.queryParamMap.get('desarrolloId'));
    const loteId = Number(this.route.snapshot.queryParamMap.get('loteId'));
    if (desarrolloDelLote && loteId) void this.precargarLote(desarrolloDelLote, loteId);
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
    this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
    this.actualizarFormaPago();
    // Cualquier cambio en plazo regenera el texto de forma de pago Y recalcula la mensualidad
    // (mismo cálculo que ya usa el Cotizador: saldo a financiar ÷ meses); ambos siguen editables
    // a mano por si el trato real fue distinto.
    this.form.controls.plazoMeses.valueChanges.subscribe((plazo) => {
      this.plazoActual.set(plazo);
      this.actualizarFormaPago();
      this.actualizarMensualidad();
    });
    // Cambiar la fecha de venta mueve el calendario de las aportaciones (el selector descarta las
    // que queden fuera y avisa por onAportacionesChange).
    this.form.controls.fechaVenta.valueChanges.subscribe((fecha) => {
      this.fechaVentaIso.set(fecha);
      if (!this.diaPagoEditado && fecha) this.form.controls.diaPago.setValue(Number(fecha.slice(8, 10)));
      this.actualizarMensualidad();
    });
    this.form.controls.diaPago.valueChanges.subscribe((dia) => {
      this.diaPagoValor.set(dia);
      // Solo cuenta como edición a mano si ya no coincide con el día de la fecha de venta.
      this.diaPagoEditado = this.form.controls.diaPago.value !== Number(this.fechaVentaIso().slice(8, 10));
    });
  }

  alternarPrimeraMensualidad(): void {
    this.primeraMensualidadMesVenta.update((v) => !v);
  }

  /** Marca o desmarca una forma de pago (lo que no se combina está bloqueado, ver formas-pago) y limpia lo
   * que ya no aplica: el enganche sin entrada, plazo y mensualidad en contado, las aportaciones o anualidades
   * que se desmarcaron. */
  alternarTipoPago(tipo: TipoPago): void {
    if (this.tipoPagoBloqueado(tipo)) return;
    this.formaPagoSel.set(alternarFormaPago(this.formaPagoSel(), tipo));
    if (!this.mostrarEnganche()) {
      this.montoEnganche.set(null);
    }
    if (this.esContado()) {
      this.form.controls.mensualidad.setValue(null);
      this.form.controls.plazoMeses.setValue(null);
      this.aplicarDepositoA.set(null);
    }
    if (!this.esAportaciones()) {
      this.aportaciones.set([]);
    }
    if (!this.esAnualidades()) {
      this.montoAnualidad.set(null);
      this.numeroAnualidades.set(null);
    }
    this.actualizarFormaPago();
    this.actualizarMensualidad();
  }

  onMontoAnualidadChange(valor: number | null): void {
    this.montoAnualidad.set(valor);
    this.actualizarMensualidad();
  }

  onMesAnualidadChange(mes: number): void {
    this.mesAnualidad.set(Number(mes));
    this.actualizarMensualidad();
  }

  onNumeroAnualidadesChange(valor: number | null): void {
    this.numeroAnualidades.set(valor === null ? null : Math.max(0, Math.trunc(valor)));
    this.actualizarMensualidad();
  }

  /** El enganche también se puede capturar como % del precio; el monto se calcula solo. */
  onEnganchePctChange(pct: number | null): void {
    const precio = this.precioTotal();
    if (pct === null || precio <= 0) {
      this.onMontoEngancheChange(null);
      return;
    }
    this.onMontoEngancheChange(Math.round(((precio * pct) / 100) * 100) / 100);
  }

  /** Se llama cada vez que cambia el monto de enganche/pago inicial/aportación a mano. */
  onMontoEngancheChange(valor: number | null): void {
    this.montoEnganche.set(valor);
    this.actualizarMensualidad();
  }

  private actualizarFormaPago(): void {
    this.form.controls.formaPago.setValue(etiquetaFormaPago(this.formaPagoSel(), this.form.controls.plazoMeses.value));
  }

  /** Mensualidad = saldo a financiar (precio total − enganche) ÷ meses, igual que
   * CotizadorComponent.monthlyPayment (0% interés — "meses sin intereses"). Se recalcula cada vez
   * que cambia el precio total, el plazo, el enganche o el tipo de pago; el campo sigue editable
   * por si el trato real llevó un ajuste manual. */
  private actualizarMensualidad(): void {
    const plazo = this.form.controls.plazoMeses.value;
    if (!hayFormaPago(this.formaPagoSel()) || this.esContado() || !plazo || plazo <= 0) {
      return;
    }

    const saldoAFinanciar = this.saldoParaMensualidades();

    // Con aportaciones o anualidades: en un mes con una se paga solo esa, así que la mensualidad
    // regular es lo que queda (ya sin enganche) entre los meses que no la llevan.
    if (this.formaPagoSel().extra) {
      const regular = mensualidadConAportaciones(saldoAFinanciar, this.aportacionesDeLaVenta(), plazo);
      this.form.controls.mensualidad.setValue(regular > 0 ? Math.round(regular * 100) / 100 : null);
      return;
    }

    if (saldoAFinanciar <= 0) {
      this.form.controls.mensualidad.setValue(null);
      return;
    }

    this.form.controls.mensualidad.setValue(Math.round((saldoAFinanciar / plazo) * 100) / 100);
  }

  /** Precio menos el enganche o pago inicial y menos lo ya apartado si se eligió que baje la mensualidad. */
  private saldoParaMensualidades(): number {
    const enganche = this.mostrarEnganche() ? (this.montoEnganche() ?? 0) : 0;
    const depositoParaMensualidad = this.aplicarDepositoA() === 'mensualidad' ? this.depositoTotal() : 0;
    return this.precioTotal() - enganche - depositoParaMensualidad;
  }

  onAportacionesChange(lista: Aportacion[]): void {
    this.aportaciones.set(lista);
    this.actualizarMensualidad();
  }

  /** Por qué las aportaciones no se pueden guardar todavía (null si están bien o si no aplican). */
  private errorAportaciones(): string | null {
    if (this.esAnualidades()) return this.errorAnualidades();
    if (!this.esAportaciones()) return null;
    const plazo = this.form.controls.plazoMeses.value;
    if (!plazo) return 'Indica el plazo para registrar aportaciones.';
    const lista = this.aportaciones();
    if (lista.length === 0) return 'Elige al menos un mes para las aportaciones.';
    if (lista.some((a) => a.monto === null || a.monto <= 0)) return 'Ingresa el monto de todas las aportaciones elegidas.';
    if (!aportacionesValidas(this.saldoParaMensualidades(), lista, plazo)) {
      return 'Las aportaciones suman todo el saldo: deja saldo para las mensualidades regulares.';
    }
    return null;
  }

  private errorAnualidades(): string | null {
    const plazo = this.form.controls.plazoMeses.value;
    if (!plazo) return 'Indica el plazo para registrar anualidades.';
    if (this.maximoAnualidades() < 1) return 'Con anualidades el plazo debe ser de al menos 24 meses.';
    if (!this.montoAnualidad() || this.montoAnualidad()! <= 0) return 'Ingresa el monto de cada anualidad.';
    const lista = this.anualidadesProgramadas();
    if (lista.length === 0) return 'Indica cuántas anualidades lleva la venta.';
    if (!aportacionesValidas(this.saldoParaMensualidades(), lista, plazo)) {
      return 'Las anualidades suman todo el saldo: deja saldo para las mensualidades regulares.';
    }
    return null;
  }

  /** Se llama al elegir cómo aplicar el dinero ya recibido al apartar (ver depositoTotal). */
  onAplicarDepositoChange(valor: AplicarDepositoA): void {
    this.aplicarDepositoA.set(valor);
    this.actualizarMensualidad();
  }

  async onDesarrolloChange(valor: string): Promise<void> {
    const desarrolloId = valor === '' ? null : Number(valor);
    this.form.controls.desarrolloId.setValue(desarrolloId);
    this.loteParaAgregarId.set(null);
    this.precioParaAgregar.set(null);
    this.lotesDelDesarrollo.set([]);
    if (desarrolloId === null) return;

    this.isCargandoLotes.set(true);
    try {
      const resultado = await this.lotesService.buscarPaginado({
        desarrolloId,
        pagina: 0,
        tamano: TAMANO_LOTES_DESARROLLO,
      });
      this.lotesDelDesarrollo.set(resultado.contenido);
    } catch {
      this.errorMessage.set('No se pudieron cargar los lotes de este desarrollo.');
    } finally {
      this.isCargandoLotes.set(false);
    }
  }

  /** Autocompleta el precio con el mismo estimado que ya se ve en /panel/lotes (precio por m² del
   * desarrollo × superficie del lote); queda editable por si la venta se cerró en otro monto. */
  onLoteParaAgregarChange(valor: string): void {
    const loteId = valor === '' ? null : Number(valor);
    this.loteParaAgregarId.set(loteId);
    const lote = loteId == null ? null : this.lotesDelDesarrollo().find((l) => l.id === loteId);
    this.precioParaAgregar.set(lote ? Math.round(lote.precio) : null);
  }

  agregarLote(): void {
    const loteId = this.loteParaAgregarId();
    const precio = this.precioParaAgregar();
    if (loteId == null || precio == null || precio <= 0) return;

    const lote = this.lotesDelDesarrollo().find((l) => l.id === loteId);
    if (!lote) return;

    this.lotesAgregados.update((actuales) => [...actuales, { lote, precio }]);
    this.loteParaAgregarId.set(null);
    this.precioParaAgregar.set(null);
    // Cambió la base del depósito (si el lote agregado tenía uno): que se vuelva a elegir en vez
    // de arrastrar una decisión tomada sobre un monto distinto.
    this.aplicarDepositoA.set(null);
    this.actualizarMensualidad();
  }

  /** Deja elegidos el desarrollo y el lote, con su precio estimado (se puede quitar y volver a agregar con otro). */
  private async precargarLote(desarrolloId: number, loteId: number): Promise<void> {
    await this.onDesarrolloChange(String(desarrolloId));
    const enOtraVenta = this.lotesDelDesarrollo().find((l) => l.id === loteId && l.ventaNumero != null);
    if (enOtraVenta) {
      this.errorMessage.set(`Ese lote ya está en la venta #${enOtraVenta.ventaNumero}.`);
      return;
    }
    if (!this.lotesDisponiblesParaAgregar().some((l) => l.id === loteId)) return;
    this.onLoteParaAgregarChange(String(loteId));
    this.agregarLote();
  }

  quitarLote(loteId: number): void {
    this.lotesAgregados.update((actuales) => actuales.filter((l) => l.lote.id !== loteId));
    this.aplicarDepositoA.set(null);
    this.actualizarMensualidad();
  }

  async onSubmit(): Promise<void> {
    this.clienteFaltante.set(this.clienteElegido() === null);
    const faltaTipoPago = !hayFormaPago(this.formaPagoSel());
    const faltaMontoEnganche = this.mostrarEnganche() && !this.montoEnganche();
    const faltaElegirDeposito = this.requiereElegirDeposito() && !this.aplicarDepositoA();
    const faltaCoasesor = this.coasesorActivo() && (!this.coasesorSel() || this.coasesorIgualAlAsesor());
    const comisionInvalida =
      this.comisionPctInvalida() || (this.coasesorActivo() && this.coasesorPctInvalida());
    const faltaFechaContrato = this.contratoFirmado() && (!this.contratoFecha() || !this.contratoMedio());
    const faltaFolioDeposito = !faltaElegirDeposito && this.registraDepositoComoAbono() && !this.folioDeposito().trim();
    const errorAportaciones = this.errorAportaciones();
    if (
      this.clienteElegido() === null ||
      hayCopropietariosRepetidos(this.copropietarios(), this.clienteElegido()?.id ?? null) ||
      this.form.invalid ||
      this.lotesAgregados().length === 0 ||
      faltaTipoPago ||
      faltaMontoEnganche ||
      faltaElegirDeposito ||
      faltaFolioDeposito ||
      faltaFechaContrato ||
      faltaCoasesor ||
      comisionInvalida ||
      errorAportaciones !== null ||
      this.isLoading()
    ) {
      this.form.markAllAsTouched();
      if (this.lotesAgregados().length === 0) {
        this.errorMessage.set('Agrega al menos un lote a la venta.');
      } else if (faltaTipoPago) {
        this.errorMessage.set('Elige el tipo de pago.');
      } else if (faltaMontoEnganche) {
        this.errorMessage.set(`Ingresa el monto de ${this.engancheLabelActual()!.toLowerCase()}.`);
      } else if (faltaElegirDeposito) {
        this.errorMessage.set('Indica si el dinero ya apartado baja la mensualidad o el saldo.');
      } else if (comisionInvalida) {
        this.errorMessage.set(`La comisión debe estar entre 0% y ${this.maxComision}%.`);
      } else if (faltaCoasesor) {
        this.errorMessage.set('Elige al copropietario (distinto al asesor de la venta) o quita esa opción.');
      } else if (faltaFechaContrato) {
        this.errorMessage.set('Indica la fecha de firma y si el contrato se firmó en físico o en DocuSign.');
      } else if (faltaFolioDeposito) {
        this.errorMessage.set('Ingresa el folio del dinero recibido al apartar el lote.');
      } else if (errorAportaciones !== null) {
        this.errorMessage.set(errorAportaciones);
      }
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();
    // Contado no tiene mensualidad que reducir: si había depósito, siempre se registra como abono.
    const registrarDepositoComoAbono = this.registraDepositoComoAbono();

    try {
      const venta = await this.ventasService.crear({
        lotes: this.lotesAgregados().map((l) => ({ loteId: l.lote.id, precio: l.precio })),
        clienteId: this.clienteElegido()!.id,
        numero: this.numeroVenta(),
        copropietariosIds: idsCopropietarios(this.copropietarios()),
        ...parseAsesorSeleccion(v.asesor),
        formaPago: v.formaPago,
        modalidadPago: v.modalidadPago || null,
        fechaVenta: v.fechaVenta,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
        engancheLabel: this.engancheLabelActual(),
        enganche: this.mostrarEnganche() ? this.montoEnganche() : null,
        diaPago: v.diaPago as number,
        primeraMensualidadMesVenta: this.primeraMensualidadMesVenta(),
        notas: v.notas?.trim() || null,
        contratoFirmado: this.contratoFirmado(),
        contratoFechaFirma: this.contratoFirmado() ? this.contratoFecha() : null,
        contratoMedio: this.contratoFirmado() ? (this.contratoMedio() as MedioFirma) : null,
        contratoDriveUrl: this.contratoDriveUrl().trim() || null,
        comisionPorcentaje: this.comisionPct() ?? 5,
        ...(this.coasesorActivo()
          ? (() => {
              const co = parseAsesorSeleccion(this.coasesorSel());
              return {
                coasesorUsuarioId: co.usuarioAsesorId,
                coasesorExternoId: co.asesorExternoId,
                coasesorComisionPorcentaje: this.coasesorPct() ?? 5,
              };
            })()
          : {}),
        aportaciones: this.aportacionesDeLaVenta().map((a) => ({ anio: a.anio, mes: a.mes, monto: a.monto as number })),
      });

      if (registrarDepositoComoAbono) {
        try {
          await this.ventasService.registrarPago(venta.id, {
            fecha: v.fechaVenta,
            folio: this.folioDeposito().trim(),
            notas: 'Dinero recibido al apartar el lote',
            lineas: [{ tipo: 'APARTADO', monto: this.depositoTotal() }],
          });
        } catch {
          this.toast.error(
            'La venta se registró, pero no se pudo aplicar el depósito de apartado como abono. Regístralo a mano en la venta.',
          );
        }
        await this.router.navigate(['/panel/ventas', venta.numero]);
        return;
      }

      await this.router.navigate(['/panel/ventas']);
    } catch (error) {
      this.errorMessage.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo registrar la venta. Intenta de nuevo.',
      );
    } finally {
      this.isLoading.set(false);
    }
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

/** true si el porcentaje es negativo o pasa del máximo (vacío cuenta como 0). */
function pctInvalido(valor: number | null, max: number): boolean {
  const v = valor ?? 0;
  return v < 0 || v > max;
}
