import { LucideCircleAlert, LucideHandCoins, LucideLandmark, LucideTrendingUp, LucideWallet } from '@lucide/angular';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AbonoDeComision,
  Comision,
  ComisionDesglose,
  ComisionDesgloseFila,
  ComisionDetalle,
  ComisionPorEntregar,
  ComisionResumen,
  ComisionesPorEntregar,
  ESTADO_COMISION_CLASES,
  ESTADO_PAGO_CLIENTE_CLASES,
  ESTADO_PAGO_CLIENTE_LABELS,
  ESTADO_COMISION_LABELS,
  EstadoComision,
  FORMA_PAGO_COMISION_LABELS,
  FormaPagoComision,
  MODALIDAD_COMISION_LABELS,
} from '../../../core/models/finanzas.model';
import { frasePagosAtrasados } from '../../../core/models/amortizacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { FinanzasService } from '../../../core/services/finanzas.service';
import { PdfComisionService } from '../../../core/services/pdf-comision.service';
import { ToastService } from '../../../core/services/toast.service';
import { GastosListComponent } from '../gastos/gastos-list/gastos-list.component';
import { FinanzasIngresosComponent } from './finanzas-ingresos.component';
import { FinanzasValorComponent } from './finanzas-valor.component';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';
import { SkeletonKpisComponent } from '../../../shared/skeleton-kpis/skeleton-kpis.component';
import { MonedaInputDirective } from '../../../shared/moneda-input/moneda-input.directive';

type Pestana = 'comisiones' | 'ingresos' | 'valor' | 'gastos';

const ESTADOS: EstadoComision[] = ['PENDIENTE', 'ACUMULANDO', 'PARCIAL', 'PAGADA', 'CANCELADA'];

type VistaDesglose = 'reparto' | 'asesor' | 'desarrollo';

/** Con más abonos que estos, los más viejos se juntan en un solo renglón ("N abonos anteriores"). */
const MAX_ABONOS_VISIBLES = 8;
const ABONOS_RECIENTES = 6;

const ORDEN_NOMBRES = new Intl.Collator('es-MX', { sensitivity: 'base' });

/** Una parte de la barra de "Cómo se reparte". Colores validados con la skill de dataviz (verde, ámbar y violeta como
 * categorías vecinas en claro sobre #ffffff y en oscuro sobre la tarjeta #131d3b; en claro el ámbar queda bajo 3:1 y
 * lo compensan los nombres y montos visibles debajo). "Falta por generar" es el fondo de la barra, en gris. */
interface ParteReparto {
  clave: string;
  etiqueta: string;
  valor: number;
  /** % del total de comisiones, ya redondeado para mostrarse ("8%", "<1%"). */
  porcentaje: string;
  clase: string;
}

const CLASES_REPARTO = {
  entregado: 'bg-[#15803d]',
  porEntregar: 'bg-[#f59e0b] dark:bg-[#c98500]',
  detenido: 'bg-[#a855f7] dark:bg-[#9085e9]',
  falta: 'bg-[#cbd5e1] dark:bg-[#334155]',
} as const;

/** Una comisión de la lista del sábado con sus abonos listos para mostrarse. */
interface ItemSabado extends ComisionPorEntregar {
  anteriores: { cantidad: number; total: number } | null;
  abonosVisibles: AbonoDeComision[];
}

/** Lo que toca entregarle a un asesor el sábado: sus ventas y el total. */
interface GrupoSabado {
  clave: string;
  nombre: string;
  externo: boolean;
  total: number;
  items: ItemSabado[];
}

/** Finanzas — por ahora, comisiones de venta: el 5% de cada venta (editable), lo que ya ganó cada
 * una con los abonos del cliente y lo que se entrega los sábados. Ver backend ComisionService. */
@Component({
  selector: 'app-finanzas',
  standalone: true,
  imports: [LucideCircleAlert, LucideHandCoins, LucideTrendingUp, LucideLandmark, LucideWallet, MonedaInputDirective, WeLoaderComponent, SkeletonKpisComponent, FormsModule, RouterLink, FinanzasIngresosComponent, FinanzasValorComponent, GastosListComponent],
  templateUrl: './finanzas.component.html',
})
export class FinanzasComponent {
  private readonly finanzasService = inject(FinanzasService);
  private readonly pdfComision = inject(PdfComisionService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  /** Pestaña visible; las de ingresos y valor cargan sus datos solo al abrirse. */
  readonly pestana = signal<Pestana>('comisiones');

  /** Comisiones, Ingresos y Valor vendido dependen del módulo Finanzas; Gastos, del módulo Gastos. */
  readonly puedeFinanzas = this.auth.tieneModulo('FINANZAS');
  readonly pestanas: { id: Pestana; label: string }[] = [
    ...(this.puedeFinanzas
      ? [
          { id: 'comisiones' as Pestana, label: 'Comisiones' },
          { id: 'ingresos' as Pestana, label: 'Ingresos' },
          { id: 'valor' as Pestana, label: 'Valor vendido' },
        ]
      : []),
    ...(this.auth.tieneModulo('GASTOS') ? [{ id: 'gastos' as Pestana, label: 'Gastos' }] : []),
  ];

  readonly estados = ESTADOS;
  readonly estadoLabels = ESTADO_COMISION_LABELS;
  readonly estadoClases = ESTADO_COMISION_CLASES;
  readonly modalidadLabels = MODALIDAD_COMISION_LABELS;
  readonly pagoClienteLabels = ESTADO_PAGO_CLIENTE_LABELS;
  /** "2 mensualidades atrasadas" / "1 pago atrasado" (enganche, aportación o contado): mismo texto que Clientes. */
  readonly frasePagosAtrasados = frasePagosAtrasados;
  readonly pagoClienteClases = ESTADO_PAGO_CLIENTE_CLASES;
  readonly formaPagoLabels = FORMA_PAGO_COMISION_LABELS;

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly comisiones = signal<Comision[]>([]);
  readonly resumen = signal<ComisionResumen | null>(null);
  readonly porEntregar = signal<ComisionesPorEntregar | null>(null);
  readonly desglose = signal<ComisionDesglose | null>(null);

  // Desglose bajo las tarjetas: cómo se reparte el total, por asesor y por desarrollo.
  readonly vistasDesglose: { id: VistaDesglose; label: string }[] = [
    { id: 'reparto', label: 'Cómo se reparte' },
    { id: 'asesor', label: 'Por asesor' },
    { id: 'desarrollo', label: 'Por desarrollo' },
  ];
  readonly vistaDesglose = signal<VistaDesglose>('reparto');
  readonly filasDesglose = computed<ComisionDesgloseFila[]>(() => {
    const d = this.desglose();
    if (!d) return [];
    return this.vistaDesglose() === 'asesor' ? d.porAsesor : d.porDesarrollo;
  });

  /** "Cómo se reparte": las partes de la barra en orden (las tres de lo ganado y lo que falta por generar). */
  readonly reparto = computed(() => {
    const d = this.desglose();
    if (!d) return null;
    const t = d.total;
    const partes: ParteReparto[] = [
      { clave: 'entregado', etiqueta: 'Entregado', valor: t.entregado, porcentaje: porcentaje(t.entregado, t.total), clase: CLASES_REPARTO.entregado },
      { clave: 'porEntregar', etiqueta: 'Por entregar', valor: t.porEntregar, porcentaje: porcentaje(t.porEntregar, t.total), clase: CLASES_REPARTO.porEntregar },
      { clave: 'detenido', etiqueta: 'Detenido', valor: t.detenido, porcentaje: porcentaje(t.detenido, t.total), clase: CLASES_REPARTO.detenido },
      { clave: 'falta', etiqueta: 'Falta por generar', valor: t.faltaPorGenerar, porcentaje: porcentaje(t.faltaPorGenerar, t.total), clase: CLASES_REPARTO.falta },
    ];
    // Lo que ocupa lo ganado en la barra (sin restar lo entregado de más, que no es un tramo).
    const anchoGanado = t.entregado + t.porEntregar + t.detenido;
    return {
      partes,
      anchoGanado,
      anchoFalta: t.faltaPorGenerar,
      ganadoPorcentaje: porcentaje(t.ganado, t.total),
      descripcion:
        `Comisiones totales $${dinero(t.total)}: ` +
        partes.map((p) => `${p.etiqueta.toLowerCase()} $${dinero(p.valor)} (${p.porcentaje})`).join(', '),
    };
  });

  /** Lo que toca entregar el sábado, agrupado por asesor (por nombre), con el total a pagarle a cada uno. */
  readonly gruposSabado = computed<GrupoSabado[]>(() => {
    const grupos = new Map<string, GrupoSabado>();
    for (const item of this.porEntregar()?.items ?? []) {
      const c = item.comision;
      let grupo = grupos.get(c.asesorClave);
      if (!grupo) {
        grupo = { clave: c.asesorClave, nombre: c.asesorNombre, externo: c.asesorExterno, total: 0, items: [] };
        grupos.set(c.asesorClave, grupo);
      }
      grupo.items.push(conAbonosVisibles(item));
      grupo.total += item.montoAlSabado;
    }
    return [...grupos.values()].sort((a, b) => ORDEN_NOMBRES.compare(a.nombre, b.nombre));
  });

  /** Ventas de la lista del sábado con sus abonos abiertos. */
  readonly abonosAbiertos = signal<ReadonlySet<number>>(new Set());

  /** 'RETRASADA' no es un estado: filtra las que tienen entregas vencidas por pagar. */
  readonly filtroEstado = signal<'' | EstadoComision | 'RETRASADA'>('');
  readonly busqueda = signal('');
  readonly comisionesFiltradas = computed(() => {
    const estado = this.filtroEstado();
    const texto = this.busqueda().trim().toLowerCase();
    return this.comisiones().filter((c) => {
      if (estado === 'RETRASADA' ? !c.retrasada : estado && c.estado !== estado) return false;
      if (!texto) return true;
      return (
        c.cliente.toLowerCase().includes(texto) ||
        c.asesorNombre.toLowerCase().includes(texto) ||
        String(c.ventaNumero ?? '').includes(texto)
      );
    });
  });

  // Detalle desplegable de una comisión (lo ganado por abono y las entregas hechas).
  readonly expandidaId = signal<number | null>(null);
  readonly detalle = signal<ComisionDetalle | null>(null);

  // Editar monto/porcentaje: cambiar uno recalcula el otro sobre el valor de la venta.
  readonly editando = signal<Comision | null>(null);
  readonly editPorcentaje = signal<number | null>(null);
  readonly editMonto = signal<number | null>(null);
  private editCampo: 'porcentaje' | 'monto' = 'porcentaje';

  // Entregar: monto (hasta lo ganado sin entregar), fecha y nota; IVA, factura y forma de pago se eligen
  // siempre (empiezan vacíos), solo se marcan y salen en el ticket de pago.
  readonly entregando = signal<Comision | null>(null);
  readonly entregaMonto = signal<number | null>(null);
  readonly entregaFecha = signal(hoyIso());
  readonly entregaNotas = signal('');
  readonly entregaMax = signal(0);
  readonly entregaConIva = signal<boolean | null>(null);
  readonly entregaConFactura = signal<boolean | null>(null);
  readonly entregaFormaPago = signal<FormaPagoComision | null>(null);
  readonly opcionesIva = [
    { valor: true, label: 'Con IVA' },
    { valor: false, label: 'Sin IVA' },
  ];
  readonly opcionesFactura = [
    { valor: true, label: 'Con factura' },
    { valor: false, label: 'Sin factura' },
  ];
  readonly formasPago: FormaPagoComision[] = ['EFECTIVO', 'TRANSFERENCIA'];

  /** Entrega cuyo ticket PDF se está generando (para el "Generando…" del botón). */
  readonly generandoTicket = signal<number | null>(null);

  // Devolución: el asesor devuelve (total o parcial) una comisión ya entregada; se captura a mano.
  readonly devolviendo = signal<Comision | null>(null);
  readonly devolucionMonto = signal<number | null>(null);
  readonly devolucionFecha = signal(hoyIso());
  readonly devolucionNotas = signal('');

  readonly guardando = signal(false);
  readonly errorModal = signal<string | null>(null);

  constructor() {
    const pedida = this.route.snapshot.queryParamMap.get('pestana');
    const inicial = this.pestanas.find((t) => t.id === pedida) ?? this.pestanas[0];
    if (inicial) this.pestana.set(inicial.id);
    if (this.puedeFinanzas) void this.cargar();
    else this.isLoading.set(false);
  }

  async cargar(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [comisiones, resumen, porEntregar, desglose] = await Promise.all([
        this.finanzasService.listarComisiones(),
        this.finanzasService.resumenComisiones(),
        this.finanzasService.comisionesPorEntregar(this.porEntregar()?.sabado),
        this.finanzasService.desgloseComisiones(),
      ]);
      this.comisiones.set(comisiones);
      this.resumen.set(resumen);
      this.porEntregar.set(porEntregar);
      this.desglose.set(desglose);
    } catch {
      this.errorMessage.set('No se pudieron cargar las comisiones. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Recarga lo que cambia al editar/entregar sin parpadeo de pantalla completa. */
  private async refrescar(): Promise<void> {
    try {
      const [comisiones, resumen, porEntregar, desglose] = await Promise.all([
        this.finanzasService.listarComisiones(),
        this.finanzasService.resumenComisiones(),
        this.finanzasService.comisionesPorEntregar(this.porEntregar()?.sabado),
        this.finanzasService.desgloseComisiones(),
      ]);
      this.comisiones.set(comisiones);
      this.resumen.set(resumen);
      this.porEntregar.set(porEntregar);
      this.desglose.set(desglose);
    } catch {
      this.toast.error('No se pudieron actualizar los totales. Recarga la página.');
    }
  }

  async cambiarSabado(dias: number): Promise<void> {
    const actual = this.porEntregar()?.sabado;
    if (!actual) return;
    const fecha = new Date(`${actual}T12:00:00`);
    fecha.setDate(fecha.getDate() + dias);
    try {
      this.porEntregar.set(await this.finanzasService.comisionesPorEntregar(fecha.toISOString().slice(0, 10)));
    } catch {
      this.toast.error('No se pudo cargar ese sábado.');
    }
  }

  alternarAbonos(comisionId: number): void {
    this.abonosAbiertos.update((abiertos) => {
      const nuevos = new Set(abiertos);
      if (!nuevos.delete(comisionId)) nuevos.add(comisionId);
      return nuevos;
    });
  }

  // ---- Detalle ----

  async alternarDetalle(c: Comision): Promise<void> {
    if (this.expandidaId() === c.id) {
      this.expandidaId.set(null);
      this.detalle.set(null);
      return;
    }
    this.expandidaId.set(c.id);
    this.detalle.set(null);
    try {
      this.detalle.set(await this.finanzasService.detalleComision(c.id));
    } catch {
      this.toast.error('No se pudo cargar el detalle de la comisión.');
    }
  }

  // ---- Editar ----

  abrirEdicion(c: Comision): void {
    this.editando.set(c);
    this.editPorcentaje.set(c.porcentaje);
    this.editMonto.set(c.monto);
    this.editCampo = 'porcentaje';
    this.errorModal.set(null);
  }

  cerrarEdicion(): void {
    this.editando.set(null);
  }

  onPorcentajeChange(valor: number | null): void {
    this.editPorcentaje.set(valor);
    this.editCampo = 'porcentaje';
    const c = this.editando();
    if (c && valor !== null) this.editMonto.set(Math.round(c.base * valor) / 100);
  }

  onMontoChange(valor: number | null): void {
    this.editMonto.set(valor);
    this.editCampo = 'monto';
    const c = this.editando();
    if (c && valor !== null && c.base > 0) this.editPorcentaje.set(Math.round((valor / c.base) * 1_000_000) / 10_000);
  }

  async guardarEdicion(): Promise<void> {
    const c = this.editando();
    if (!c || this.guardando()) return;
    const porcentaje = this.editPorcentaje();
    const monto = this.editMonto();
    if (porcentaje === null || monto === null || porcentaje < 0 || porcentaje > 10 || monto < 0) {
      this.errorModal.set('Escribe un porcentaje entre 0 y 10 (el máximo de comisión es 10% del valor de la venta) y un monto válido.');
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      // Se manda solo el campo que se tocó: el servidor calcula el otro con la misma base.
      const cambio = this.editCampo === 'monto' ? { monto } : { porcentaje };
      const actualizado = await this.finanzasService.editarComision(c.id, cambio);
      this.editando.set(null);
      this.toast.success('Comisión actualizada.');
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, 'No se pudo actualizar la comisión.'));
    } finally {
      this.guardando.set(false);
    }
  }

  // ---- Entregar ----

  abrirEntrega(c: Comision, sugerido?: number): void {
    this.entregando.set(c);
    this.entregaMax.set(c.porEntregar);
    this.entregaMonto.set(sugerido ?? c.porEntregar);
    this.entregaFecha.set(hoyIso());
    this.entregaNotas.set('');
    this.entregaConIva.set(null);
    this.entregaConFactura.set(null);
    this.entregaFormaPago.set(null);
    this.errorModal.set(null);
  }

  cerrarEntrega(): void {
    this.entregando.set(null);
  }

  async confirmarEntrega(): Promise<void> {
    const c = this.entregando();
    const monto = this.entregaMonto();
    if (!c || this.guardando()) return;
    if (monto === null || monto <= 0) {
      this.errorModal.set('Escribe el monto a entregar.');
      return;
    }
    if (monto > this.entregaMax()) {
      this.errorModal.set(`Solo hay $${dinero(this.entregaMax())} ganados por entregar: el cliente no ha pagado más.`);
      return;
    }
    const conIva = this.entregaConIva();
    const conFactura = this.entregaConFactura();
    const formaPago = this.entregaFormaPago();
    if (conIva === null || conFactura === null || formaPago === null) {
      this.errorModal.set('Elige si va con o sin IVA, con o sin factura y la forma de pago.');
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      const actualizado = await this.finanzasService.entregarComision(c.id, {
        monto,
        fecha: this.entregaFecha() || null,
        notas: this.entregaNotas().trim() || null,
        conIva,
        conFactura,
        formaPago,
      });
      this.entregando.set(null);
      this.toast.success('Entrega registrada y agregada a Gastos. Se descarga su ticket de pago.');
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      // La entrega recién hecha es la de id más alto (la lista viene por fecha, que puede ser anterior).
      const nueva = actualizado.entregas.reduce((mayor, e) => (e.id > mayor ? e.id : mayor), 0);
      if (nueva) void this.descargarTicket(c.id, nueva);
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, 'No se pudo registrar la entrega.'));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Ticket (comprobante) de pago de una entrega, en PDF. */
  async descargarTicket(comisionId: number, entregaId: number): Promise<void> {
    if (this.generandoTicket() !== null) return;
    this.generandoTicket.set(entregaId);
    try {
      const ticket = await this.finanzasService.ticketEntrega(comisionId, entregaId);
      await this.pdfComision.descargarTicket(ticket);
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo generar el ticket de pago.'));
    } finally {
      this.generandoTicket.set(null);
    }
  }

  // ---- Devolución de comisión ----

  abrirDevolucion(c: Comision): void {
    this.devolviendo.set(c);
    // Se sugiere lo entregado de más (lo que ya no respaldan los abonos), si lo hay.
    this.devolucionMonto.set(c.porDevolver > 0 ? c.porDevolver : null);
    this.devolucionFecha.set(hoyIso());
    this.devolucionNotas.set('');
    this.errorModal.set(null);
  }

  cerrarDevolucion(): void {
    this.devolviendo.set(null);
  }

  async confirmarDevolucion(): Promise<void> {
    const c = this.devolviendo();
    const monto = this.devolucionMonto();
    if (!c || this.guardando()) return;
    if (monto === null || monto <= 0) {
      this.errorModal.set('Escribe el monto que devolvió el asesor.');
      return;
    }
    if (monto > c.entregado) {
      this.errorModal.set(`Solo hay $${dinero(c.entregado)} entregados que se puedan devolver.`);
      return;
    }
    this.guardando.set(true);
    this.errorModal.set(null);
    try {
      const actualizado = await this.finanzasService.devolverComision(
        c.id,
        monto,
        this.devolucionFecha() || null,
        this.devolucionNotas().trim() || null,
      );
      this.devolviendo.set(null);
      this.toast.success('Devolución registrada: aparece como ingreso en Finanzas.');
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.errorModal.set(mensajeDe(error, 'No se pudo registrar la devolución.'));
    } finally {
      this.guardando.set(false);
    }
  }

  async anularEntrega(c: Comision, entregaId: number): Promise<void> {
    const confirmado = await this.confirmService.confirm({
      titulo: 'Anular entrega',
      mensaje: 'Se anula la entrega y también se elimina el gasto que generó. ¿Continuar?',
      textoConfirmar: 'Anular',
      peligroso: true,
    });
    if (!confirmado) return;
    try {
      const actualizado = await this.finanzasService.anularEntrega(c.id, entregaId);
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      this.toast.success('Entrega anulada.');
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo anular la entrega.'));
    }
  }

  async alternarCancelada(c: Comision): Promise<void> {
    const cancelar = !c.cancelada;
    const confirmado = await this.confirmService.confirm({
      titulo: cancelar ? 'Cancelar comisión' : 'Reactivar comisión',
      mensaje: cancelar
        ? `Se deja de acumular la comisión de ${c.cliente} y ya no se puede entregar lo que tenía ganado: deja de contar en Retrasadas y en la lista del sábado. Si la reactivas, vuelve a contar.`
        : `La comisión de ${c.cliente} vuelve a acumular con los abonos del cliente y lo ganado se puede entregar.`,
      textoConfirmar: cancelar ? 'Cancelar comisión' : 'Reactivar',
      peligroso: cancelar,
    });
    if (!confirmado) return;
    try {
      const actualizado = cancelar
        ? await this.finanzasService.cancelarComision(c.id)
        : await this.finanzasService.reactivarComision(c.id);
      if (this.expandidaId() === c.id) this.detalle.set(actualizado);
      await this.refrescar();
    } catch (error) {
      this.toast.error(mensajeDe(error, 'No se pudo cambiar la comisión.'));
    }
  }

  fecha(iso: string | null): string {
    if (!iso) return '—';
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }

  money(valor: number): string {
    return dinero(valor);
  }
}

/** % de un total para mostrarse: entero, "<1%" si es poco pero no cero. */
function porcentaje(valor: number, total: number): string {
  if (total <= 0 || valor <= 0) return '0%';
  const pct = (valor / total) * 100;
  return pct < 1 ? '<1%' : `${Math.round(pct)}%`;
}

/** Con muchos abonos, los más viejos van en un solo renglón para que la lista no crezca de más. */
function conAbonosVisibles(item: ComisionPorEntregar): ItemSabado {
  if (item.abonos.length <= MAX_ABONOS_VISIBLES) return { ...item, anteriores: null, abonosVisibles: item.abonos };
  const ocultos = item.abonos.slice(0, item.abonos.length - ABONOS_RECIENTES);
  return {
    ...item,
    anteriores: { cantidad: ocultos.length, total: ocultos.reduce((suma, a) => suma + a.aporte, 0) },
    abonosVisibles: item.abonos.slice(-ABONOS_RECIENTES),
  };
}

function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dinero(valor: number): string {
  return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : porDefecto;
}
