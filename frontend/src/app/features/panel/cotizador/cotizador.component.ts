import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { PdfService, QuotePdfData } from '../../../core/services/pdf-cotizacion.service';
import { PROJECTS_CONFIG } from '../../../core/data/proyectos-cotizador.config';
import { FadeInDirective } from '../../../shared/motion/fade-in.directive';
import { PressDirective } from '../../../shared/motion/press.directive';
import { ValuePulseDirective } from '../../../shared/motion/value-pulse.directive';

export type ProjectId = 'samai' | 'nanuu';
export type PaymentType = 'msi' | 'downpayment' | 'annualities' | 'cash';

@Component({
  selector: 'app-cotizador',
  standalone: true,
  imports: [DatePipe, FormsModule, FadeInDirective, PressDirective, ValuePulseDirective],
  templateUrl: './cotizador.component.html',
})
export class CotizadorComponent {
  private readonly auth = inject(AuthService);
  private readonly pdfService = inject(PdfService);

  showQuoteErrors = false;

  selectedProject: ProjectId = 'samai';

  currentDate = new Date();

  // Superficie seleccionada
  selectedArea: number = 200;

  // Indica si el usuario eligió superficie personalizada
  isCustomArea: boolean = false;

  // Precio actual por m²
  pricePerM2: number = 800;

  customAreaDisplay = '200';

  selectedPaymentType: PaymentType = 'msi';

  downPaymentPercentage: number = 10;

  selectedMonths: number = 60;

  advisorName: string = this.auth.currentUser()?.nombre ?? '';

  clientName: string = '';

  blockNumber: string = '';
  lotNumber: string = '';

  selectProject(project: ProjectId): void {
    this.selectedProject = project;

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
  }

  selectArea(area: number): void {
    this.selectedArea = area;
    this.isCustomArea = false;
    this.customAreaDisplay = area.toLocaleString('en-US');
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
      this.selectedPaymentType = 'msi';
    }

    // Si regresamos al precio normal
    if (price === 800) {
      this.isCustomArea = false;
      this.selectedArea = 200;
      this.customAreaDisplay = '200';
    }
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

    // Valor VISUAL con comas y decimales
    const [integerPart, decimalPart] = rawValue.split('.');

    const formattedInteger = Number(integerPart || 0).toLocaleString('en-US');

    this.customAreaDisplay = decimalPart !== undefined ? `${formattedInteger}.${decimalPart}` : formattedInteger;
  }

  get totalPrice(): number {
    return this.selectedArea * this.pricePerM2;
  }

  get cashPrice(): number {
    if (this.selectedProject === 'samai') {
      return (this.selectedArea / 200) * 110000;
    }

    return this.totalPrice;
  }

  selectPaymentType(type: PaymentType): void {
    if (type === 'annualities' && !this.annualitiesAvailable) {
      return;
    }

    this.selectedPaymentType = type;
  }

  get downPayment(): number {
    if (this.selectedProject === 'nanuu') {
      if (this.selectedPaymentType === 'downpayment') {
        return this.totalPrice * 0.2;
      }

      if (this.selectedPaymentType === 'msi') {
        return 0;
      }
    }

    // SAMAI
    if (this.selectedPaymentType === 'downpayment') {
      return this.totalPrice * (this.downPaymentPercentage / 100);
    }

    return 0;
  }

  get financedAmount(): number {
    if (this.selectedProject === 'nanuu') {
      return this.totalInvestment - this.downPayment;
    }

    if (this.selectedPaymentType === 'msi') {
      return this.totalPrice;
    }

    if (this.selectedPaymentType === 'downpayment') {
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
      this.selectedPaymentType === 'annualities'
    ) {
      return this.selectedMonths;
    }

    return 0;
  }
  get monthlyPayment(): number {
    if (this.financingMonths <= 0) {
      return 0;
    }

    // NANUU
    if (this.selectedProject === 'nanuu') {
      return this.financedAmount / this.selectedMonths;
    }

    if (this.selectedPaymentType === 'annualities') {
      return this.annualitiesMonthlyPayment;
    }

    // SAMAI
    if (this.selectedPaymentType === 'msi' || this.selectedPaymentType === 'downpayment') {
      return this.financedAmount / this.selectedMonths;
    }

    return 0;
  }

  get paymentMethodLabel(): string {
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

    if (this.selectedPaymentType === 'downpayment') {
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
    await this.pdfService.downloadQuotePdf(this.getQuotePdfData());
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
      return this.buildAnnualitiesAmortizationTable();
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
      const monthOffset = this.selectedPaymentType === 'downpayment' ? i : i - 1;

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

  private buildAnnualitiesAmortizationTable(): AmortizationRow[] {
    const rows: AmortizationRow[] = [];

    const startDate = new Date(this.currentDate);

    let balance = this.totalPrice;

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

      let payment = isAnnuality ? this.annualContribution : this.annualitiesMonthlyPayment;

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

      downPayment: this.downPayment,

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

  get annualContributionsCount(): number {
    return Math.max(Math.floor(this.selectedMonths / 12) - 1, 0);
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
