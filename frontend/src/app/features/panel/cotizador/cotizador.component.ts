import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { PdfService, QuotePdfData } from '../../../core/services/pdf-cotizacion.service';
import { CotizacionesService } from '../../../core/services/cotizaciones.service';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { PromocionesService } from '../../../core/services/promociones.service';
import { Desarrollo } from '../../../core/models/lead.model';
import { Lote } from '../../../core/models/lote.model';
import { Promocion, TipoPrecioPromocion } from '../../../core/models/promocion.model';
import { PROJECTS_CONFIG } from '../../../core/data/proyectos-cotizador.config';
import { FadeInDirective } from '../../../shared/motion/fade-in.directive';
import { PressDirective } from '../../../shared/motion/press.directive';
import { ValuePulseDirective } from '../../../shared/motion/value-pulse.directive';

export type ProjectId = 'samai' | 'nanuu';
export type PaymentType = 'msi' | 'downpayment' | 'annualities' | 'cash' | 'initial' | 'promocion';

/** A partir de esta superficie (m²), SAMAI cobra la tarifa de macrolote ($170/m², la misma tarifa
 * que antes solo se elegía a mano como "Hectarea") en vez del precio de lote normal ($800/m²). */
const SUPERFICIE_MACROLOTE_M2 = 8000;

/** Compara manzana/lote como números cuando se puede (así "2" queda antes que "10" en vez del
 * orden alfabético de texto); si alguno no es numérico, cae a orden alfabético normal. */
function compararNatural(a: string, b: string): number {
  const numeroA = Number(a);
  const numeroB = Number(b);
  if (!Number.isNaN(numeroA) && !Number.isNaN(numeroB)) return numeroA - numeroB;
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

@Component({
  selector: 'app-cotizador',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, FadeInDirective, PressDirective, ValuePulseDirective],
  templateUrl: './cotizador.component.html',
})
export class CotizadorComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly pdfService = inject(PdfService);
  private readonly cotizacionesService = inject(CotizacionesService);
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);
  private readonly promocionesService = inject(PromocionesService);
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
    }
  }

  /** 'SAMAI Campestre' / 'Aldea Nanuu': mismo mapeo que ya se usa para armar el PDF de la cotización. */
  private nombreDesarrolloActual(): string {
    return this.selectedProject === 'samai' ? 'SAMAI Campestre' : 'Aldea Nanuu';
  }

  /** Lotes disponibles del desarrollo actual: alimentan las sugerencias de manzana/lote de abajo.
   * En /cotizador-publico no hay sesión, así que usa el mismo endpoint público que alimenta
   * /cotizador-publico/lotes (que trae cualquier estado) y aquí se filtra a solo disponibles. */
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
      this.selectedProject === 'nanuu' ? 3700 : lote.superficie > SUPERFICIE_MACROLOTE_M2 ? 170 : 800;
    this.loteBloqueado = true;
    this.revisarPromocionCompatible();
  }

  /** Al editar manzana o lote a mano, se desbloquea: si el nuevo valor vuelve a coincidir con un
   * lote real, onLoteNumberChange() lo vuelve a bloquear al confirmarlo. */
  desbloquearLote(): void {
    this.loteBloqueado = false;
  }

  currentDate = new Date();

  // Superficie seleccionada
  selectedArea: number = 200;

  // Indica si el usuario eligió superficie personalizada
  isCustomArea: boolean = false;

  // Precio actual por m²
  pricePerM2: number = 800;

  customAreaDisplay = '200';

  selectedPaymentType: PaymentType = 'msi';

  // Enganche de SAMAI: monto libre en pesos (Nanuu conserva su 20% fijo, ver downPayment()).
  downPaymentAmount: number = 0;
  downPaymentAmountDisplay = '';

  // Pago inicial: monto fijo en pesos (a diferencia del enganche, que es un %).
  initialPayment: number = 0;
  initialPaymentDisplay = '';

  selectedMonths: number = 60;

  advisorName: string = this.auth.currentUser()?.nombre ?? '';

  clientName: string = '';

  blockNumber: string = '';
  lotNumber: string = '';

  // true cuando manzana/lote coinciden con un lote real del inventario: mientras esté así, precio
  // por m² y superficie quedan bloqueados porque ya vienen del lote, no se capturan a mano.
  loteBloqueado = false;

  selectProject(project: ProjectId): void {
    this.promocionSeleccionada = null;
    this.selectedProject = project;
    this.loteBloqueado = false;

    if (project === 'samai') {
      this.pricePerM2 = 800;
      this.selectedArea = 200;
      this.selectedMonths = 60;
    }

    if (project === 'nanuu') {
      this.pricePerM2 = 3700;
      this.selectedArea = 200;
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
      this.pricePerM2 = 170;
    }
  }

  selectCustomArea(): void {
    this.isCustomArea = true;
    this.customAreaDisplay = this.selectedArea.toLocaleString('en-US');
  }

  selectPricePerM2(price: number): void {
    this.pricePerM2 = price;

    // Si seleccionamos precio de macrolote
    if (price === 170) {
      this.isCustomArea = true;
      this.selectedArea = 0;
      this.customAreaDisplay = '0';
      this.selectedPaymentType = 'msi';
    }

    // Si regresamos al precio normal
    if (price === 800) {
      this.isCustomArea = false;
      this.selectedArea = 200;
      this.customAreaDisplay = '200';
    }

    this.revisarPromocionCompatible();
  }

  onCustomAreaInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    // Quitamos comas para trabajar con el valor real
    let rawValue = input.value.replace(/,/g, '');

    // Permitimos solo números y un punto decimal
    rawValue = rawValue.replace(/[^0-9.]/g, '');

    // Evitamos más de un punto
    const parts = rawValue.split('.');

    if (parts.length > 2) {
      rawValue = parts[0] + '.' + parts.slice(1).join('');
    }

    // Máximo 2 decimales
    if (rawValue.includes('.')) {
      const [integerPart, decimalPart] = rawValue.split('.');

      rawValue = integerPart + '.' + decimalPart.slice(0, 2);
    }

    if (!rawValue) {
      this.selectedArea = 0;
      this.customAreaDisplay = '';
      return;
    }

    // Valor REAL para los cálculos
    this.selectedArea = Number(rawValue);
    this.aplicarPrecioMacrolote();
    this.revisarPromocionCompatible();

    // Valor VISUAL con comas y decimales
    const [integerPart, decimalPart] = rawValue.split('.');

    const formattedInteger = Number(integerPart || 0).toLocaleString('en-US');

    this.customAreaDisplay = decimalPart !== undefined ? `${formattedInteger}.${decimalPart}` : formattedInteger;
  }

  onInitialPaymentInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const rawValue = input.value.replace(/\D/g, '');

    if (!rawValue) {
      this.initialPayment = 0;
      this.initialPaymentDisplay = '';
      return;
    }

    this.initialPayment = Number(rawValue);

    this.initialPaymentDisplay = this.initialPayment.toLocaleString('en-US');
  }

  get initialPaymentInvalid(): boolean {
    if (this.selectedPaymentType !== 'initial') {
      return false;
    }

    if (this.initialPayment <= 0) {
      return true;
    }

    return this.initialPayment >= this.totalPrice;
  }

  onDownPaymentAmountInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const rawValue = input.value.replace(/\D/g, '');

    if (!rawValue) {
      this.downPaymentAmount = 0;
      this.downPaymentAmountDisplay = '';
      return;
    }

    this.downPaymentAmount = Number(rawValue);

    this.downPaymentAmountDisplay = this.downPaymentAmount.toLocaleString('en-US');
  }

  get downPaymentAmountInvalid(): boolean {
    if (this.selectedProject !== 'samai' || this.selectedPaymentType !== 'downpayment') {
      return false;
    }

    if (this.downPaymentAmount <= 0) {
      return true;
    }

    return this.downPaymentAmount >= this.totalPrice;
  }

  get totalPrice(): number {
    return this.selectedArea * this.pricePerM2;
  }

  /** El precio de contado con descuento ($110,000 fijo) es una promoción exclusiva del lote
   * estándar de SAMAI de 200 m² exactos, no se prorratea a otros tamaños (antes sí se escalaba
   * proporcionalmente, pero ese precio especial nunca aplicó más que a ese tamaño puntual). Para
   * cualquier otro tamaño en SAMAI, o para Nanuu, "Contado" cobra el precio de lista sin descuento. */
  get cashPrice(): number {
    if (this.selectedProject === 'samai' && this.selectedArea === 200) {
      return 110000;
    }

    return this.totalPrice;
  }

  /** Si "Contado" está seleccionado pero el lote no es el estándar de 200 m² de SAMAI, no hay
   * descuento real: el resumen debe decirlo (ver cashPrice) en vez de mostrar "Precio especial". */
  get cashDiscountApplied(): boolean {
    return this.selectedPaymentType === 'cash' && this.selectedProject === 'samai' && this.selectedArea === 200;
  }

  selectPaymentType(type: PaymentType): void {
    if (type === 'annualities' && !this.annualitiesAvailable) {
      return;
    }

    this.selectedPaymentType = type;
  }

  /** Aplica una promoción: cambia al proyecto al que pertenece y activa el modo "promocion".
   * selectProject() deja la tarifa en la normal (Lotes) por defecto; una promoción de Hectáreas
   * necesita quedar en tarifa de macrolote para no contradecirse con la tarifa que exige. */
  seleccionarPromocion(promo: Promocion): void {
    this.selectProject(promo.proyecto);
    if (promo.tipoPrecio === 'HECTAREA') {
      this.selectPricePerM2(170);
      // selectPricePerM2(170) fuerza selectedPaymentType a 'msi'; lo volvemos a poner en 'promocion'
      // un par de líneas abajo, junto con el resto de las demás promociones.
    }
    this.promocionSeleccionada = promo;
    this.selectedPaymentType = 'promocion';
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
    this.selectedPaymentType = 'msi';
    this.mostrarSelectorPromociones.set(false);
  }

  get isPromocion(): boolean {
    return this.selectedPaymentType === 'promocion' && this.promocionSeleccionada !== null;
  }

  /** LOTE (precio normal por m²) o HECTAREA (tarifa de macrolote): solo SAMAI llega a HECTAREA,
   * cuando el precio por m² vigente ya es el de macrolote (ver aplicarPrecioMacrolote/selectPricePerM2). */
  get tipoPrecioActual(): TipoPrecioPromocion {
    return this.selectedProject === 'samai' && this.pricePerM2 === 170 ? 'HECTAREA' : 'LOTE';
  }

  /** Promociones activas que de verdad aplican a la cotización actual: una promoción de Hectáreas
   * solo se ofrece mientras la tarifa de macrolote está vigente, y una de Lotes solo mientras no lo
   * está. No cambia la superficie/tarifa por sí sola, solo filtra qué se puede elegir. */
  get promocionesElegibles(): Promocion[] {
    return this.promocionesActivas().filter((promo) => promo.tipoPrecio === this.tipoPrecioActual);
  }

  /** Si un cambio de superficie o precio por m² deja de calificar para la tarifa de la promoción ya
   * aplicada, se quita sola: seguir mostrándola sería incoherente con el nuevo precio. */
  private revisarPromocionCompatible(): void {
    if (this.promocionSeleccionada && this.promocionSeleccionada.tipoPrecio !== this.tipoPrecioActual) {
      this.promocionSeleccionada = null;
      if (this.selectedPaymentType === 'promocion') {
        this.selectedPaymentType = 'msi';
      }
    }
  }

  /** Si el plazo no da para ninguna aportación anual, no hay forma de aplicar la promoción sin
   * importar la superficie del lote — por eso oculta el panel entero (selector de mes, resumen)
   * y solo muestra el aviso de que hace falta un plazo mayor. */
  get promocionSinAportacionesDisponibles(): boolean {
    return this.isPromocion && this.annualContributionsCount <= 0;
  }

  get promocionInvalida(): boolean {
    return this.promocionSinAportacionesDisponibles;
  }

  /** Precio total del lote estándar (200 m²) al precio por m² vigente: la mensualidad fija de la
   * promoción está calibrada para este tamaño. Cualquier lote distinto (más grande o más chico)
   * se reparte proporcionalmente entre mensualidad y aportación anual, ver `promocionFactorAjuste`. */
  get promocionTotalEstandar(): number {
    const totalEstandar = 200 * this.pricePerM2;

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
    if (!this.promocionSeleccionada || this.annualContributionsCount <= 0) {
      return 0;
    }

    const mensualidadFija = this.promocionSeleccionada.mensualidadFija;
    const cubiertoPorMensualidades = mensualidadFija * this.regularPaymentMonths;
    const requerido = (this.promocionTotalEstandar - cubiertoPorMensualidades) / this.annualContributionsCount;

    return Math.max(requerido, 0);
  }

  /** Aportación anual real: la del lote estándar escalada por el factor de ajuste, para que un
   * lote más grande o más chico la mueva en la misma proporción que la mensualidad. */
  get promocionAnnualContribution(): number {
    return this.promocionAnnualContributionEstandar * this.promocionFactorAjuste;
  }

  get downPayment(): number {
    // Pago inicial: monto fijo capturado por el asesor, igual para ambos proyectos.
    if (this.selectedPaymentType === 'initial') {
      return this.initialPayment;
    }

    if (this.selectedProject === 'nanuu') {
      if (this.selectedPaymentType === 'downpayment') {
        return this.totalPrice * 0.2;
      }

      if (this.selectedPaymentType === 'msi') {
        return 0;
      }
    }

    // SAMAI: monto de enganche libre, capturado directamente por el asesor.
    if (this.selectedPaymentType === 'downpayment') {
      return this.downPaymentAmount;
    }

    return 0;
  }

  get financedAmount(): number {
    // Promoción: el total (con intereses de Nanuu incluidos, si aplica) se reparte entre
    // mensualidades fijas y aportaciones anuales, igual que "Con anualidades".
    if (this.isPromocion) {
      return this.totalInvestment;
    }

    if (this.selectedProject === 'nanuu') {
      return this.totalInvestment - this.downPayment;
    }

    if (this.selectedPaymentType === 'msi') {
      return this.totalPrice;
    }

    if (this.selectedPaymentType === 'downpayment' || this.selectedPaymentType === 'initial') {
      return this.totalPrice - this.downPayment;
    }

    if (this.isAnnualities) {
      return this.totalPrice;
    }

    return 0;
  }

  get financingMonths(): number {
    if (this.selectedProject === 'nanuu') {
      return this.selectedMonths;
    }

    if (
      this.selectedPaymentType === 'msi' ||
      this.selectedPaymentType === 'downpayment' ||
      this.selectedPaymentType === 'annualities' ||
      this.selectedPaymentType === 'initial' ||
      this.selectedPaymentType === 'promocion'
    ) {
      return this.selectedMonths;
    }

    return 0;
  }
  get monthlyPayment(): number {
    if (this.financingMonths <= 0) {
      return 0;
    }

    // PROMOCIÓN: la mensualidad fija de la promoción se escala por el mismo factor que la
    // aportación anual, para que ambas suban o bajen juntas cuando el lote no es el estándar.
    if (this.isPromocion) {
      return (this.promocionSeleccionada?.mensualidadFija ?? 0) * this.promocionFactorAjuste;
    }

    // NANUU
    if (this.selectedProject === 'nanuu') {
      return this.financedAmount / this.selectedMonths;
    }

    if (this.selectedPaymentType === 'annualities') {
      return this.annualitiesMonthlyPayment;
    }

    // SAMAI
    if (
      this.selectedPaymentType === 'msi' ||
      this.selectedPaymentType === 'downpayment' ||
      this.selectedPaymentType === 'initial'
    ) {
      return this.financedAmount / this.selectedMonths;
    }

    return 0;
  }

  get paymentMethodLabel(): string {
    if (this.isPromocion) {
      return `Promoción "${this.promocionSeleccionada?.nombre}" · ${this.selectedMonths} meses`;
    }

    if (this.selectedProject === 'nanuu') {
      return `${this.selectedMonths} meses`;
    }

    if (this.selectedPaymentType === 'msi') {
      return `Sin enganche · ${this.selectedMonths} MSI`;
    }

    if (this.selectedPaymentType === 'downpayment') {
      return `Con enganche · ${this.selectedMonths} MSI`;
    }

    if (this.selectedPaymentType === 'annualities') {
      return `Con anualidades · ${this.selectedMonths} meses`;
    }

    if (this.selectedPaymentType === 'initial') {
      return `Pago inicial · ${this.selectedMonths} meses`;
    }

    if (this.selectedPaymentType === 'cash') {
      return 'Contado';
    }

    return '';
  }

  get totalToPay(): number {
    if (this.selectedPaymentType === 'cash') {
      return this.cashPrice;
    }

    if (this.selectedPaymentType === 'msi') {
      return this.totalPrice;
    }

    if (this.selectedPaymentType === 'downpayment' || this.selectedPaymentType === 'initial') {
      return this.downPayment + this.financedAmount;
    }

    return 0;
  }

  get displayedLandPrice(): number {
    if (this.selectedPaymentType === 'cash') {
      return this.cashPrice;
    }

    return this.totalPrice;
  }

  async generatePdf(): Promise<void> {
    this.showQuoteErrors = true;

    if (!this.isQuoteValid) {
      alert('Completa los datos de la cotización antes de generar el PDF.');
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
    const request = {
      proyecto: data.project,
      nombreCliente: data.clientName,
      // Solo lo exige/usa el backend en /cotizador-publico (ver CotizacionService.registrarPublica);
      // en el cotizador con sesión el asesor ya se resuelve del token, así que se manda null.
      nombreAsesorPublico: this.esPublico ? data.advisorName : null,
      manzana: data.blockNumber || null,
      lote: data.lotNumber || null,
      superficie: data.area,
      precioM2: data.pricePerM2,
      precioTotal: data.totalPrice,
      formaPago: data.paymentMethod,
      engancheLabel: data.downPaymentLabel,
      enganche: data.downPayment,
      montoFinanciado: data.financedAmount,
      meses: data.months,
      mensualidad: data.monthlyPayment,
      interesPorcentaje: data.interestPercentage,
      interesMonto: data.interestAmount,
      totalInversion: data.totalInvestment,
    };

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

  private readonly monthNames = [
    'ENERO',
    'FEBRERO',
    'MARZO',
    'ABRIL',
    'MAYO',
    'JUNIO',
    'JULIO',
    'AGOSTO',
    'SEPTIEMBRE',
    'OCTUBRE',
    'NOVIEMBRE',
    'DICIEMBRE',
  ];

  get amortizationTable(): AmortizationRow[] {
    // ==============================
    // SAMAI - ANUALIDADES
    // ==============================

    if (this.isAnnualities) {
      return this.buildAnnualContributionAmortizationTable(
        this.totalPrice,
        this.annualContribution,
        this.annualitiesMonthlyPayment,
      );
    }

    // ==============================
    // PROMOCIÓN
    // ==============================

    if (this.isPromocion) {
      if (this.promocionInvalida) {
        return [];
      }

      return this.buildAnnualContributionAmortizationTable(
        this.totalInvestment,
        this.promocionAnnualContribution,
        this.monthlyPayment,
      );
    }

    // ==============================
    // CONTADO
    // ==============================

    if (this.selectedPaymentType === 'cash') {
      return [];
    }

    // ==============================
    // FINANCIAMIENTO NORMAL
    // ==============================

    const rows: AmortizationRow[] = [];

    let balance = this.financedAmount;
    let accumulatedPayment = 0;

    const startDate = new Date(this.currentDate);

    for (let i = 1; i <= this.financingMonths; i++) {
      // El enganche y el pago inicial se cubren de entrada, aparte del calendario de
      // mensualidades: no cuentan como la primera mensualidad, por eso el conteo de meses
      // empieza después (offset i, no i - 1).
      const monthOffset =
        this.selectedPaymentType === 'downpayment' || this.selectedPaymentType === 'initial' ? i : i - 1;

      const paymentDate = new Date(startDate.getFullYear(), startDate.getMonth() + monthOffset, 1);

      const payment = i === this.financingMonths ? balance : this.monthlyPayment;

      balance -= payment;

      if (balance < 0.01) {
        balance = 0;
      }

      accumulatedPayment += payment;

      rows.push({
        paymentNumber: i,
        month: paymentDate.toLocaleString('es-MX', { month: 'long' }).toUpperCase(),
        year: paymentDate.getFullYear(),
        payment,
        balance: Math.max(balance, 0),
        accumulatedPayment,
      });
    }

    return rows;
  }

  /** Genera la tabla de amortización para cualquier esquema de "mensualidad + aportación
   * anual" (Con anualidades y Promoción comparten esta misma mecánica; solo cambia cuál de
   * las dos cantidades es fija y cuál se calcula). */
  private buildAnnualContributionAmortizationTable(
    totalAmount: number,
    annualContributionAmount: number,
    monthlyPaymentAmount: number,
  ): AmortizationRow[] {
    const rows: AmortizationRow[] = [];

    const startDate = new Date(this.currentDate);

    let balance = totalAmount;

    let accumulatedPayment = 0;

    let annualitiesUsed = 0;

    for (let i = 0; i < this.selectedMonths; i++) {
      // ==============================
      // FECHA DEL PAGO
      // ==============================

      const paymentDate = new Date(startDate.getFullYear(), startDate.getMonth() + i, 1);

      const monthNumber = paymentDate.getMonth() + 1;

      // ==============================
      // ¿ESTE MES ES ANUALIDAD?
      // ==============================

      const isAnnuality =
        monthNumber === this.annualContributionMonth && annualitiesUsed < this.annualContributionsCount;

      // ==============================
      // MONTO DEL PAGO
      // ==============================

      let payment = isAnnuality ? annualContributionAmount : monthlyPaymentAmount;

      // ==============================
      // ÚLTIMO PAGO
      // ==============================

      // Nunca permitimos pagar más que el saldo restante.

      if (payment > balance) {
        payment = balance;
      }

      // ==============================
      // ACTUALIZAR SALDO
      // ==============================

      balance -= payment;

      if (balance < 0.01) {
        balance = 0;
      }

      // ==============================
      // ACUMULADO
      // ==============================

      accumulatedPayment += payment;

      // ==============================
      // CONTAR ANUALIDAD UTILIZADA
      // ==============================

      if (isAnnuality) {
        annualitiesUsed++;
      }

      // ==============================
      // AGREGAR FILA
      // ==============================

      rows.push({
        paymentNumber: i + 1,
        month: this.monthNames[paymentDate.getMonth()],
        year: paymentDate.getFullYear(),
        payment,
        balance,
        accumulatedPayment,
      });

      // Si ya se liquidó, no seguimos generando filas.

      if (balance <= 0) {
        break;
      }
    }

    return rows;
  }

  private getQuotePdfData(): QuotePdfData {
    return {
      project: this.selectedProject === 'samai' ? 'SAMAI Campestre' : 'Aldea Nanuu',

      location:
        this.selectedProject === 'samai'
          ? 'San José La Ciénega, Pochutla, Oaxaca'
          : 'Cuatunalco, Huatulco, Oaxaca',

      advisorName: this.advisorName,
      clientName: this.clientName,
      blockNumber: this.blockNumber,
      lotNumber: this.lotNumber,

      area: this.selectedArea,
      pricePerM2: this.pricePerM2,

      totalPrice: this.displayedLandPrice,

      paymentMethod: this.paymentMethodLabel,

      downPaymentLabel: this.isPromocion
        ? 'Aportación anual'
        : this.selectedPaymentType === 'initial'
          ? 'Pago inicial'
          : 'Enganche',

      downPayment: this.isPromocion ? this.promocionAnnualContribution : this.downPayment,

      financedAmount: this.financedAmount,

      months: this.financingMonths,

      monthlyPayment: this.monthlyPayment,

      date: this.currentDate.toLocaleDateString('es-MX'),

      amortizationTable: this.amortizationTable,

      interestPercentage: this.interestPercentage,
      interestAmount: this.interestAmount,
      totalInvestment: this.totalInvestment,
    };
  }

  async sharePdf(): Promise<void> {
    this.showQuoteErrors = true;

    if (!this.isQuoteValid) {
      alert('Completa los datos de la cotización antes de generar el PDF.');
      return;
    }

    try {
      const data = this.getQuotePdfData();

      const pdfFile = await this.pdfService.createQuoteFile(data);

      // Verificamos si el navegador permite compartir archivos
      if (!navigator.share || !navigator.canShare || !navigator.canShare({ files: [pdfFile] })) {
        alert('Este dispositivo no permite compartir el PDF directamente. Puedes descargarlo y enviarlo manualmente por WhatsApp.');
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
    if (this.selectedProject === 'samai') {
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

  get interestAmount(): number {
    if (this.selectedProject !== 'nanuu') {
      return 0;
    }

    return this.totalPrice * (this.interestPercentage / 100);
  }

  get financedTotalPrice(): number {
    if (this.selectedProject === 'nanuu') {
      return this.totalPrice + this.interestAmount;
    }

    return this.totalPrice;
  }

  get totalInvestment(): number {
    if (this.selectedProject === 'nanuu') {
      return this.totalPrice + this.interestAmount;
    }

    if (this.selectedPaymentType === 'cash') {
      return this.cashPrice;
    }

    return this.totalPrice;
  }

  get currentProjectName(): string {
    return this.selectedProject === 'samai' ? 'SAMAI CAMPESTRE' : 'ALDEA NANUU';
  }

  get currentProjectLocation(): string {
    return this.selectedProject === 'samai'
      ? 'San José La Ciénega, Pochutla, Oaxaca'
      : 'Cuatunalco, Huatulco, Oaxaca';
  }

  get currentProjectImage(): string {
    return this.selectedProject === 'samai' ? '/images/samai-cover.png' : '/images/nanuu-cover.png';
  }

  get isQuoteDataComplete(): boolean {
    return (
      this.advisorName.trim().length > 0 &&
      this.clientName.trim().length > 0 &&
      this.blockNumber.trim().length > 0 &&
      this.lotNumber.trim().length > 0
    );
  }

  get advisorNameInvalid(): boolean {
    return this.showQuoteErrors && !this.advisorName.trim();
  }

  get clientNameInvalid(): boolean {
    return this.showQuoteErrors && !this.clientName.trim();
  }

  get blockNumberInvalid(): boolean {
    return this.showQuoteErrors && !this.blockNumber.trim();
  }

  get lotNumberInvalid(): boolean {
    return this.showQuoteErrors && !this.lotNumber.trim();
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
  readonly mesesDelAnio: { value: number; label: string }[] = [
    { value: 1, label: 'Enero' },
    { value: 2, label: 'Febrero' },
    { value: 3, label: 'Marzo' },
    { value: 4, label: 'Abril' },
    { value: 5, label: 'Mayo' },
    { value: 6, label: 'Junio' },
    { value: 7, label: 'Julio' },
    { value: 8, label: 'Agosto' },
    { value: 9, label: 'Septiembre' },
    { value: 10, label: 'Octubre' },
    { value: 11, label: 'Noviembre' },
    { value: 12, label: 'Diciembre' },
  ];

  // El asesor puede editar el número de aportaciones (compartido entre "Con anualidades" y
  // "Promoción", igual que antes de ser editable). null = todavía no lo ha tocado, así que
  // sigue el cálculo automático según el plazo.
  private annualContributionsCountOverride: number | null = null;

  /** Tope de aportaciones según el plazo actual: un año completo de mensualidades regulares
   * antes de terminar de pagar. Es el mismo valor que antes se calculaba siempre, ahora es
   * también el máximo que el asesor puede capturar a mano. */
  get annualContributionsCountMax(): number {
    return Math.max(Math.floor(this.selectedMonths / 12) - 1, 0);
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
    return this.selectedProject === 'samai' && this.selectedMonths > 12;
  }

  get isAnnualities(): boolean {
    return this.selectedProject === 'samai' && this.selectedPaymentType === 'annualities';
  }

  onAnnualContributionInput(event: Event): void {
    const input = event.target as HTMLInputElement;

    const rawValue = input.value.replace(/\D/g, '');

    if (!rawValue) {
      this.annualContribution = 0;
      this.annualContributionDisplay = '';
      return;
    }

    this.annualContribution = Number(rawValue);

    this.annualContributionDisplay = this.annualContribution.toLocaleString('en-US');
  }

  get annualContributionsTotal(): number {
    return this.annualContribution * this.annualContributionsCount;
  }

  get annualitiesMonthlyBalance(): number {
    return Math.max(this.totalPrice - this.annualContributionsTotal, 0);
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
      return this.totalPrice / this.selectedMonths;
    }

    // Total que se pagará mediante anualidades
    const annualitiesTotal = this.annualContribution * this.annualContributionsCount;

    // Lo que todavía falta cubrir con mensualidades
    const remainingBalance = this.totalPrice - annualitiesTotal;

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

    return this.totalPrice / this.selectedMonths;
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

    return Math.floor((this.totalPrice - 1) / this.annualContributionsCount);
  }

  get annualContributionValid(): boolean {
    return this.isAnnualities && !this.annualContributionInvalid;
  }

  get isQuoteValid(): boolean {
    if (!this.isQuoteDataComplete) {
      return false;
    }

    if (this.isAnnualities && this.annualContributionInvalid) {
      return false;
    }

    if (this.selectedPaymentType === 'initial' && this.initialPaymentInvalid) {
      return false;
    }

    if (this.selectedPaymentType === 'downpayment' && this.downPaymentAmountInvalid) {
      return false;
    }

    if (this.isPromocion && this.promocionInvalida) {
      return false;
    }

    return true;
  }
}

export interface AmortizationRow {
  paymentNumber: number;
  month: string;
  year: number;
  payment: number;
  balance: number;
  accumulatedPayment: number;
}
