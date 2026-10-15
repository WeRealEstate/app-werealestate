import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideExternalLink, LucidePencil } from '@lucide/angular';
import { UBICACION_DOCUMENTO_LABELS } from '../../../../core/models/asesor-externo.model';
import { Cliente, ClienteVenta, ESTADO_CIVIL_LABELS, FUENTE_CLIENTE_LABELS, NumeracionCliente } from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ClienteFormModalComponent } from '../../../../shared/cliente-form-modal/cliente-form-modal.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

@Component({
  selector: 'app-cliente-detalle',
  imports: [WeLoaderComponent, FormsModule, RouterLink, ClienteFormModalComponent, LucideExternalLink, LucidePencil],
  templateUrl: './cliente-detalle.component.html',
})
export class ClienteDetalleComponent {
  private readonly clientesService = inject(ClientesService);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  readonly cliente = signal<Cliente | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly editando = signal(false);

  // --- Cambiar el número de cliente (misma lógica que el número de venta: si otro ya lo tiene, se recorre +1).
  private readonly toast = inject(ToastService);
  readonly cambiandoNumero = signal(false);
  readonly numeroNuevo = signal<number | null>(null);
  readonly numeracion = signal<NumeracionCliente | null>(null);
  readonly guardandoNumero = signal(false);
  readonly errorNumero = signal<string | null>(null);

  abrirCambioNumero(c: Cliente): void {
    this.numeroNuevo.set(c.numero);
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
    if (numero === null) return;
    try {
      const info = await this.clientesService.numeracion(numero, this.id);
      // Solo vale si el asesor no siguió escribiendo mientras tanto.
      if (this.numeroNuevo() === numero) this.numeracion.set(info);
    } catch {
      // el aviso es solo informativo; al guardar se valida de todos modos
    }
  }

  async guardarNumero(): Promise<void> {
    const numero = this.numeroNuevo();
    const actual = this.cliente();
    if (numero === null || !actual || this.guardandoNumero()) return;
    if (numero === actual.numero) {
      this.cambiandoNumero.set(false);
      return;
    }
    this.guardandoNumero.set(true);
    this.errorNumero.set(null);
    try {
      this.cliente.set(await this.clientesService.cambiarNumero(this.id, numero));
      this.cambiandoNumero.set(false);
      this.toast.success(`Ahora es el cliente n.º ${numero}.`);
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

  async guardarContrato(v: ClienteVenta): Promise<void> {
    if (this.guardandoContrato()) return;
    const nuevo = this.contratoBorrador().trim() || null;
    if (nuevo === (v.numeroContrato ?? null)) {
      this.editandoContratoDe.set(null);
      return;
    }
    this.guardandoContrato.set(true);
    this.errorContrato.set(null);
    try {
      const venta = await this.clientesService.guardarNumeroContratoDeVenta(v.ventaId, nuevo);
      this.cliente.update((c) =>
        c ? { ...c, ventas: c.ventas.map((x) => (x.ventaId === v.ventaId ? { ...x, numeroContrato: venta.numeroContrato } : x)) } : c,
      );
      this.editandoContratoDe.set(null);
      this.toast.success(nuevo ? `Contrato ${nuevo} guardado en la venta #${v.numero}.` : `Se quitó el número de contrato de la venta #${v.numero}.`);
    } catch (e) {
      this.errorContrato.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo guardar el número de contrato.',
      );
    } finally {
      this.guardandoContrato.set(false);
    }
  }

  readonly civilLabels = ESTADO_CIVIL_LABELS;
  readonly fuenteLabels = FUENTE_CLIENTE_LABELS;
  readonly ubicacionLabels = UBICACION_DOCUMENTO_LABELS;

  constructor() {
    void this.cargar();
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
    await this.cargar();
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
}
