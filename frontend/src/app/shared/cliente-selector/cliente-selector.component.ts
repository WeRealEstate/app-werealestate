import { Component, OnDestroy, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideSearch, LucideUserPlus, LucideX } from '@lucide/angular';
import { Cliente, ClienteLista } from '../../core/models/cliente.model';
import { ClientesService } from '../../core/services/clientes.service';
import { ClienteFormModalComponent } from '../cliente-form-modal/cliente-form-modal.component';

/** Elegir (buscando) o registrar al cliente de una venta. */
@Component({
  selector: 'app-cliente-selector',
  imports: [FormsModule, ClienteFormModalComponent, LucideSearch, LucideUserPlus, LucideX],
  templateUrl: './cliente-selector.component.html',
})
export class ClienteSelectorComponent implements OnDestroy {
  private readonly clientesService = inject(ClientesService);

  readonly cliente = model<ClienteLista | null>(null);
  /** Para mostrar el error de "obligatorio" desde el formulario padre. */
  readonly invalido = model(false);

  readonly texto = signal('');
  readonly resultados = signal<ClienteLista[]>([]);
  readonly abierto = signal(false);
  readonly buscando = signal(false);
  readonly registrando = signal(false);
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.temporizador) clearTimeout(this.temporizador);
  }

  escribir(valor: string): void {
    this.texto.set(valor);
    this.abierto.set(true);
    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => void this.buscar(), 250);
  }

  async abrir(): Promise<void> {
    this.abierto.set(true);
    await this.buscar();
  }

  private async buscar(): Promise<void> {
    this.buscando.set(true);
    try {
      this.resultados.set(await this.clientesService.buscar(this.texto().trim()));
    } catch {
      this.resultados.set([]);
    } finally {
      this.buscando.set(false);
    }
  }

  elegir(c: ClienteLista): void {
    this.cliente.set(c);
    this.abierto.set(false);
    this.texto.set('');
  }

  quitar(): void {
    this.cliente.set(null);
  }

  cerrarLista(): void {
    // Da tiempo a que el clic en una opción se procese antes de ocultar la lista.
    setTimeout(() => this.abierto.set(false), 150);
  }

  registrarNuevo(): void {
    this.abierto.set(false);
    this.registrando.set(true);
  }

  async alGuardar(c: Cliente): Promise<void> {
    this.registrando.set(false);
    this.elegir(this.aLista(c));
  }

  /** El alta devolvió uno que ya existía: se usa ese. */
  async usarExistente(id: number): Promise<void> {
    this.registrando.set(false);
    try {
      this.elegir(this.aLista(await this.clientesService.obtener(id)));
    } catch {
      /* si falla, el usuario puede buscarlo manualmente */
    }
  }

  private aLista(c: Cliente): ClienteLista {
    return {
      id: c.id,
      nombreCompleto: c.nombreCompleto,
      telefono: c.telefono,
      correo: c.correo,
      activo: c.activo,
      datosIncompletos: c.datosIncompletos,
      compras: c.compras,
      desarrollos: [...new Set(c.ventas.flatMap((v) => v.desarrollos))],
      saldoPendiente: c.saldoPendiente,
    };
  }
}
