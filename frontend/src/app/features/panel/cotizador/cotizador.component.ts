import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { LucideCalendar, LucideCheck, LucideDollarSign, LucideHistory } from '@lucide/angular';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { PdfService, QuotePdfData } from '../../../core/services/pdf-cotizacion.service';
import { CotizacionesService } from '../../../core/services/cotizaciones.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { PromocionesService } from '../../../core/services/promociones.service';
import { ToastService } from '../../../core/services/toast.service';
import { Desarrollo } from '../../../core/models/lead.model';
import { Lote } from '../../../core/models/lote.model';
import { Promocion, TipoPrecioPromocion } from '../../../core/models/promocion.model';
import {
  Aportacion,
  MESES_NOMBRE,
  aniosElegibles,
  aportacionesValidas,
  mensualidadConAportaciones,
  ordenarAportaciones,
  tablaConAportaciones,
  totalAportaciones,
} from '../../../core/utils/aportaciones';
import {
  SIN_FORMA_PAGO,
  SeleccionPago,
  TipoPago,
  alternarFormaPago,
  estaBloqueado,
  estaMarcado,
  etiquetaEntrada,
  etiquetaFormaPago,
  hayFormaPago,
  llevaEntrada,
  maximoAnualidades,
} from '../../../core/utils/formas-pago';
import { AportacionesSelectorComponent } from '../../../shared/aportaciones-selector/aportaciones-selector.component';
import { PROJECTS_CONFIG } from '../../../core/data/proyectos-cotizador.config';
import { FadeInDirective } from '../../../shared/motion/fade-in.directive';
import { PressDirective } from '../../../shared/motion/press.directive';
import { RevelarDirective } from '../../../shared/motion/revelar.directive';
import { MonedaInputDirective, formatearNumero, limpiarNumero, numeroDeTexto } from '../../../shared/moneda-input/moneda-input.directive';
import {
  LoteCotizado,
  MESES_DEL_ANIO,
  OPCIONES_PAGO,
  PRECIO_CONTADO_LOTE_ESTANDAR_SAMAI,
  PRECIO_M2_CONTADO_SAMAI,
  PRECIO_M2_MACROLOTE,
  PRECIO_M2_NANUU,
  PRECIO_M2_SAMAI,
  PROYECTOS_COTIZADOR,
  ProjectId,
  SUPERFICIE_ESTANDAR_M2,
  SUPERFICIE_MACROLOTE_M2,
  compararNatural,
  enumerar,
  leerSuperficieEscrita,
} from './cotizador-datos';
import { AmortizationRow, MESES_MAYUSCULAS, tablaConAnualidad, tablaFinanciamientoNormal } from './cotizador-tablas';
import { solicitudDeHistorial } from './cotizador-historial';
import { CotizadorResumenComponent, ResumenCotizacion } from './cotizador-resumen/cotizador-resumen.component';

export type { LoteCotizado, ProjectId } from './cotizador-datos';
export type { AmortizationRow } from './cotizador-tablas';

@Component({
  selector: 'app-cotizador',
  standalone: true,
  imports: [MonedaInputDirective, RevelarDirective, 
    DatePipe,
    FormsModule,
    RouterLink,
    FadeInDirective,
    PressDirective,
    LucideHistory,
    LucideDollarSign,
    LucideCalendar,
    LucideCheck,
    AportacionesSelectorComponent,
    CotizadorResumenComponent,
  ],
  templateUrl: './cotizador.component.html',
})
export class CotizadorComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly pdfService = inject(PdfService);
  private readonly cotizacionesService = inject(CotizacionesService);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly promocionesService = inject(PromocionesService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly lotesDisponibles = signal<Lote[]>([]);
  readonly isCargandoLotes = signal(false);

  /** true en la ruta pública sin login (/cotizador-publico, ver app.routes.ts). Ahí no hay un
   * asesor de verdad detrás, así que la cotización se registra en el historial atribuida al
   * usuario de sistema en vez de a un asesor autenticado (ver `registrarHistorialCotizacion`) y
   * tampoco se muestran los accesos de admin (ver `esAdmin` abajo): si quien abre el link sigue
   * con sesión de admin guardada en ese navegador de una visita anterior al panel interno, esta
   * ruta pública no debe revelar ni ofrecer esos atajos de todos modos. */
  readonly esPublico = this.route.snapshot.data['publico'] === true;

  readonly esAdmin = computed(() => !this.esPublico && this.auth.currentUser()?.rol === 'ADMIN');

  showQuoteErrors = false;

  selectedProject: ProjectId = 'samai';

  // Promociones vigentes (cargadas al iniciar) y cuál está aplicada a esta cotización, si acaso.
  readonly promocionesActivas = signal<Promocion[]>([]);
  readonly mostrarSelectorPromociones = signal(false);
  promocionSeleccionada: Promocion | null = null;

  async ngOnInit(): Promise<void> {
    try {
      this.promocionesActivas.set(await this.promocionesService.listarActivas());
    } catch {
      // Si fallan las promociones, el cotizador sigue funcionando normal sin ellas.
    }

    if (!this.esPublico) {
      try {
        this.desarrollos.set(await this.leadsService.listarDesarrollos());
      } catch {
        // El selector de lote del inventario es una comodidad; si falla, se sigue capturando
        // manzana/lote a mano como siempre.
      }
    }

    // Llegar desde el botón "Cotizar" de un lote en el Plano: ?proyecto=samai&manzana=5&lote=11
    const params = this.route.snapshot.queryParamMap;
    const proyectoParam = params.get('proyecto');
    if (proyectoParam === 'samai' || proyectoParam === 'nanuu') {
      this.selectProject(proyectoParam);
    }

    try {
      await this.cargarLotesDisponibles();
    } catch {
      // Igual: si falla, se sigue capturando manzana/lote a mano.
    }

    const manzanaParam = params.get('manzana');
    const loteParam = params.get('lote');
    if (manzanaParam) this.blockNumber = manzanaParam;
    if (loteParam) {
      this.lotNumber = loteParam;
      this.onLoteNumberChange();
      this.irAFormaDePago();
    }
  }

  /** Al llegar con un lote ya elegido desde el Plano ("Cotizar este lote"), el proyecto, la manzana
   * y el lote ya vienen llenos: lo único que falta es escoger la forma de pago, así que se baja
   * directo ahí en vez de dejar a quien cotiza arriba, en el encabezado, sin saber qué sigue. Se
   * espera un instante a que Angular pinte los datos del lote antes de medir a dónde desplazarse. */
  private irAFormaDePago(): void {
    setTimeout(() => {
      const destino = document.getElementById('forma-de-pago');
      if (!destino) return;
      const reducirMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      destino.scrollIntoView({ behavior: reducirMovimiento ? 'auto' : 'smooth', block: 'start' });
    }, 250);
  }

  /** 'SAMAI Campestre' / 'Aldea Nanuu': mismo nombre que va en el PDF de la cotización. */
  private nombreDesarrolloActual(): string {
    return PROYECTOS_COTIZADOR[this.selectedProject].nombre;
  }

  /** Lotes disponibles del desarrollo actual: alimentan las sugerencias de manzana/lote de abajo.
   * En /cotizador-publico no hay sesión, así que usa el mismo endpoint público que alimenta los
   * planos públicos (que trae cualquier estado) y aquí se filtra a solo disponibles. */
  private async cargarLotesDisponibles(): Promise<void> {
    this.isCargandoLotes.set(true);
    try {
      if (this.esPublico) {
        const lotes = await this.lotesService.listarPublicoPorProyecto(this.selectedProject);
        this.lotesDisponibles.set(lotes.filter((l) => l.estado === 'DISPONIBLE'));
        return;
      }

      const desarrollo = this.desarrollos().find((d) => d.nombre === this.nombreDesarrolloActual());
      if (!desarrollo) {
        this.lotesDisponibles.set([]);
        return;
      }
      this.lotesDisponibles.set(await this.lotesService.listarDisponibles(desarrollo.id));
    } catch {
      this.lotesDisponibles.set([]);
    } finally {
      this.isCargandoLotes.set(false);
    }
  }

  /** Manzanas del inventario disponibles del desarrollo actual, en orden numérico, para sugerirlas
   * mientras el asesor escribe en el campo Manzana. */
  get manzanasDisponibles(): string[] {
    const unicas = new Set(this.lotesDisponibles().map((l) => l.manzana.trim()));
    return Array.from(unicas).sort(compararNatural);
  }

  /** Lotes del inventario que pertenecen a la manzana capturada, en orden numérico, para sugerirlos
   * en el campo Lote junto con su superficie. */
  get lotesDeManzanaActual(): Lote[] {
    const manzana = this.blockNumber.trim().toLowerCase();
    if (!manzana) return [];
    return this.lotesDisponibles()
      .filter((l) => l.manzana.trim().toLowerCase() === manzana)
      .sort((a, b) => compararNatural(a.numeroLote, b.numeroLote));
  }

  /** Si el lote capturado coincide con uno real del inventario, se autocompleta la superficie —
   * el precio sigue siendo el precio por m² del desarrollo (nunca uno capturado a mano) — pero el
   * lote en sí NO cambia de estado: es solo para no capturar todo a mano. Como la superficie ya es
   * un dato real del lote, se bloquean el precio por m² y la superficie: ya no tiene sentido que el
   * asesor los edite a mano. */
  onLoteNumberChange(): void {
    const numeroLote = this.lotNumber.trim().toLowerCase();
    const lote = this.lotesDeManzanaActual.find((l) => l.numeroLote.trim().toLowerCase() === numeroLote);
    if (!lote) return;
    this.selectArea(lote.superficie);
    // El precio de un lote real se determina siempre por su superficie, sin importar qué precio
    // por m² hubiera quedado seleccionado antes a mano.
    this.pricePerM2 =
      lote.precioM2Propio ??
      (this.selectedProject === 'nanuu'
        ? PRECIO_M2_NANUU
        : lote.superficie > SUPERFICIE_MACROLOTE_M2
          ? PRECIO_M2_MACROLOTE
          : PRECIO_M2_SAMAI);
    this.loteBloqueado = true;
    this.revisarPromocionCompatible();
  }

  /** Al editar manzana o lote a mano, se desbloquea: si el nuevo valor vuelve a coincidir con un
   * lote real, onLoteNumberChange() lo vuelve a bloquear al confirmarlo. */
  desbloquearLote(): void {
    this.loteBloqueado = false;
  }

  currentDate = new Date();

  /** Mes del primer pago del plan. Con enganche o pago inicial, ese mes se paga la entrada y el plan empieza
   * el siguiente. Son dos fechas fijas (no una nueva en cada lectura) porque son la entrada del selector de
   * aportaciones: si cambiara en cada detección de cambios, el selector se reiniciaría. */
  private readonly inicioPlanMesActual = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth(), 1);
  private readonly inicioPlanMesSiguiente = new Date(this.currentDate.getFullYear(), this.currentDate.getMonth() + 1, 1);

  get inicioPlan(): Date {
    return this.llevaEntrada ? this.inicioPlanMesSiguiente : this.inicioPlanMesActual;
  }

  // Superficie seleccionada
  selectedArea: number = SUPERFICIE_ESTANDAR_M2;

  // Indica si el usuario eligió superficie personalizada
  isCustomArea: boolean = false;

  // Precio actual por m²
  pricePerM2: number = PRECIO_M2_SAMAI;

  customAreaDisplay = '200';

  /** Forma de pago elegida (ver core/utils/formas-pago): una base (sin enganche, con enganche, pago inicial
   * o contado) y, opcional, anualidades o aportaciones. Empieza vacía para que el asesor la elija. */
  formaPago: SeleccionPago = SIN_FORMA_PAGO;

  /** La promoción va aparte de la forma de pago y no se combina con nada. */
  modoPromocion = false;

  // Enganche de SAMAI: monto libre en pesos (Nanuu conserva su 20% fijo, ver downPayment()).
  downPaymentAmount: number = 0;
  downPaymentAmountDisplay = '';

  // Pago inicial: monto fijo en pesos (a diferencia del enganche, que es un %).
  initialPayment: number = 0;
  initialPaymentDisplay = '';

  selectedMonths: number = 60;

  /** Nanuu: permite cotizar un plazo sin intereses (p. ej. una promoción puntual) sin tener que
   * cambiar de proyecto ni de plazo — activado por defecto porque es la condición normal. */
  readonly interesesActivados = signal(true);

  toggleIntereses(): void {
    this.interesesActivados.update((activos) => !activos);
  }

  advisorName: string = this.auth.currentUser()?.nombre ?? '';

  clientName: string = '';

  blockNumber: string = '';
  lotNumber: string = '';

  // true cuando manzana/lote coinciden con un lote real del inventario: mientras esté así, precio
  // por m² y superficie quedan bloqueados porque ya vienen del lote, no se capturan a mano.
  loteBloqueado = false;

  // ==============================
  // VARIOS LOTES EN UNA COTIZACIÓN
  // ==============================

  /** Lotes ya confirmados de esta cotización. Los campos de Manzana/Lote/Superficie de arriba
   * son siempre el lote "en captura" (el más reciente o el único); agregarOtroLote() lo guarda
   * aquí y limpia los campos para capturar el siguiente. Así, si nunca se usa este botón, el
   * comportamiento es idéntico al de antes: un solo lote, esta lista vacía. */
  readonly lotesAgregados = signal<LoteCotizado[]>([]);

  get esMultiLote(): boolean {
    return this.lotesAgregados().length > 0;
  }

  /** Con varios lotes ya agregados, Manzana/Lote de arriba dejan de ser obligatorios: si se dejan
   * vacíos, es que el usuario ya terminó y no quiere agregar uno más (ver isQuoteDataComplete). En
   * cambio, si ya escribió algo en cualquiera de los dos, sí hace falta completarlo o borrarlo —
   * dejarlo a medias sería ambiguo. Con un solo lote (el caso de siempre) siempre hacen falta. */
  get requiereLoteEnCaptura(): boolean {
    return !this.esMultiLote || this.blockNumber.trim().length > 0 || this.lotNumber.trim().length > 0;
  }

  /** El lote que se está capturando ahora mismo (Manzana/Lote/Superficie de arriba), o null si con
   * varios lotes ya agregados se dejó en blanco a propósito (ver requiereLoteEnCaptura) — en ese
   * caso no cuenta para el total ni aparece en el PDF. */
  private get loteEnCapturaOpcional(): LoteCotizado | null {
    if (!this.requiereLoteEnCaptura) {
      return null;
    }
    return { manzana: this.blockNumber, lote: this.lotNumber, superficie: this.selectedArea, pricePerM2: this.precioM2Vigente };
  }

  /** Todos los lotes de la cotización: los ya confirmados más el que está en captura ahora mismo
   * (si lo hay). */
  private get todosLosLotes(): LoteCotizado[] {
    const actual = this.loteEnCapturaOpcional;
    return actual ? [...this.lotesAgregados(), actual] : this.lotesAgregados();
  }

  get cantidadLotes(): number {
    return this.todosLosLotes.length;
  }

  get superficieTotal(): number {
    return this.todosLosLotes.reduce((suma, l) => suma + l.superficie, 0);
  }

  /** Precio por m² promedio ponderado de todos los lotes de la cotización: coincide con
   * pricePerM2 cuando solo hay un lote, así que se puede usar siempre sin distinguir casos. */
  get precioM2Promedio(): number {
    return this.superficieTotal > 0 ? this.totalPrice / this.superficieTotal : this.pricePerM2;
  }

  /** Guarda el lote que se estaba capturando (Manzana/Lote/Superficie/Precio de arriba) en la
   * lista y limpia esos campos para capturar el siguiente. Precio de contado y promociones dejan
   * de tener sentido con varios lotes juntos (están calibrados para un solo lote estándar), así
   * que se desactivan solos si estaban aplicados. */
  agregarOtroLote(): void {
    if (!this.blockNumber.trim() || !this.lotNumber.trim() || this.selectedArea <= 0) {
      this.showQuoteErrors = true;
      return;
    }

    this.lotesAgregados.update((lotes) => [
      ...lotes,
      {
        manzana: this.blockNumber.trim(),
        lote: this.lotNumber.trim(),
        superficie: this.selectedArea,
        pricePerM2: this.pricePerM2,
      },
    ]);

    this.blockNumber = '';
    this.lotNumber = '';
    this.loteBloqueado = false;
    this.selectedArea = SUPERFICIE_ESTANDAR_M2;
    this.isCustomArea = false;
    this.customAreaDisplay = '200';
    this.pricePerM2 = this.selectedProject === 'nanuu' ? PRECIO_M2_NANUU : PRECIO_M2_SAMAI;

    // Contado y las promociones de 1 lote no aplican a varios lotes.
    this.quitarContado();
    if (this.promocionSeleccionada?.modalidad !== 'BONO_LOTES') {
      this.promocionSeleccionada = null;
      this.modoPromocion = false;
    }
    this.mostrarSelectorPromociones.set(false);
  }

  quitarLoteAgregado(index: number): void {
    this.lotesAgregados.update((lotes) => lotes.filter((_, i) => i !== index));
  }

  selectProject(project: ProjectId): void {
    this.promocionSeleccionada = null;
    this.promocionUsaAportaciones = false;
    this.aportaciones = [];
    this.selectedProject = project;
    this.loteBloqueado = false;
    this.interesesActivados.set(true);

    if (project === 'samai') {
      this.pricePerM2 = PRECIO_M2_SAMAI;
      this.selectedArea = SUPERFICIE_ESTANDAR_M2;
      this.selectedMonths = 60;
    }

    if (project === 'nanuu') {
      this.pricePerM2 = PRECIO_M2_NANUU;
      this.selectedArea = SUPERFICIE_ESTANDAR_M2;
      this.selectedMonths = 24;
      this.isCustomArea = false;
      this.customAreaDisplay = '200';
    }

    this.customAreaDisplay = this.selectedArea.toLocaleString('en-US');

    this.isCustomArea = false;

    void this.cargarLotesDisponibles();
  }

  selectArea(area: number): void {
    this.selectedArea = area;
    this.isCustomArea = false;
    this.customAreaDisplay = area.toLocaleString('en-US');
    this.aplicarPrecioMacrolote();
    this.revisarPromocionCompatible();
  }

  /** SAMAI cobra $170/m² (tarifa de macrolote) en cuanto la superficie pasa de
   * SUPERFICIE_MACROLOTE_M2, sin importar si se escribió a mano o vino de un lote del inventario. */
  private aplicarPrecioMacrolote(): void {
    if (this.selectedProject === 'samai' && this.selectedArea > SUPERFICIE_MACROLOTE_M2) {
      this.pricePerM2 = PRECIO_M2_MACROLOTE;
      this.quitarContado();
    }
  }

  selectCustomArea(): void {
    this.isCustomArea = true;
    this.customAreaDisplay = this.selectedArea.toLocaleString('en-US');
  }

  selectPricePerM2(price: number): void {
    this.pricePerM2 = price;

    // Si seleccionamos precio de macrolote
    if (price === PRECIO_M2_MACROLOTE) {
      this.isCustomArea = true;
      this.selectedArea = 0;
      this.customAreaDisplay = '0';
      // La tarifa de macrolote no tiene contado y saca de la promoción (seleccionarPromocion la vuelve a poner).
      this.quitarContado();
      this.modoPromocion = false;
    }

    // Si regresamos al precio normal
    if (price === PRECIO_M2_SAMAI) {
      this.isCustomArea = false;
      this.selectedArea = SUPERFICIE_ESTANDAR_M2;
      this.customAreaDisplay = '200';
    }

    this.revisarPromocionCompatible();
  }

  onCustomAreaInput(event: Event): void {
    const superficie = leerSuperficieEscrita((event.target as HTMLInputElement).value);
    if (!superficie) {
      this.selectedArea = 0;
      this.customAreaDisplay = '';
      return;
    }

    this.selectedArea = superficie.valor;
    this.aplicarPrecioMacrolote();
    this.revisarPromocionCompatible();
    this.customAreaDisplay = superficie.texto;
  }

  onInitialPaymentInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const limpio = limpiarNumero(input.value, 2);

    this.initialPayment = numeroDeTexto(limpio);
    this.initialPaymentDisplay = formatearNumero(limpio, 2);
    input.value = this.initialPaymentDisplay;
  }

  get initialPaymentInvalid(): boolean {
    if (!this.conPagoInicial) {
      return false;
    }

    if (this.initialPayment <= 0) {
      return true;
    }

    return this.initialPayment >= this.totalPrice;
  }

  onDownPaymentAmountInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const limpio = limpiarNumero(input.value, 2);

    this.downPaymentAmount = numeroDeTexto(limpio);
    this.downPaymentAmountDisplay = formatearNumero(limpio, 2);
    input.value = this.downPaymentAmountDisplay;
  }

  get downPaymentAmountInvalid(): boolean {
    if (!this.conEnganche) {
      return false;
    }

    if (this.downPaymentAmount <= 0) {
      return true;
    }

    return this.downPaymentAmount >= this.totalPrice;
  }

  /** Suma de todos los lotes de la cotización: cada uno con su propia superficie y precio por m²
   * (el umbral de macrolote depende de la superficie de cada lote). Con un solo lote (el caso de
   * siempre, lotesAgregados vacío) es exactamente selectedArea * pricePerM2, igual que antes. */
  get totalPrice(): number {
    return this.todosLosLotes.reduce((suma, l) => suma + l.superficie * l.pricePerM2, 0);
  }

  /** En SAMAI, cuando se cotiza un solo lote mayor a 200 m² (fuera de macrolote) y la forma de
   * pago es de contado, el precio por m² pasa a $600 MXN; con cualquier otra forma de pago se
   * mantiene el precio por m² normal del lote. */
  get aplicaPrecioContadoMayor200(): boolean {
    return (
      !this.esMultiLote &&
      this.esContado &&
      this.selectedProject === 'samai' &&
      this.selectedArea > SUPERFICIE_ESTANDAR_M2 &&
      this.pricePerM2 !== PRECIO_M2_MACROLOTE
    );
  }

  /** Precio por m² vigente del lote actual ($600/m² si aplica contado en SAMAI para > 200 m²,
   * o el pricePerM2 base en cualquier otro caso). */
  get precioM2Vigente(): number {
    return this.aplicaPrecioContadoMayor200 ? PRECIO_M2_CONTADO_SAMAI : this.pricePerM2;
  }

  /** Precio solo del lote que se está capturando ahora mismo (Manzana/Lote/Superficie de arriba),
   * sin sumar los ya agregados a la lista — lo que se muestra en la caja de "Configuración del
   * terreno", a diferencia de totalPrice/displayedLandPrice que son el total combinado. 0 si con
   * varios lotes ya agregados se dejó en blanco a propósito (ver requiereLoteEnCaptura). */
  get precioLoteActual(): number {
    return this.requiereLoteEnCaptura ? this.selectedArea * this.precioM2Vigente : 0;
  }

  /** En SAMAI, el lote estándar de 200 m² de contado tiene un precio especial fijo de $120,000, y
   * los lotes mayores a 200 m² (fuera de macrolote) se cotizan a $600/m² solo cuando el pago es
   * de contado. En cualquier otro caso cobra el precio de lista. */
  get cashPrice(): number {
    if (!this.esMultiLote && this.selectedProject === 'samai' && this.pricePerM2 !== PRECIO_M2_MACROLOTE) {
      if (this.selectedArea === SUPERFICIE_ESTANDAR_M2) {
        return PRECIO_CONTADO_LOTE_ESTANDAR_SAMAI;
      }
      if (this.selectedArea > SUPERFICIE_ESTANDAR_M2) {
        return this.selectedArea * PRECIO_M2_CONTADO_SAMAI;
      }
    }

    return this.totalPrice;
  }

  /** Si "Contado" está seleccionado pero el lote no es el estándar de 200 m² de SAMAI (o hay
   * varios lotes juntos), no hay descuento real: el resumen debe decirlo (ver cashPrice) en vez
   * de mostrar "Precio especial". */
  get cashDiscountApplied(): boolean {
    return (
      !this.esMultiLote &&
      this.esContado &&
      this.selectedProject === 'samai' &&
      this.selectedArea === SUPERFICIE_ESTANDAR_M2
    );
  }

  /** Las tarjetas de forma de pago de cada proyecto, en el orden en que se muestran. */
  readonly opcionesPago = OPCIONES_PAGO;

  pagoMarcado(tipo: TipoPago): boolean {
    return estaMarcado(this.formaPago, tipo);
  }

  /** Anualidades y aportaciones piden un plazo de más de 12 meses; contado, un solo lote fuera de macrolote. */
  private pagoDisponible(tipo: TipoPago): boolean {
    if (tipo === 'annualities' || tipo === 'aportaciones') return this.annualitiesAvailable;
    if (tipo === 'cash') return this.contadoDisponible;
    return true;
  }

  /** Lo marcado siempre se puede desmarcar; lo demás se apaga si no está disponible o no se combina con lo
   * ya marcado (ver estaBloqueado). */
  pagoDeshabilitado(tipo: TipoPago): boolean {
    if (this.pagoMarcado(tipo)) return false;
    return !this.pagoDisponible(tipo) || estaBloqueado(this.formaPago, tipo);
  }

  alternarPago(tipo: TipoPago): void {
    if (this.pagoDeshabilitado(tipo)) return;
    this.formaPago = alternarFormaPago(this.formaPago, tipo);
  }

  private quitarContado(): void {
    if (this.formaPago.base === 'cash') {
      this.formaPago = { ...this.formaPago, base: null };
    }
  }

  get hayFormaPago(): boolean {
    return this.isPromocion || hayFormaPago(this.formaPago);
  }

  get esContado(): boolean {
    return !this.isPromocion && this.formaPago.base === 'cash';
  }

  get conEnganche(): boolean {
    return !this.isPromocion && this.formaPago.base === 'downpayment';
  }

  get conPagoInicial(): boolean {
    return !this.isPromocion && this.formaPago.base === 'initial';
  }

  /** Con enganche o pago inicial: la entrada se paga este mes y las mensualidades empiezan el siguiente. */
  get llevaEntrada(): boolean {
    return !this.isPromocion && llevaEntrada(this.formaPago);
  }

  /** 'Enganche' o 'Pago inicial' (o 'Enganche' a secas cuando no hay entrada, para el resumen en $0). */
  get etiquetaEntrada(): string {
    return etiquetaEntrada(this.formaPago) ?? 'Enganche';
  }

  /** "Contado" no está disponible con tarifa de macrolote (siempre fue así) ni al combinar varios
   * lotes en la misma cotización (el precio especial es exclusivo de un solo lote estándar). */
  get contadoDisponible(): boolean {
    return !this.esMultiLote && this.pricePerM2 !== PRECIO_M2_MACROLOTE;
  }

  /** Aplica una promoción: cambia al proyecto al que pertenece y activa el modo "promocion".
   * selectProject() deja la tarifa en la normal (Lotes) por defecto; una promoción de Hectáreas
   * necesita quedar en tarifa de macrolote para no contradecirse con la tarifa que exige. */
  seleccionarPromocion(promo: Promocion): void {
    if (this.selectedProject !== promo.proyecto) {
      this.selectProject(promo.proyecto);
    }
    if (promo.tipoPrecio === 'HECTAREA' && this.pricePerM2 !== PRECIO_M2_MACROLOTE) {
      this.selectPricePerM2(PRECIO_M2_MACROLOTE);
      // selectPricePerM2(170) saca del modo promoción; se vuelve a poner un par de líneas abajo, junto
      // con el resto de las promociones.
    } else if (promo.tipoPrecio === 'LOTE' && this.selectedProject === 'samai' && this.pricePerM2 === PRECIO_M2_MACROLOTE) {
      this.selectPricePerM2(PRECIO_M2_SAMAI);
    }
    if (promo.modalidad === 'BONO_LOTES' && promo.plazoFijo) {
      this.selectedMonths = promo.plazoFijo;
    }
    this.promocionSeleccionada = promo;
    this.modoPromocion = true;
    this.mostrarSelectorPromociones.set(false);
  }

  /** Botón "PROMOCIÓN" junto a SAMAI/NANUU: aplica directo si hay una sola elegible, o abre el selector. */
  togglePromocion(): void {
    const elegibles = this.promocionesElegibles;

    if (elegibles.length === 0) {
      return;
    }

    if (elegibles.length === 1) {
      this.seleccionarPromocion(elegibles[0]);
      return;
    }

    this.mostrarSelectorPromociones.update((visible) => !visible);
  }

  quitarPromocion(): void {
    this.promocionSeleccionada = null;
    this.promocionUsaAportaciones = false;
    this.modoPromocion = false;
    this.mostrarSelectorPromociones.set(false);
  }

  get isPromocion(): boolean {
    return this.modoPromocion && this.promocionSeleccionada !== null;
  }

  get isPromocionBonoLotes(): boolean {
    return this.isPromocion && this.promocionSeleccionada?.modalidad === 'BONO_LOTES';
  }

  /** Desde cuántos lotes aplica la promoción de bono: 1 si tiene bono para 1 lote, 2 si no. */
  get lotesMinimosPromocion(): number {
    return (this.promocionSeleccionada?.bonoUnLote ?? 0) > 0 ? 1 : 2;
  }

  /** Bono en pesos aplicado cuando la promoción activa es de modalidad BONO_LOTES (1, 2 o 3+ lotes). */
  get bonoPromocion(): number {
    if (!this.isPromocionBonoLotes || !this.promocionSeleccionada) {
      return 0;
    }
    if (this.cantidadLotes >= 3) {
      return this.promocionSeleccionada.bonoTresLotes ?? 0;
    }
    if (this.cantidadLotes === 2) {
      return this.promocionSeleccionada.bonoDosLotes ?? 0;
    }
    return this.promocionSeleccionada.bonoUnLote ?? 0;
  }

  /** LOTE (precio normal por m²) o HECTAREA (tarifa de macrolote): solo SAMAI llega a HECTAREA,
   * cuando el precio por m² vigente ya es el de macrolote (ver aplicarPrecioMacrolote/selectPricePerM2). */
  get tipoPrecioActual(): TipoPrecioPromocion {
    return this.selectedProject === 'samai' && this.pricePerM2 === PRECIO_M2_MACROLOTE ? 'HECTAREA' : 'LOTE';
  }

  /** Promociones activas que de verdad aplican a la cotización actual: una promoción de Hectáreas
   * solo se ofrece mientras la tarifa de macrolote está vigente, y una de Lotes solo mientras no lo
   * está. Con varios lotes solo aplican promociones de modalidad BONO_LOTES. */
  get promocionesElegibles(): Promocion[] {
    return this.promocionesActivas().filter((promo) => {
      if (promo.tipoPrecio !== this.tipoPrecioActual) {
        return false;
      }
      if (this.esMultiLote) {
        return promo.modalidad === 'BONO_LOTES';
      }
      return true;
    });
  }

  /** Si un cambio de superficie o precio por m² deja de calificar para la tarifa de la promoción ya
   * aplicada, se quita sola: seguir mostrándola sería incoherente con el nuevo precio. */
  private revisarPromocionCompatible(): void {
    if (!this.promocionSeleccionada) return;
    if (this.promocionSeleccionada.tipoPrecio !== this.tipoPrecioActual) {
      this.promocionSeleccionada = null;
      this.modoPromocion = false;
      return;
    }
    if (this.esMultiLote && this.promocionSeleccionada.modalidad !== 'BONO_LOTES') {
      this.promocionSeleccionada = null;
      this.modoPromocion = false;
    }
  }

  /** Si el plazo no da para ninguna aportación anual, no hay forma de aplicar la promoción sin
   * importar la superficie del lote — por eso oculta el panel entero (selector de mes, resumen)
   * y solo muestra el aviso de que hace falta un plazo mayor. */
  get promocionSinAportacionesDisponibles(): boolean {
    if (!this.isPromocion || this.isPromocionBonoLotes) {
      return false;
    }
    // Con aportaciones por mes, lo que importa es que el plazo tenga meses elegibles (ver aniosElegibles).
    return this.promocionUsaAportaciones
      ? aniosElegibles(this.inicioPlan, this.selectedMonths).length === 0
      : this.annualContributionsCount <= 0;
  }

  get promocionInvalida(): boolean {
    if (this.isPromocionBonoLotes) {
      const plazoRequerido = this.promocionSeleccionada?.plazoFijo;
      return this.cantidadLotes < this.lotesMinimosPromocion || (!!plazoRequerido && this.selectedMonths !== plazoRequerido);
    }
    if (this.promocionSinAportacionesDisponibles) {
      return true;
    }
    // Con aportaciones por mes hace falta elegir al menos una y que todas tengan monto.
    return (
      this.promocionUsaAportaciones &&
      (this.aportaciones.length === 0 || this.aportaciones.some((a) => a.monto === null || a.monto <= 0))
    );
  }

  /** Promoción con aportaciones por mes (varias por año, cada una con su mes y monto) en lugar de
   * una sola aportación anual: las aportaciones no editadas a mano reparten igual lo que la
   * promoción exige (ver promocionAnnualContributionEstandar). */
  promocionUsaAportaciones = false;

  /** Cuántas aportaciones lleva la promoción: las elegidas mes por mes, o las anuales de siempre. */
  get promocionContribucionesCount(): number {
    return this.promocionUsaAportaciones ? this.aportaciones.length : this.annualContributionsCount;
  }

  /** Monto igual para repartir entre las aportaciones de la promoción (editable en cada una). */
  get promocionMontoSugerido(): number {
    return Math.round(this.promocionAnnualContribution * 100) / 100;
  }

  cambiarPromocionUsaAportaciones(valor: boolean): void {
    this.promocionUsaAportaciones = valor;
    this.aportaciones = [];
  }

  /** Precio total del lote estándar (200 m²) al precio por m² vigente: la mensualidad fija de la
   * promoción está calibrada para este tamaño. Cualquier lote distinto (más grande o más chico)
   * se reparte proporcionalmente entre mensualidad y aportación anual, ver `promocionFactorAjuste`. */
  get promocionTotalEstandar(): number {
    const totalEstandar = SUPERFICIE_ESTANDAR_M2 * this.pricePerM2;

    if (this.selectedProject === 'nanuu') {
      return totalEstandar * (1 + this.interestPercentage / 100);
    }

    return totalEstandar;
  }

  /** Cuánto crece o baja el total a pagar respecto del lote estándar: 1 = mismo tamaño,
   * mayor a 1 = lote más grande, menor a 1 = lote más chico. Mensualidad y aportación anual se
   * escalan por este mismo factor, así que ambas suben o bajan juntas y de forma proporcional. */
  get promocionFactorAjuste(): number {
    if (this.promocionTotalEstandar <= 0) {
      return 1;
    }

    return this.totalInvestment / this.promocionTotalEstandar;
  }

  /** Aportación anual que le correspondería al lote estándar, dada la mensualidad fija de la
   * promoción: mismo cálculo que `annualitiesMonthlyPayment` pero despejando la aportación en
   * vez de la mensualidad, y sobre el total del lote estándar (no el real). */
  get promocionAnnualContributionEstandar(): number {
    if (!this.promocionSeleccionada || this.promocionContribucionesCount <= 0) {
      return 0;
    }

    const mensualidadFija = this.promocionSeleccionada.mensualidadFija ?? 0;
    const mesesRegulares = this.selectedMonths - this.promocionContribucionesCount;
    const cubiertoPorMensualidades = mensualidadFija * mesesRegulares;
    const requerido = (this.promocionTotalEstandar - cubiertoPorMensualidades) / this.promocionContribucionesCount;

    return Math.max(requerido, 0);
  }

  /** Aportación anual real: la del lote estándar escalada por el factor de ajuste, para que un
   * lote más grande o más chico la mueva en la misma proporción que la mensualidad. */
  get promocionAnnualContribution(): number {
    return this.promocionAnnualContributionEstandar * this.promocionFactorAjuste;
  }

  /** Monto de entrada: el enganche o el pago inicial capturado (igual para ambos proyectos); 0 sin entrada. */
  get downPayment(): number {
    if (this.conPagoInicial) {
      return this.initialPayment;
    }

    if (this.conEnganche) {
      return this.downPaymentAmount;
    }

    return 0;
  }

  /** Saldo después de la entrada: lo que se reparte entre mensualidades y, si hay, anualidades o aportaciones.
   * En Nanuu incluye los intereses (si el interruptor está activado). */
  get financedAmount(): number {
    // Promoción: el total (con intereses de Nanuu incluidos, si aplica) se reparte entre
    // mensualidades fijas y aportaciones anuales, igual que "Con anualidades".
    if (this.isPromocion) {
      return this.totalInvestment;
    }

    // Contado: se paga todo de una vez, no queda nada que financiar (en ambos proyectos).
    if (this.esContado) {
      return 0;
    }

    return this.totalInvestment - this.downPayment;
  }

  get financingMonths(): number {
    if (!this.hayFormaPago || this.esContado) {
      return 0;
    }

    return this.selectedMonths;
  }

  get monthlyPayment(): number {
    if (this.financingMonths <= 0) {
      return 0;
    }

    if (this.isPromocionBonoLotes) {
      return this.financedAmount / this.selectedMonths;
    }

    // PROMOCIÓN: la mensualidad fija de la promoción se escala por el mismo factor que la
    // aportación anual, para que ambas suban o bajen juntas cuando el lote no es el estándar.
    if (this.isPromocion) {
      return (this.promocionSeleccionada?.mensualidadFija ?? 0) * this.promocionFactorAjuste;
    }

    if (this.isAnnualities) {
      return this.annualitiesMonthlyPayment;
    }

    if (this.isAportaciones) {
      return this.aportacionesMonthlyPayment;
    }

    return this.financedAmount / this.selectedMonths;
  }

  get paymentMethodLabel(): string {
    if (this.isPromocion) {
      return `Promoción "${this.promocionSeleccionada?.nombre}" · ${this.selectedMonths} meses`;
    }

    if (!this.hayFormaPago) {
      return 'Sin elegir';
    }

    // Nanuu siempre lleva intereses (no son MSI): sin anualidades ni aportaciones se nombra solo por el plazo.
    if (this.selectedProject === 'nanuu' && !this.formaPago.extra && !this.esContado) {
      return `${this.selectedMonths} meses`;
    }

    return etiquetaFormaPago(this.formaPago, this.selectedMonths);
  }

  get displayedLandPrice(): number {
    if (this.esContado) {
      return this.cashPrice;
    }

    return this.totalPrice;
  }

  async generatePdf(): Promise<void> {
    this.showQuoteErrors = true;

    if (!this.isQuoteValid) {
      this.toast.aviso('Completa los datos de la cotización antes de generar el PDF.');
      return;
    }

    const data = this.getQuotePdfData();
    await this.pdfService.downloadQuotePdf(data);
    await this.registrarHistorialCotizacion(data);
  }

  /** Bitácora para el admin: cada PDF generado o compartido queda registrado (en /cotizador-publico,
   * sin sesión, atribuido al usuario de sistema — ver CotizacionesService.registrarPublica). No debe
   * bloquear ni fallar visiblemente la descarga/envío del PDF, que para el asesor ya se completó. */
  private async registrarHistorialCotizacion(data: QuotePdfData): Promise<void> {
    const request = solicitudDeHistorial(data, this.esPublico, this.isAnnualities);

    try {
      if (this.esPublico) {
        await this.cotizacionesService.registrarPublica(request);
      } else {
        await this.cotizacionesService.registrar(request);
      }
    } catch (error) {
      console.error('No se pudo registrar la cotización en el historial:', error);
    }
  }

  get amortizationTable(): AmortizationRow[] {
    // ==============================
    // SAMAI - ANUALIDADES
    // ==============================

    if (this.isAnnualities) {
      return this.tablaConAnualidad(
        this.financedAmount,
        this.annualContribution,
        this.annualitiesMonthlyPayment,
        this.llevaEntrada ? 1 : 0,
      );
    }

    // ==============================
    // SAMAI - APORTACIONES
    // ==============================

    if (this.isAportaciones) {
      if (this.aportacionesInvalid) {
        return [];
      }

      return tablaConAportaciones(
        this.inicioPlan,
        this.selectedMonths,
        this.financedAmount,
        this.aportacionesMonthlyPayment,
        this.aportaciones,
        MESES_MAYUSCULAS,
      );
    }

    // ==============================
    // PROMOCIÓN
    // ==============================

    if (this.isPromocion) {
      if (this.promocionInvalida) {
        return [];
      }

      if (this.isPromocionBonoLotes) {
        return tablaFinanciamientoNormal(
          this.currentDate,
          this.financingMonths,
          this.financedAmount,
          this.monthlyPayment,
          false,
        );
      }

      if (this.promocionUsaAportaciones) {
        return tablaConAportaciones(
          this.inicioPlan,
          this.selectedMonths,
          this.totalInvestment,
          this.monthlyPayment,
          this.aportaciones,
          MESES_MAYUSCULAS,
        );
      }

      return this.tablaConAnualidad(
        this.totalInvestment,
        this.promocionAnnualContribution,
        this.monthlyPayment,
      );
    }

    // ==============================
    // CONTADO
    // ==============================

    if (!this.hayFormaPago || this.esContado) {
      return [];
    }

    return tablaFinanciamientoNormal(
      this.currentDate,
      this.financingMonths,
      this.financedAmount,
      this.monthlyPayment,
      this.llevaEntrada,
    );
  }

  /** "Mensualidad + aportación anual" (Con anualidades y Promoción) con el mes y el número de anualidades
   * elegidos; `mesesDespues` = 1 cuando hay enganche o pago inicial. */
  private tablaConAnualidad(total: number, anualidad: number, mensualidad: number, mesesDespues = 0): AmortizationRow[] {
    return tablaConAnualidad({
      hoy: this.currentDate,
      meses: this.selectedMonths,
      total,
      anualidad,
      mensualidad,
      mesAnualidad: this.annualContributionMonth,
      numeroAnualidades: this.annualContributionsCount,
      mesesDespues,
    });
  }

  private getQuotePdfData(): QuotePdfData {
    const lotes = this.todosLosLotes;

    return {
      project: PROYECTOS_COTIZADOR[this.selectedProject].nombre,

      location: PROYECTOS_COTIZADOR[this.selectedProject].ubicacion,

      advisorName: this.advisorName,
      clientName: this.clientName,
      // Con varios lotes, manzana/lote pasan a ser la lista completa (ej. "5, 5, 6"): el
      // desglose por lote va en la tabla de "lotes" del PDF y en la lista `lotes` de abajo.
      blockNumber: this.esMultiLote ? lotes.map((l) => l.manzana || '—').join(', ') : this.blockNumber,
      lotNumber: this.esMultiLote ? lotes.map((l) => l.lote || '—').join(', ') : this.lotNumber,
      lotes,

      area: this.superficieTotal,
      pricePerM2: this.esMultiLote ? this.precioM2Promedio : this.precioM2Vigente,

      totalPrice: this.displayedLandPrice,

      paymentMethod: this.paymentMethodLabel,

      descuentoContado: this.cashDiscountApplied,

      bonoPromocion: this.bonoPromocion,

      downPaymentLabel: this.isPromocionBonoLotes
        ? 'Enganche'
        : this.isPromocion
          ? this.promocionUsaAportaciones
            ? 'Total en aportaciones'
            : 'Aportación anual'
          : this.etiquetaEntrada,

      downPayment: this.isPromocionBonoLotes
        ? 0
        : this.isPromocion
          ? this.promocionUsaAportaciones
            ? this.aportacionesTotal
            : this.promocionAnnualContribution
          : this.downPayment,

      pagoExtra: this.pagoExtraParaPdf,

      financedAmount: this.financedAmount,

      months: this.financingMonths,

      monthlyPayment: this.monthlyPayment,

      date: this.currentDate.toLocaleDateString('es-MX'),

      amortizationTable: this.amortizationTable,

      aportaciones: this.aportacionesParaPdf,

      interestPercentage: this.interestPercentage,
      interestAmount: this.interestAmount,
      totalInvestment: this.totalInvestment,
    };
  }

  /** Anualidades o aportaciones de la forma de pago, como un renglón aparte del enganche en el PDF y el
   * historial (la promoción las lleva en su propio renglón, ver downPaymentLabel). */
  private get pagoExtraParaPdf(): QuotePdfData['pagoExtra'] {
    if (this.isAnnualities) {
      const mes = this.mesesDelAnio.find((m) => m.value === this.annualContributionMonth)?.label ?? '';
      return {
        label: `Anualidades (${this.annualContributionsCount} en ${mes.toLowerCase()})`,
        monto: this.annualContributionsTotal,
      };
    }
    if (this.isAportaciones) {
      return { label: `Aportaciones (${this.aportaciones.length})`, monto: this.aportacionesTotal };
    }
    return undefined;
  }

  async sharePdf(): Promise<void> {
    this.showQuoteErrors = true;

    if (!this.isQuoteValid) {
      this.toast.aviso('Completa los datos de la cotización antes de generar el PDF.');
      return;
    }

    try {
      const data = this.getQuotePdfData();

      const pdfFile = await this.pdfService.createQuoteFile(data);

      // Verificamos si el navegador permite compartir archivos
      if (!navigator.share || !navigator.canShare || !navigator.canShare({ files: [pdfFile] })) {
        this.toast.aviso('Este dispositivo no permite compartir el PDF directamente. Puedes descargarlo y enviarlo manualmente por WhatsApp.');
        return;
      }

      await navigator.share({
        title: `Cotización ${data.project}`,
        text: `Te comparto la cotización de ${data.project}.`,
        files: [pdfFile],
      });

      await this.registrarHistorialCotizacion(data);
    } catch (error) {
      // Si el usuario cerró el menú de compartir, no es realmente un error
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }

      console.error('Error al compartir PDF:', error);
    }
  }

  get nanuuFinancing() {
    if (this.selectedProject !== 'nanuu') {
      return null;
    }

    return PROJECTS_CONFIG.nanuu.financingPlans.find((plan) => plan.months === this.selectedMonths) ?? null;
  }

  get interestPercentage(): number {
    if (this.selectedProject === 'samai' || !this.interesesActivados() || this.esContado) {
      return 0;
    }

    return this.nanuuFinancing?.interestPercentage ?? 0;
  }

  get nanuuDownPaymentPercentage(): number {
    if (this.selectedProject !== 'nanuu') {
      return 0;
    }

    return this.nanuuFinancing?.downPaymentPercentage ?? 0;
  }

  /** Precio de los lotes después de descontar el bono promocional (si aplica). */
  get precioDespuesDeBono(): number {
    return Math.max(this.totalPrice - this.bonoPromocion, 0);
  }

  get interestAmount(): number {
    if (this.selectedProject !== 'nanuu') {
      return 0;
    }

    return this.precioDespuesDeBono * (this.interestPercentage / 100);
  }

  get financedTotalPrice(): number {
    if (this.selectedProject === 'nanuu') {
      return this.precioDespuesDeBono + this.interestAmount;
    }

    return this.precioDespuesDeBono;
  }

  get totalInvestment(): number {
    if (this.selectedProject === 'nanuu') {
      return this.precioDespuesDeBono + this.interestAmount;
    }

    if (this.esContado) {
      return this.cashPrice;
    }

    return this.precioDespuesDeBono;
  }

  get currentProjectName(): string {
    return PROYECTOS_COTIZADOR[this.selectedProject].portada;
  }

  get currentProjectLocation(): string {
    return PROYECTOS_COTIZADOR[this.selectedProject].ubicacion;
  }

  get currentProjectImage(): string {
    return PROYECTOS_COTIZADOR[this.selectedProject].imagen;
  }

  /** Con un solo lote, Manzana/Lote son obligatorios como siempre. Con varios lotes ya agregados,
   * solo hacen falta si el usuario empezó a llenarlos (ver requiereLoteEnCaptura) — dejarlos
   * vacíos significa que ya terminó y la cotización se genera solo con los lotes de la lista. */
  get isQuoteDataComplete(): boolean {
    if (!this.advisorName.trim() || !this.clientName.trim()) {
      return false;
    }
    return !this.requiereLoteEnCaptura || (this.blockNumber.trim().length > 0 && this.lotNumber.trim().length > 0);
  }

  /** Qué le falta a la cotización para poder generar o compartir el PDF; null si ya está lista. Se muestra
   * bajo esos botones para que no queden apagados sin explicación. */
  get motivoCotizacionIncompleta(): string | null {
    if (this.isQuoteValid) return null;
    const faltan: string[] = [];
    if (!this.advisorName.trim()) faltan.push('el nombre del asesor');
    if (!this.clientName.trim()) faltan.push('el nombre del cliente');
    if (this.requiereLoteEnCaptura && !this.blockNumber.trim()) faltan.push('la manzana');
    if (this.requiereLoteEnCaptura && !this.lotNumber.trim()) faltan.push('el lote');
    if (!this.hayFormaPago) faltan.push('la forma de pago');
    if (faltan.length === 0) {
      if (this.isPromocionBonoLotes && this.cantidadLotes < this.lotesMinimosPromocion) {
        return 'Esta promoción requiere agregar al menos 2 lotes a la cotización.';
      }
      if (this.isPromocionBonoLotes && this.promocionSeleccionada?.plazoFijo && this.selectedMonths !== this.promocionSeleccionada.plazoFijo) {
        return `Esta promoción aplica únicamente a ${this.promocionSeleccionada.plazoFijo} meses.`;
      }
      return 'Revisa los montos de la forma de pago para generar el PDF.';
    }
    return `Para generar el PDF falta ${enumerar(faltan)}.`;
  }

  get advisorNameInvalid(): boolean {
    return this.showQuoteErrors && !this.advisorName.trim();
  }

  get clientNameInvalid(): boolean {
    return this.showQuoteErrors && !this.clientName.trim();
  }

  get blockNumberInvalid(): boolean {
    return this.showQuoteErrors && this.requiereLoteEnCaptura && !this.blockNumber.trim();
  }

  get lotNumberInvalid(): boolean {
    return this.showQuoteErrors && this.requiereLoteEnCaptura && !this.lotNumber.trim();
  }

  // ==============================
  // APORTACIONES - SAMAI
  // ==============================

  // Varias aportaciones por año, cada una con su mes y su monto (a diferencia de las anualidades:
  // una por año, mismo mes y mismo monto). La lógica de cálculo vive en core/utils/aportaciones.ts.
  aportaciones: Aportacion[] = [];

  get isAportaciones(): boolean {
    return !this.isPromocion && this.formaPago.extra === 'aportaciones';
  }

  get aportacionesTotal(): number {
    return totalAportaciones(this.aportaciones);
  }

  get aportacionesMonthlyPayment(): number {
    return mensualidadConAportaciones(this.financedAmount, this.aportaciones, this.selectedMonths);
  }

  get aportacionesInvalid(): boolean {
    return this.isAportaciones && !aportacionesValidas(this.financedAmount, this.aportaciones, this.selectedMonths);
  }

  /** Mensaje de por qué las aportaciones no son válidas (null si lo son o si todavía no hay ninguna). */
  get aportacionesError(): string | null {
    if (!this.isAportaciones || this.aportaciones.length === 0) {
      return null;
    }
    if (this.aportaciones.some((a) => a.monto === null || a.monto <= 0)) {
      return 'Ingresa el monto de todas las aportaciones elegidas.';
    }
    if (this.aportacionesMonthlyPayment <= 0) {
      return 'Las aportaciones cubren todo el saldo: deja algo para las mensualidades regulares.';
    }
    return null;
  }

  /** Aportaciones ordenadas para el PDF, con el nombre del mes (null si no es el esquema activo). */
  private get aportacionesParaPdf(): { mes: string; anio: number; monto: number }[] | undefined {
    const usan = this.isAportaciones || (this.isPromocion && this.promocionUsaAportaciones);
    if (!usan || this.aportaciones.length === 0) {
      return undefined;
    }
    return ordenarAportaciones(this.aportaciones).map((a) => ({
      mes: MESES_NOMBRE[a.mes - 1],
      anio: a.anio,
      monto: a.monto ?? 0,
    }));
  }

  // ==============================
  // ANUALIDADES - SAMAI
  // ==============================

  // Monto que el asesor decide para cada anualidad
  annualContribution: number = 0;

  // Valor visual con comas
  annualContributionDisplay: string = '';

  // Mes elegido para realizar la anualidad
  // 1 = Enero ... 12 = Diciembre
  annualContributionMonth: number = 12;

  // Reutilizado por el selector de "mes de la aportación" tanto en Anualidades como en Promoción.
  readonly mesesDelAnio = MESES_DEL_ANIO;

  // El asesor puede editar el número de aportaciones (compartido entre "Con anualidades" y
  // "Promoción", igual que antes de ser editable). null = todavía no lo ha tocado, así que
  // sigue el cálculo automático según el plazo.
  private annualContributionsCountOverride: number | null = null;

  /** Tope de aportaciones según el plazo actual: un año completo de mensualidades regulares
   * antes de terminar de pagar. Es el mismo valor que antes se calculaba siempre, ahora es
   * también el máximo que el asesor puede capturar a mano. */
  get annualContributionsCountMax(): number {
    return maximoAnualidades(this.selectedMonths);
  }

  get annualContributionsCount(): number {
    if (this.annualContributionsCountOverride === null) {
      return this.annualContributionsCountMax;
    }
    return Math.min(Math.max(this.annualContributionsCountOverride, 0), this.annualContributionsCountMax);
  }

  set annualContributionsCount(value: number) {
    this.annualContributionsCountOverride = Number.isFinite(value) ? Math.trunc(value) : 0;
  }

  get annualitiesAvailable(): boolean {
    return this.selectedMonths > 12;
  }

  get isAnnualities(): boolean {
    return !this.isPromocion && this.formaPago.extra === 'annualities';
  }

  onAnnualContributionInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const limpio = limpiarNumero(input.value, 2);

    this.annualContribution = numeroDeTexto(limpio);
    this.annualContributionDisplay = formatearNumero(limpio, 2);
    input.value = this.annualContributionDisplay;
  }

  get annualContributionsTotal(): number {
    return this.annualContribution * this.annualContributionsCount;
  }

  get annualitiesMonthlyBalance(): number {
    return Math.max(this.financedAmount - this.annualContributionsTotal, 0);
  }

  get regularPaymentMonths(): number {
    return this.selectedMonths - this.annualContributionsCount;
  }

  get annualitiesMonthlyPayment(): number {
    if (this.annualContributionInvalid) {
      return this.baseMonthlyPayment;
    }

    if (!this.isAnnualities || this.selectedMonths <= 0) {
      return 0;
    }

    // Si todavía no hay anualidad
    if (this.annualContribution <= 0) {
      return this.financedAmount / this.selectedMonths;
    }

    // Total que se pagará mediante anualidades
    const annualitiesTotal = this.annualContribution * this.annualContributionsCount;

    // Lo que todavía falta cubrir con mensualidades (el saldo ya sin enganche ni pago inicial)
    const remainingBalance = this.financedAmount - annualitiesTotal;

    // Meses normales restantes
    const regularMonths = this.selectedMonths - this.annualContributionsCount;

    if (remainingBalance <= 0 || regularMonths <= 0) {
      return 0;
    }

    return remainingBalance / regularMonths;
  }

  get baseMonthlyPayment(): number {
    if (this.selectedMonths <= 0) {
      return 0;
    }

    return this.financedAmount / this.selectedMonths;
  }

  get annualContributionInvalid(): boolean {
    if (!this.isAnnualities) {
      return false;
    }

    if (this.annualContribution <= 0) {
      return true;
    }

    if (this.annualContribution < this.minimumAnnualContribution) {
      return true;
    }

    if (this.annualContribution > this.maximumAnnualContribution) {
      return true;
    }

    return false;
  }

  get minimumAnnualContribution(): number {
    return Math.ceil(this.baseMonthlyPayment);
  }

  get maximumAnnualContribution(): number {
    if (this.annualContributionsCount <= 0) {
      return 0;
    }

    return Math.floor((this.financedAmount - 1) / this.annualContributionsCount);
  }

  get annualContributionValid(): boolean {
    return this.isAnnualities && !this.annualContributionInvalid;
  }

  /** Lo que muestra la tarjeta "Resumen de cotización" (ver CotizadorResumenComponent). */
  get resumen(): ResumenCotizacion {
    return {
      currentProjectImage: this.currentProjectImage,
      currentProjectName: this.currentProjectName,
      currentProjectLocation: this.currentProjectLocation,
      esMultiLote: this.esMultiLote,
      superficieTotal: this.superficieTotal,
      cantidadLotes: this.cantidadLotes,
      cashDiscountApplied: this.cashDiscountApplied,
      precioM2Promedio: this.precioM2Promedio,
      displayedLandPrice: this.displayedLandPrice,
      paymentMethodLabel: this.paymentMethodLabel,
      isPromocion: this.isPromocion,
      isPromocionBonoLotes: this.isPromocionBonoLotes,
      bonoPromocion: this.bonoPromocion,
      promocionUsaAportaciones: this.promocionUsaAportaciones,
      aportacionesTotal: this.aportacionesTotal,
      promocionAnnualContribution: this.promocionAnnualContribution,
      promocionContribucionesCount: this.promocionContribucionesCount,
      llevaEntrada: this.llevaEntrada,
      extra: this.formaPago.extra,
      etiquetaEntrada: this.etiquetaEntrada,
      downPayment: this.downPayment,
      isAnnualities: this.isAnnualities,
      annualContributionsCount: this.annualContributionsCount,
      annualContributionsTotal: this.annualContributionsTotal,
      isAportaciones: this.isAportaciones,
      numeroAportaciones: this.aportaciones.length,
      financedAmount: this.financedAmount,
      hayFormaPago: this.hayFormaPago,
      esContado: this.esContado,
      totalInvestment: this.totalInvestment,
      monthlyPayment: this.monthlyPayment,
      financingMonths: this.financingMonths,
      interestAmount: this.interestAmount,
      isQuoteValid: this.isQuoteValid,
      motivoCotizacionIncompleta: this.motivoCotizacionIncompleta,
    };
  }

  get isQuoteValid(): boolean {
    if (!this.isQuoteDataComplete) {
      return false;
    }

    if (!this.hayFormaPago) {
      return false;
    }

    if (this.isAnnualities && this.annualContributionInvalid) {
      return false;
    }

    if (this.aportacionesInvalid) {
      return false;
    }

    if (this.initialPaymentInvalid || this.downPaymentAmountInvalid) {
      return false;
    }

    if (this.isPromocion && this.promocionInvalida) {
      return false;
    }

    return true;
  }
}
