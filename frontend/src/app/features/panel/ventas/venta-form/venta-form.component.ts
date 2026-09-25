import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Desarrollo, UsuarioResumen } from '../../../../core/models/lead.model';
import { ESTADO_LOTE_LABELS, Lote } from '../../../../core/models/lote.model';
import { AsesorExterno } from '../../../../core/models/asesor-externo.model';
import { AsesoresExternosService } from '../../../../core/services/asesores-externos.service';
import { LeadsService } from '../../../../core/services/leads.service';
import { LotesService } from '../../../../core/services/lotes.service';
import { ToastService } from '../../../../core/services/toast.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { VentasService } from '../../../../core/services/ventas.service';

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

/** Mismos tipos de pago que ya usa el Cotizador (ver CotizadorComponent.PaymentType), para no
 * inventar un segundo vocabulario: cada uno define si hay enganche/aportación y cómo se etiqueta. */
type TipoPago = 'msi' | 'downpayment' | 'initial' | 'annualities' | 'cash';

const TIPO_PAGO_OPCIONES: { value: TipoPago; label: string; sublabel: string }[] = [
  { value: 'msi', label: 'Sin enganche', sublabel: 'Meses sin intereses' },
  { value: 'downpayment', label: 'Con enganche', sublabel: 'Monto libre' },
  { value: 'initial', label: 'Pago inicial', sublabel: 'Baja la mensualidad' },
  { value: 'annualities', label: 'Con anualidades', sublabel: 'Aportación anual' },
  { value: 'cash', label: 'Contado', sublabel: 'Pago único' },
];

/** "Enganche" / "Pago inicial" / "Aportación anual" (mismo concepto que ya usa Cotización); null
 * para los tipos sin enganche (Sin enganche / Contado). */
const ENGANCHE_LABEL_POR_TIPO: Record<TipoPago, string | null> = {
  msi: null,
  downpayment: 'Enganche',
  initial: 'Pago inicial',
  annualities: 'Aportación anual',
  cash: null,
};

@Component({
  selector: 'app-venta-form',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink],
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
  readonly requiereElegirDeposito = computed(() => this.depositoTotal() > 0 && this.mostrarPlazo());

  readonly tipoPagoOpciones = TIPO_PAGO_OPCIONES;
  readonly tipoPago = signal<TipoPago>('msi');
  readonly montoEnganche = signal<number | null>(null);

  /** Sin enganche/Con enganche usan "MSI" (meses sin intereses); pago inicial/anualidades usan
   * "meses" a secas — mismo texto que ya arma CotizadorComponent.paymentMethodLabel. */
  readonly unidadPlazo = computed(() =>
    this.tipoPago() === 'msi' || this.tipoPago() === 'downpayment' ? 'MSI' : 'meses',
  );
  readonly engancheLabelActual = computed(() => ENGANCHE_LABEL_POR_TIPO[this.tipoPago()]);
  readonly mostrarEnganche = computed(() => this.engancheLabelActual() !== null);
  readonly mostrarPlazo = computed(() => this.tipoPago() !== 'cash');

  readonly form = this.fb.group({
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    cliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    asesor: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    formaPago: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    fechaVenta: this.fb.control(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] }),
    // Opcionales: una venta de contado no los necesita.
    mensualidad: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    plazoMeses: this.fb.control<number | null>(null, { validators: [Validators.min(1)] }),
    notas: this.fb.control(''),
    marcarLoteVendido: this.fb.control(true, { nonNullable: true }),
  });

  /** Mini-formulario para agregar un lote a la lista (separado del form principal: no se envía
   * directo, cada "Agregar" lo empuja a lotesAgregados). */
  readonly loteParaAgregarId = signal<number | null>(null);
  readonly precioParaAgregar = signal<number | null>(null);

  /** Lotes del desarrollo elegido que todavía no se agregaron, para no repetir uno en la misma venta. */
  readonly lotesDisponiblesParaAgregar = computed<Lote[]>(() => {
    const agregadosIds = new Set(this.lotesAgregados().map((l) => l.lote.id));
    return this.lotesDelDesarrollo().filter((l) => !agregadosIds.has(l.id));
  });

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
    this.usuariosService.paraVenta().then((u) => this.asesoresInternos.set(u));
    this.asesoresExternosService.listarActivos().then((a) => this.asesoresExternos.set(a));
    this.actualizarFormaPago();
    // Cualquier cambio en plazo regenera el texto de forma de pago Y recalcula la mensualidad
    // (mismo cálculo que ya usa el Cotizador: saldo a financiar ÷ meses); ambos siguen editables
    // a mano por si el trato real fue distinto.
    this.form.controls.plazoMeses.valueChanges.subscribe(() => {
      this.actualizarFormaPago();
      this.actualizarMensualidad();
    });
  }

  /** Igual que CotizadorComponent.selectPaymentType: cambiar de tipo limpia lo que ya no aplica
   * (ej. el monto de enganche si pasas a "Sin enganche", plazo/mensualidad si pasas a "Contado"). */
  seleccionarTipoPago(tipo: TipoPago): void {
    this.tipoPago.set(tipo);
    if (!this.mostrarEnganche()) {
      this.montoEnganche.set(null);
    }
    if (tipo === 'cash') {
      this.form.controls.mensualidad.setValue(null);
      this.form.controls.plazoMeses.setValue(null);
      this.aplicarDepositoA.set(null);
    }
    this.actualizarFormaPago();
    this.actualizarMensualidad();
  }

  /** Se llama cada vez que cambia el monto de enganche/pago inicial/aportación a mano. */
  onMontoEngancheChange(valor: number | null): void {
    this.montoEnganche.set(valor);
    this.actualizarMensualidad();
  }

  private actualizarFormaPago(): void {
    const plazo = this.form.controls.plazoMeses.value;
    const tipo = this.tipoPago();
    const opcion = TIPO_PAGO_OPCIONES.find((o) => o.value === tipo)!;

    const texto = tipo === 'cash' || !plazo ? opcion.label : `${opcion.label} · ${plazo} ${this.unidadPlazo()}`;
    this.form.controls.formaPago.setValue(texto);
  }

  /** Mensualidad = saldo a financiar (precio total − enganche) ÷ meses, igual que
   * CotizadorComponent.monthlyPayment (0% interés — "meses sin intereses"). Se recalcula cada vez
   * que cambia el precio total, el plazo, el enganche o el tipo de pago; el campo sigue editable
   * por si el trato real llevó un ajuste manual. */
  private actualizarMensualidad(): void {
    const plazo = this.form.controls.plazoMeses.value;
    if (this.tipoPago() === 'cash' || !plazo || plazo <= 0) {
      return;
    }

    const enganche = this.mostrarEnganche() ? (this.montoEnganche() ?? 0) : 0;
    const depositoParaMensualidad = this.aplicarDepositoA() === 'mensualidad' ? this.depositoTotal() : 0;
    const saldoAFinanciar = this.precioTotal() - enganche - depositoParaMensualidad;
    if (saldoAFinanciar <= 0) {
      this.form.controls.mensualidad.setValue(null);
      return;
    }

    this.form.controls.mensualidad.setValue(Math.round((saldoAFinanciar / plazo) * 100) / 100);
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
    this.precioParaAgregar.set(lote ? Math.round(lote.desarrollo.precioM2 * lote.superficie) : null);
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

  quitarLote(loteId: number): void {
    this.lotesAgregados.update((actuales) => actuales.filter((l) => l.lote.id !== loteId));
    this.aplicarDepositoA.set(null);
    this.actualizarMensualidad();
  }

  async onSubmit(): Promise<void> {
    const faltaMontoEnganche = this.mostrarEnganche() && !this.montoEnganche();
    const faltaElegirDeposito = this.requiereElegirDeposito() && !this.aplicarDepositoA();
    if (
      this.form.invalid ||
      this.lotesAgregados().length === 0 ||
      faltaMontoEnganche ||
      faltaElegirDeposito ||
      this.isLoading()
    ) {
      this.form.markAllAsTouched();
      if (this.lotesAgregados().length === 0) {
        this.errorMessage.set('Agrega al menos un lote a la venta.');
      } else if (faltaMontoEnganche) {
        this.errorMessage.set(`Ingresa el monto de ${this.engancheLabelActual()!.toLowerCase()}.`);
      } else if (faltaElegirDeposito) {
        this.errorMessage.set('Indica si el dinero ya apartado baja la mensualidad o el saldo.');
      }
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();
    // Contado no tiene mensualidad que reducir: si había depósito, siempre se registra como abono.
    const registrarDepositoComoAbono = this.depositoTotal() > 0 && this.aplicarDepositoA() !== 'mensualidad';

    try {
      const venta = await this.ventasService.crear({
        lotes: this.lotesAgregados().map((l) => ({ loteId: l.lote.id, precio: l.precio })),
        cliente: v.cliente,
        ...parseAsesorSeleccion(v.asesor),
        formaPago: v.formaPago,
        fechaVenta: v.fechaVenta,
        mensualidad: v.mensualidad,
        plazoMeses: v.plazoMeses,
        engancheLabel: this.engancheLabelActual(),
        enganche: this.mostrarEnganche() ? this.montoEnganche() : null,
        notas: v.notas?.trim() || null,
        marcarLoteVendido: v.marcarLoteVendido,
      });

      if (registrarDepositoComoAbono) {
        try {
          await this.ventasService.registrarPago(venta.id, {
            fecha: v.fechaVenta,
            monto: this.depositoTotal(),
            notas: 'Dinero recibido al apartar el lote',
          });
        } catch {
          this.toast.error(
            'La venta se registró, pero no se pudo aplicar el depósito de apartado como abono. Regístralo a mano en la venta.',
          );
        }
        await this.router.navigate(['/panel/ventas', venta.id]);
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
