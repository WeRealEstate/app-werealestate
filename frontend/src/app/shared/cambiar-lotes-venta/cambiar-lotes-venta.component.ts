import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideX } from '@lucide/angular';
import { Lote } from '../../core/models/lote.model';
import { Promocion } from '../../core/models/promocion.model';
import { Venta } from '../../core/models/venta.model';
import { LotesService } from '../../core/services/lotes.service';
import { PromocionesService } from '../../core/services/promociones.service';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';
import { mensualidadConAportaciones } from '../../core/utils/aportaciones';
import { dinero } from '../../core/utils/formato-pantalla';
import { bonoDe, motivoNoAplica, partesDelBono } from '../../core/utils/promocion';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

interface Renglon {
  lote: Lote;
  /** Precio de lista (sin el bono de la promoción). */
  precio: number | null;
}

/**
 * Sustituir, agregar o quitar los lotes de una venta ya registrada. Muestra cómo quedan precio, abonado, saldo y
 * mensualidad antes de confirmar con contraseña; el servidor libera los lotes que salen, marca los que entran y
 * recalcula la comisión.
 */
@Component({
  selector: 'app-cambiar-lotes-venta',
  standalone: true,
  imports: [FormsModule, LucideX, MonedaInputDirective],
  templateUrl: './cambiar-lotes-venta.component.html',
})
export class CambiarLotesVentaComponent {
  private readonly ventasService = inject(VentasService);
  private readonly lotesService = inject(LotesService);
  private readonly promocionesService = inject(PromocionesService);
  private readonly toast = inject(ToastService);

  readonly venta = input.required<Venta>();
  readonly reestructurada = input(false);
  readonly cerrar = output<void>();
  readonly guardado = output<void>();

  readonly renglones = signal<Renglon[]>([]);
  readonly inventario = signal<Lote[]>([]);
  readonly promociones = signal<Promocion[]>([]);
  readonly promocionId = signal<number | null>(null);
  /** La venta tenía una promoción que ya no se puede elegir (borrada o apagada). */
  readonly promocionPerdida = signal(false);
  readonly mensualidadManual = signal<number | null>(null);
  readonly password = signal('');
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly loteNuevoId = signal<number | null>(null);

  readonly promocion = computed(() => this.promociones().find((p) => p.id === this.promocionId()) ?? null);
  readonly promocionError = computed(() => {
    const promo = this.promocion();
    return promo
      ? motivoNoAplica(promo, this.renglones().map((r) => ({ superficie: r.lote.superficie, desarrollo: r.lote.desarrollo.nombre })))
      : null;
  });
  readonly bono = computed(() => {
    const promo = this.promocion();
    return promo && !this.promocionError() ? bonoDe(promo, this.renglones().length) : 0;
  });
  readonly preciosFinales = computed(() => {
    const partes = partesDelBono(this.bono(), this.renglones().length);
    return this.renglones().map((r, i) => Math.max(0, Math.round(((r.precio ?? 0) - (partes[i] ?? 0)) * 100) / 100));
  });
  readonly total = computed(() => this.preciosFinales().reduce((s, p) => s + p, 0));
  readonly saldo = computed(() => Math.max(0, Math.round((this.total() - this.venta().totalAbonado) * 100) / 100));
  readonly bajoLoAbonado = computed(() => this.total() < this.venta().totalAbonado);

  /** Misma regla que al registrar: precio menos enganche (y aportaciones) entre los meses del plazo. */
  readonly mensualidadSugerida = computed<number | null>(() => {
    const v = this.venta();
    if (!v.plazoMeses) return null;
    // Si el precio no cambia, la mensualidad se queda como está (pudo ajustarse a mano o por un depósito).
    if (Math.abs(this.total() - v.precioVenta) < 0.01) return v.mensualidad;
    const saldo = this.total() - (v.enganche ?? 0);
    const valor = v.aportaciones.length
      ? mensualidadConAportaciones(saldo, v.aportaciones.map((a) => ({ anio: a.anio, mes: a.mes, monto: a.monto })), v.plazoMeses)
      : saldo / v.plazoMeses;
    return valor > 0 ? Math.round(valor * 100) / 100 : null;
  });
  readonly mensualidad = computed(() => this.mensualidadManual() ?? this.mensualidadSugerida());

  readonly disponibles = computed(() => {
    const puestos = new Set(this.renglones().map((r) => r.lote.id));
    const delaVenta = new Set(this.venta().lotes.map((l) => l.lote.id));
    return this.inventario().filter((l) => !puestos.has(l.id) && (l.ventaNumero == null || delaVenta.has(l.id)));
  });
  /** Dinero apartado de los lotes que entran: queda pendiente de aplicar en la venta. */
  readonly apartadoNuevo = computed(() => {
    const deLaVenta = new Set(this.venta().lotes.map((l) => l.lote.id));
    return this.renglones()
      .filter((r) => !deLaVenta.has(r.lote.id))
      .reduce((s, r) => s + (r.lote.montoApartado ?? 0), 0);
  });
  readonly huboCambios = computed(() => {
    const antes = this.venta().lotes;
    const ahora = this.renglones();
    const precios = this.preciosFinales();
    return (
      antes.length !== ahora.length ||
      ahora.some((r, i) => antes.find((a) => a.lote.id === r.lote.id)?.precio !== precios[i]) ||
      (this.promocion()?.nombre ?? null) !== this.venta().promocionNombre ||
      this.mensualidadManual() !== null
    );
  });
  readonly puedeGuardar = computed(
    () =>
      this.renglones().length > 0 &&
      this.renglones().every((r) => r.precio !== null && r.precio > 0) &&
      !this.promocionError() &&
      this.huboCambios() &&
      this.password().length > 0 &&
      !this.guardando(),
  );

  money(valor: number): string {
    return dinero(valor);
  }

  ngOnInit(): void {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    const v = this.venta();
    this.renglones.set(v.lotes.map((l) => ({ lote: l.lote, precio: l.precio })));
    try {
      const [inventario, promociones] = await Promise.all([
        this.lotesService.buscarPaginado({ desarrolloId: v.lotes[0].lote.desarrollo.id, pagina: 0, tamano: 1000 }),
        this.promocionesService.paraVenta(v.fechaVenta, true).catch(() => [] as Promocion[]),
      ]);
      this.inventario.set(inventario.contenido);
      // Aquí solo se eligen las de bono; la de mensualidad fija que ya tenga la venta se conserva.
      this.promociones.set(promociones.filter((p) => p.modalidad === 'BONO_LOTES' || p.nombre === v.promocionNombre));
      if (v.promocionNombre) {
        const actual = promociones.find((p) => p.nombre === v.promocionNombre);
        if (actual) {
          // Precios de lista: lo guardado más la parte del bono que tenía cada lote.
          const partes = partesDelBono(v.promocionBono ?? 0, v.lotes.length);
          this.renglones.set(v.lotes.map((l, i) => ({ lote: l.lote, precio: Math.round((l.precio + partes[i]) * 100) / 100 })));
          this.promocionId.set(actual.id);
        } else {
          this.promocionPerdida.set(true);
        }
      }
    } catch {
      this.error.set('No se pudieron cargar los lotes del desarrollo.');
    } finally {
      this.cargando.set(false);
    }
  }

  cambiarPrecio(loteId: number, precio: number | null): void {
    this.renglones.update((lista) => lista.map((r) => (r.lote.id === loteId ? { ...r, precio } : r)));
  }

  quitar(loteId: number): void {
    this.renglones.update((lista) => lista.filter((r) => r.lote.id !== loteId));
  }

  agregar(): void {
    const lote = this.disponibles().find((l) => l.id === this.loteNuevoId());
    if (!lote) return;
    const anterior = this.venta().lotes.find((l) => l.lote.id === lote.id);
    this.renglones.update((lista) => [...lista, { lote, precio: anterior?.precio ?? Math.round(lote.precio) }]);
    this.loteNuevoId.set(null);
  }

  async guardar(): Promise<void> {
    if (!this.puedeGuardar()) return;
    this.guardando.set(true);
    this.error.set(null);
    const precios = this.preciosFinales();
    try {
      await this.ventasService.cambiarLotes(this.venta().id, {
        lotes: this.renglones().map((r, i) => ({ loteId: r.lote.id, precio: precios[i] })),
        mensualidad: this.venta().plazoMeses ? this.mensualidad() : null,
        promocionId: this.promocionId(),
        password: this.password(),
      });
      this.toast.success('Lotes de la venta actualizados.');
      this.guardado.emit();
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo guardar el cambio.',
      );
    } finally {
      this.guardando.set(false);
    }
  }
}
