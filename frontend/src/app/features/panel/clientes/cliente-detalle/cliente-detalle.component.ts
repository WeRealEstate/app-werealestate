import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideExternalLink, LucidePencil, LucidePhone } from '@lucide/angular';
import { Amortizacion, cuentaAtraso, frasePagosAtrasados } from '../../../../core/models/amortizacion.model';
import { UBICACION_DOCUMENTO_LABELS } from '../../../../core/models/asesor-externo.model';
import {
  Cliente,
  ClienteVenta,
  ESTADO_CIVIL_LABELS,
  FUENTE_CLIENTE_LABELS,
  NumeracionCliente,
  NumeroCliente,
} from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ClienteFormModalComponent } from '../../../../shared/cliente-form-modal/cliente-form-modal.component';
import { ConfirmarCambioContratoComponent } from '../../../../shared/confirmar-cambio-contrato/confirmar-cambio-contrato.component';
import { TablaAmortizacionComponent } from '../../../../shared/tabla-amortizacion/tabla-amortizacion.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

@Component({
  selector: 'app-cliente-detalle',
  imports: [WeLoaderComponent, FormsModule, RouterLink, ClienteFormModalComponent, TablaAmortizacionComponent, ConfirmarCambioContratoComponent, LucideExternalLink, LucidePencil, LucidePhone],
  templateUrl: './cliente-detalle.component.html',
})
export class ClienteDetalleComponent {
  private readonly clientesService = inject(ClientesService);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  readonly cliente = signal<Cliente | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly editando = signal(false);

  // --- Tabla de amortización de cada compra (con su estado de cuenta a hoy): se piden aparte para no frenar la
  // ficha. Arranca abierta la primera compra atrasada o, si ninguna lo está, la más reciente.
  readonly amortizaciones = signal<Amortizacion[] | null>(null);
  readonly errorAmortizaciones = signal<string | null>(null);
  readonly abiertas = signal<ReadonlySet<number>>(new Set());
  readonly cuentaAtraso = cuentaAtraso;
  readonly frasePagosAtrasados = frasePagosAtrasados;
  readonly hayAtraso = computed(() => (this.cliente()?.pagosAtrasados ?? 0) > 0);

  estaAbierta(ventaId: number): boolean {
    return this.abiertas().has(ventaId);
  }

  alCambiarAbierta(ventaId: number, abierta: boolean): void {
    this.abiertas.update((actual) => {
      const nuevo = new Set(actual);
      if (abierta) nuevo.add(ventaId);
      else nuevo.delete(ventaId);
      return nuevo;
    });
  }

  private async cargarAmortizaciones(): Promise<void> {
    this.errorAmortizaciones.set(null);
    try {
      const lista = await this.clientesService.amortizaciones(this.id);
      // La primera vez se abre una; al recargar se respeta lo que la persona dejó abierto.
      if (this.amortizaciones() === null && lista.length > 0) {
        const inicial = lista.find((a) => a.pagosAtrasados > 0) ?? lista[0];
        this.abiertas.set(new Set([inicial.ventaId]));
      }
      this.amortizaciones.set(lista);
    } catch {
      this.errorAmortizaciones.set('No se pudieron cargar las tablas de amortización.');
    }
  }

  // --- Cambiar su número en un desarrollo (S-… o N-…; misma lógica que el número de venta: si otro ya lo tiene, se
  // recorre +1). Se mueve junto con quienes lo comparten (el titular y los copropietarios de esa compra).
  private readonly toast = inject(ToastService);
  readonly cambiandoNumero = signal(false);
  readonly numeroEditando = signal<NumeroCliente | null>(null);
  readonly numeroNuevo = signal<number | null>(null);
  readonly numeracion = signal<NumeracionCliente | null>(null);
  readonly guardandoNumero = signal(false);
  readonly errorNumero = signal<string | null>(null);

  abrirCambioNumero(n: NumeroCliente): void {
    this.numeroEditando.set(n);
    this.numeroNuevo.set(n.numero);
    this.numeracion.set(null);
    this.errorNumero.set(null);
    this.cambiandoNumero.set(true);
  }

  cerrarCambioNumero(): void {
    if (!this.guardandoNumero()) this.cambiandoNumero.set(false);
  }

  async alEscribirNumero(valor: string): Promise<void> {
    const n = Number(valor.replace(/\D/g, ''));
    const numero = Number.isInteger(n) && n >= 1 ? n : null;
    this.numeroNuevo.set(numero);
    this.errorNumero.set(null);
    this.numeracion.set(null);
    const editando = this.numeroEditando();
    if (numero === null || !editando) return;
    try {
      const info = await this.clientesService.numeracion(editando.desarrolloId, numero, this.id);
      // Solo vale si el asesor no siguió escribiendo mientras tanto.
      if (this.numeroNuevo() === numero) this.numeracion.set(info);
    } catch {
      // el aviso es solo informativo; al guardar se valida de todos modos
    }
  }

  async guardarNumero(): Promise<void> {
    const numero = this.numeroNuevo();
    const editando = this.numeroEditando();
    if (numero === null || !editando || this.guardandoNumero()) return;
    if (numero === editando.numero) {
      this.cambiandoNumero.set(false);
      return;
    }
    this.guardandoNumero.set(true);
    this.errorNumero.set(null);
    try {
      this.cliente.set(await this.clientesService.cambiarNumero(this.id, editando.desarrolloId, numero));
      this.cambiandoNumero.set(false);
      void this.cargarAmortizaciones();
      this.toast.success(`Ahora es el cliente ${editando.prefijo}-${numero} en ${editando.desarrollo}.`);
    } catch (e) {
      this.errorNumero.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo cambiar el número.',
      );
    } finally {
      this.guardandoNumero.set(false);
    }
  }

  // --- Número de contrato de cada venta: se edita con el lápiz de su fila (opcional, no se repite entre ventas).
  readonly editandoContratoDe = signal<number | null>(null);
  readonly contratoBorrador = signal('');
  readonly guardandoContrato = signal(false);
  readonly errorContrato = signal<string | null>(null);

  editarContrato(v: ClienteVenta): void {
    this.contratoBorrador.set(v.numeroContrato ?? '');
    this.errorContrato.set(null);
    this.editandoContratoDe.set(v.ventaId);
    setTimeout(() => document.querySelector<HTMLInputElement>('[data-testid="contrato-input"]')?.focus());
  }

  cancelarContrato(): void {
    if (!this.guardandoContrato()) this.editandoContratoDe.set(null);
  }

  /** Ventana de contraseña: al cambiar o quitar un número que la venta ya tiene (poner el primero es directo). */
  readonly confirmandoContratoDe = signal<ClienteVenta | null>(null);
  readonly errorConfirmacionContrato = signal<string | null>(null);

  async guardarContrato(v: ClienteVenta): Promise<void> {
    if (this.guardandoContrato()) return;
    const nuevo = this.contratoBorrador().trim() || null;
    if (nuevo === (v.numeroContrato ?? null)) {
      this.editandoContratoDe.set(null);
      return;
    }
    if (v.numeroContrato) {
      this.errorConfirmacionContrato.set(null);
      this.confirmandoContratoDe.set(v);
      return;
    }
    await this.enviarContrato(v, null);
  }

  async enviarContrato(v: ClienteVenta, password: string | null): Promise<void> {
    if (this.guardandoContrato()) return;
    const nuevo = this.contratoBorrador().trim() || null;
    this.guardandoContrato.set(true);
    this.errorContrato.set(null);
    this.errorConfirmacionContrato.set(null);
    try {
      const venta = await this.clientesService.guardarNumeroContratoDeVenta(v.ventaId, nuevo, password);
      this.cliente.update((c) =>
        c ? { ...c, ventas: c.ventas.map((x) => (x.ventaId === v.ventaId ? { ...x, numeroContrato: venta.numeroContrato } : x)) } : c,
      );
      // El PDF de la tabla de amortización lleva el número de contrato.
      this.amortizaciones.update((lista) =>
        lista ? lista.map((a) => (a.ventaId === v.ventaId ? { ...a, numeroContrato: venta.numeroContrato } : a)) : lista,
      );
      this.confirmandoContratoDe.set(null);
      this.editandoContratoDe.set(null);
      this.toast.success(nuevo ? `Contrato ${nuevo} guardado en la venta ${v.numero}.` : `Se quitó el número de contrato de la venta ${v.numero}.`);
    } catch (e) {
      const mensaje =
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo guardar el número de contrato.';
      if (this.confirmandoContratoDe()) this.errorConfirmacionContrato.set(mensaje);
      else this.errorContrato.set(mensaje);
    } finally {
      this.guardandoContrato.set(false);
    }
  }

  readonly civilLabels = ESTADO_CIVIL_LABELS;
  readonly fuenteLabels = FUENTE_CLIENTE_LABELS;
  readonly ubicacionLabels = UBICACION_DOCUMENTO_LABELS;

  constructor() {
    void this.cargar();
    void this.cargarAmortizaciones();
  }

  private async cargar(): Promise<void> {
    try {
      this.cliente.set(await this.clientesService.obtener(this.id));
    } catch {
      this.error.set('No se encontró el cliente.');
    } finally {
      this.cargando.set(false);
    }
  }

  async alGuardar(): Promise<void> {
    this.editando.set(false);
    // Nombre y teléfono también van en el PDF de la tabla de amortización.
    await Promise.all([this.cargar(), this.cargarAmortizaciones()]);
  }

  esUrl(url: string | null): boolean {
    return !!url && /^https?:\/\//i.test(url);
  }

  fecha(iso: string | null): string {
    if (!iso) return '—';
    const [y, m, d] = iso.slice(0, 10).split('-');
    return `${d}/${m}/${y}`;
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  domicilio(c: Cliente): string {
    return [c.calle, c.colonia, c.codigoPostal ? 'CP ' + c.codigoPostal : null, c.municipio, c.estado].filter(Boolean).join(', ');
  }

  /** Enlace tel: que abre la app de llamadas del dispositivo; deja solo dígitos y el + inicial. */
  enlaceTel(telefono: string): string {
    return 'tel:' + telefono.replace(/(?!^\+)[^\d]/g, '');
  }
}
