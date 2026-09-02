import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { ComisionesService } from '../../../core/services/comisiones.service';
import { ToastService } from '../../../core/services/toast.service';
import { descargarCsv } from '../../../core/utils/csv';
import { Comision } from '../../../core/models/comision.model';

interface ResumenAsesor {
  nombre: string;
  generado: number;
  pagado: number;
}

@Component({
  selector: 'app-comisiones',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './comisiones.component.html',
})
export class ComisionesComponent {
  private readonly auth = inject(AuthService);
  private readonly comisionesService = inject(ComisionesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  private readonly currencyFormatter = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly isLoading = signal(true);
  readonly isGuardandoPorcentaje = signal(false);
  readonly savingId = signal<number | null>(null);

  readonly comisiones = signal<Comision[]>([]);
  readonly porcentaje = signal<number>(0);

  readonly porcentajeForm = this.fb.group({
    porcentaje: this.fb.control<number | null>(null, {
      validators: [Validators.required, Validators.min(0), Validators.max(100)],
    }),
  });

  readonly totalGenerado = computed(() => this.comisiones().reduce((suma, c) => suma + c.monto, 0));
  readonly totalPagado = computed(() =>
    this.comisiones()
      .filter((c) => c.pagada)
      .reduce((suma, c) => suma + c.monto, 0),
  );
  readonly totalPendiente = computed(() => this.totalGenerado() - this.totalPagado());

  readonly porAsesor = computed<ResumenAsesor[]>(() => {
    const mapa = new Map<number, ResumenAsesor>();
    for (const c of this.comisiones()) {
      const entrada = mapa.get(c.asesor.id) ?? { nombre: c.asesor.nombre, generado: 0, pagado: 0 };
      entrada.generado += c.monto;
      if (c.pagada) entrada.pagado += c.monto;
      mapa.set(c.asesor.id, entrada);
    }
    return [...mapa.values()].sort((a, b) => b.generado - a.generado);
  });

  constructor() {
    this.cargar();
  }

  private async cargar(): Promise<void> {
    this.isLoading.set(true);
    try {
      const [comisiones, config] = await Promise.all([
        this.comisionesService.listar(),
        this.comisionesService.configuracion(),
      ]);
      this.comisiones.set(comisiones);
      this.porcentaje.set(config.porcentaje);
      this.porcentajeForm.setValue({ porcentaje: config.porcentaje });
    } catch {
      this.toast.error('No se pudieron cargar las comisiones.');
    } finally {
      this.isLoading.set(false);
    }
  }

  formatMonto(valor: number): string {
    return this.currencyFormatter.format(valor);
  }

  pillClase(pagada: boolean): string {
    return pagada
      ? 'bg-green-50 text-green-700 dark:bg-green-500/10 dark:text-green-400'
      : 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400';
  }

  async guardarPorcentaje(): Promise<void> {
    if (this.porcentajeForm.invalid || this.isGuardandoPorcentaje()) {
      this.porcentajeForm.markAllAsTouched();
      return;
    }

    this.isGuardandoPorcentaje.set(true);
    try {
      const config = await this.comisionesService.actualizarConfiguracion(
        this.porcentajeForm.getRawValue().porcentaje!,
      );
      this.porcentaje.set(config.porcentaje);
      this.toast.success(`Comisión actualizada a ${config.porcentaje}%.`);
    } catch {
      this.toast.error('No se pudo actualizar el porcentaje.');
    } finally {
      this.isGuardandoPorcentaje.set(false);
    }
  }

  exportarCsv(): void {
    const filas = this.comisiones().map((c) => ({
      lead: c.leadNombreCliente,
      asesor: c.asesor.nombre,
      monto: c.monto,
      porcentajeAplicado: c.porcentajeAplicado,
      estado: c.pagada ? 'Pagada' : 'Pendiente',
      fechaCreacion: c.fechaCreacion,
      fechaPago: c.fechaPago ?? '',
    }));

    descargarCsv(
      `comisiones_${new Date().toISOString().slice(0, 10)}.csv`,
      {
        lead: 'Lead',
        asesor: 'Asesor',
        monto: 'Monto',
        porcentajeAplicado: 'Porcentaje aplicado',
        estado: 'Estado',
        fechaCreacion: 'Fecha de generación',
        fechaPago: 'Fecha de pago',
      },
      filas,
    );
  }

  async alternarPagada(comision: Comision): Promise<void> {
    this.savingId.set(comision.id);
    try {
      const actualizada = await this.comisionesService.marcarPagada(comision.id, !comision.pagada);
      this.comisiones.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c)));
    } catch {
      this.toast.error('No se pudo actualizar el estado de pago.');
    } finally {
      this.savingId.set(null);
    }
  }
}
