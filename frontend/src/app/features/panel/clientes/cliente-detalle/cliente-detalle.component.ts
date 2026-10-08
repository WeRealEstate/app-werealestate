import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideExternalLink, LucidePencil } from '@lucide/angular';
import { UBICACION_DOCUMENTO_LABELS } from '../../../../core/models/asesor-externo.model';
import { Cliente, ESTADO_CIVIL_LABELS, FUENTE_CLIENTE_LABELS } from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { ClienteFormModalComponent } from '../../../../shared/cliente-form-modal/cliente-form-modal.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

@Component({
  selector: 'app-cliente-detalle',
  imports: [WeLoaderComponent, RouterLink, ClienteFormModalComponent, LucideExternalLink, LucidePencil],
  templateUrl: './cliente-detalle.component.html',
})
export class ClienteDetalleComponent {
  private readonly clientesService = inject(ClientesService);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  readonly cliente = signal<Cliente | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly editando = signal(false);

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
