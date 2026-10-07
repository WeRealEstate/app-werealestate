import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClienteLista } from '../../../../core/models/cliente.model';
import { Cliente } from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { ClienteFormModalComponent } from '../../../../shared/cliente-form-modal/cliente-form-modal.component';

type FiltroEstado = 'TODOS' | 'ACTIVOS' | 'INACTIVOS' | 'INCOMPLETOS' | 'CON_SALDO';

@Component({
  selector: 'app-clientes-list',
  imports: [FormsModule, RouterLink, ClienteFormModalComponent],
  templateUrl: './clientes-list.component.html',
})
export class ClientesListComponent {
  private readonly clientesService = inject(ClientesService);
  private readonly router = inject(Router);

  readonly clientes = signal<ClienteLista[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly busqueda = signal('');
  readonly filtroEstado = signal<FiltroEstado>('ACTIVOS');
  readonly filtroDesarrollo = signal('');
  readonly registrando = signal(false);

  readonly desarrollos = computed(() => [...new Set(this.clientes().flatMap((c) => c.desarrollos))].sort());

  readonly filtrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const estado = this.filtroEstado();
    const desarrollo = this.filtroDesarrollo();
    return this.clientes().filter((c) => {
      if (texto && !`${c.nombreCompleto} ${c.telefono ?? ''} ${c.correo ?? ''}`.toLowerCase().includes(texto)) return false;
      if (estado === 'ACTIVOS' && !c.activo) return false;
      if (estado === 'INACTIVOS' && c.activo) return false;
      if (estado === 'INCOMPLETOS' && !c.datosIncompletos) return false;
      if (estado === 'CON_SALDO' && c.saldoPendiente <= 0) return false;
      if (desarrollo && !c.desarrollos.includes(desarrollo)) return false;
      return true;
    });
  });

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      this.clientes.set(await this.clientesService.listar());
    } catch {
      this.error.set('No se pudieron cargar los clientes.');
    } finally {
      this.cargando.set(false);
    }
  }

  alGuardar(c: Cliente): void {
    this.registrando.set(false);
    void this.router.navigate(['/panel/clientes', c.id]);
  }

  irAExistente(id: number): void {
    this.registrando.set(false);
    void this.router.navigate(['/panel/clientes', id]);
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
